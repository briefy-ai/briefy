package com.briefy.api.infrastructure.extraction

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Instant
import java.time.format.DateTimeParseException

class XApiExtractionProvider(
    private val restClient: RestClient,
    private val bearerToken: String,
    private val timeoutMs: Long,
    private val threadMaxResults: Int
) : ExtractionProvider {
    override val id: ExtractionProviderId = ExtractionProviderId.X_API
    private val logger = LoggerFactory.getLogger(XApiExtractionProvider::class.java)

    init {
        require(timeoutMs > 0) { "timeoutMs must be greater than 0" }
        require(threadMaxResults in 10..100) { "threadMaxResults must be between 10 and 100" }
    }

    override fun extract(url: String): ExtractionResult {
        logger.info("[twitter] extract.start url={}", url)
        val postId = extractPostId(url)
            ?: throw ExtractionProviderException(
                providerId = id,
                reason = ExtractionFailureReason.UNSUPPORTED,
                message = "X URL does not contain a status ID"
            )
        logger.info("[twitter] extract.post_id_resolved url={} postId={}", url, postId)

        val root = lookupPostById(postId, includeArticleExpansions = false)
        val rootData = root.data?.firstOrNull()
            ?: throw ExtractionProviderException(
                providerId = id,
                reason = ExtractionFailureReason.UNKNOWN,
                message = "X API returned empty lookup result"
            )
        logger.info(
            "[twitter] extract.lookup_root_resolved postId={} hasArticle={} conversationId={} authorId={}",
            postId,
            rootData.article != null,
            rootData.conversationId,
            rootData.authorId
        )

        val authorId = rootData.authorId
        val authorName = resolveAuthorName(authorId, root.includes)
        val createdAt = parseInstant(rootData.createdAt)
        val conversationId = rootData.conversationId

        val isArticle = rootData.article != null
        if (isArticle) {
            logger.info("[twitter] extract.article_detected postId={} action=refetch_article", postId)
            val enrichedRoot = lookupArticleById(postId) ?: rootData
            logger.info(
                "[twitter] extract.article_refetch_done postId={} articleKeys={} hasText={} hasTitle={}",
                postId,
                enrichedRoot.article?.keys?.joinToString(","),
                !enrichedRoot.article?.stringValue("text").isNullOrBlank(),
                !resolveArticleTitle(enrichedRoot).isBlank()
            )
            val markdown = buildArticleText(enrichedRoot)
            return ExtractionResult(
                text = markdown,
                title = resolveArticleTitle(enrichedRoot),
                author = authorName,
                publishedDate = createdAt,
                aiFormatted = false
            )
        }

        val threadPosts = fetchThreadPostsOrRoot(
            rootPost = rootData,
            conversationId = conversationId,
            authorId = authorId
        )
        logger.info("[twitter] extract.thread_resolution postId={} posts={}", postId, threadPosts.size)

        val markdown = if (threadPosts.size <= 1) {
            buildSinglePostMarkdown(url = url, post = threadPosts.first(), authorName = authorName)
        } else {
            buildThreadMarkdown(url = url, posts = threadPosts, authorName = authorName)
        }

        return ExtractionResult(
            text = markdown,
            title = resolvePostTitle(rootData, threadPosts.size),
            author = authorName,
            publishedDate = createdAt,
            aiFormatted = false
        )
    }

    private fun lookupPostById(postId: String, includeArticleExpansions: Boolean): TweetLookupResponse {
        val tweetFields = listOf(
            "article",
            "author_id",
            "conversation_id",
            "created_at",
            "entities",
            "note_tweet",
            "referenced_tweets",
            "text"
        )

        val expansions = mutableListOf("author_id")
        if (includeArticleExpansions) {
            expansions.add("article.cover_media")
            expansions.add("article.media_entities")
        }

        logger.info(
            "[twitter] request.lookup_post method=GET path=/2/tweets ids={} includeArticleExpansions={} tweetFields={} expansions={}",
            postId,
            includeArticleExpansions,
            tweetFields.joinToString(","),
            expansions.joinToString(",")
        )

        val response = xRequest {
            restClient.get()
                .uri { builder ->
                    builder.path("/2/tweets")
                        .queryParam("ids", postId)
                        .queryParam("tweet.fields", tweetFields.joinToString(","))
                        .queryParam("expansions", expansions.joinToString(","))
                        .build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $bearerToken")
                .retrieve()
                .onStatus(HttpStatusCode::isError) { _, response ->
                    throw createHttpError("X lookup failed", response.statusCode)
                }
                .body(TweetLookupResponse::class.java)
                ?: throw ExtractionProviderException(
                    providerId = id,
                    reason = ExtractionFailureReason.UNKNOWN,
                    message = "X API returned empty body for tweet lookup"
                )
        }
        logger.info(
            "[twitter] response.lookup_post postId={} count={} firstId={}",
            postId,
            response.data?.size ?: 0,
            response.data?.firstOrNull()?.id
        )
        return response
    }

    private fun lookupArticleById(postId: String): TweetData? {
        logger.info("[twitter] request.lookup_article method=GET path=/2/tweets/{} tweet.fields=article", postId)
        val response = xRequest {
            restClient.get()
                .uri { builder ->
                    builder.path("/2/tweets/$postId")
                        .queryParam("tweet.fields", "article")
                        .build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $bearerToken")
                .retrieve()
                .onStatus(HttpStatusCode::isError) { _, response ->
                    throw createHttpError("X article lookup failed", response.statusCode)
                }
                .body(SingleTweetLookupResponse::class.java)
        }
        logger.info(
            "[twitter] response.lookup_article postId={} found={} articleKeys={}",
            postId,
            response?.data != null,
            response?.data?.article?.keys?.joinToString(",")
        )
        return response?.data
    }

    private fun fetchThreadPostsOrRoot(rootPost: TweetData, conversationId: String?, authorId: String?): List<TweetData> {
        if (conversationId.isNullOrBlank() || authorId.isNullOrBlank()) {
            logger.info(
                "[twitter] thread.skip reason=missing_conversation_or_author conversationId={} authorId={}",
                conversationId,
                authorId
            )
            return listOf(rootPost)
        }

        return try {
            logger.info(
                "[twitter] request.lookup_thread method=GET path=/2/tweets/search/recent query=conversation_id:{} max_results={}",
                conversationId,
                threadMaxResults.coerceIn(10, 100)
            )
            val response = xRequest {
                restClient.get()
                    .uri { builder ->
                        builder.path("/2/tweets/search/recent")
                            .queryParam("query", "conversation_id:$conversationId")
                            .queryParam("max_results", threadMaxResults.coerceIn(10, 100))
                            .queryParam(
                                "tweet.fields",
                                "author_id,conversation_id,created_at,note_tweet,text"
                            )
                            .build()
                    }
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $bearerToken")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError) { _, clientResponse ->
                        throw createHttpError("X thread lookup failed", clientResponse.statusCode)
                    }
                    .body(TweetLookupResponse::class.java)
                    ?: TweetLookupResponse()
            }
            logger.info(
                "[twitter] response.lookup_thread conversationId={} totalPosts={}",
                conversationId,
                response.data?.size ?: 0
            )

            val posts = response.data.orEmpty()
                .asSequence()
                .filter { it.authorId == authorId }
                .sortedBy { parseInstant(it.createdAt) ?: Instant.EPOCH }
                .toList()
            logger.info(
                "[twitter] thread.filtered conversationId={} authorId={} filteredPosts={}",
                conversationId,
                authorId,
                posts.size
            )

            if (posts.isEmpty()) listOf(rootPost) else posts
        } catch (_: ExtractionProviderException) {
            logger.warn(
                "[twitter] thread.lookup_failed_fallback_to_root conversationId={} authorId={} rootPostId={}",
                conversationId,
                authorId,
                rootPost.id
            )
            listOf(rootPost)
        }
    }

    private fun resolveAuthorName(authorId: String?, includes: Includes?): String? {
        if (authorId.isNullOrBlank()) return null
        return includes?.users
            ?.firstOrNull { it.id == authorId }
            ?.let { user ->
                when {
                    !user.username.isNullOrBlank() -> "@${user.username}"
                    else -> user.name
                }
            }
    }

    private fun resolvePostTitle(root: TweetData, postCount: Int): String {
        val authorHint = root.authorId?.let { "by $it" } ?: ""
        return if (postCount > 1) {
            "X Thread $authorHint".trim()
        } else {
            val snippet = extractPostText(root).lineSequence().firstOrNull()?.trim().orEmpty().take(80)
            if (snippet.isNotBlank()) snippet else "X Post"
        }
    }

    private fun resolveArticleTitle(post: TweetData): String {
        val articleNode = post.article ?: return "X Article"
        val candidates = listOf(
            articleNode.stringValue("title"),
            articleNode.stringValue("headline"),
            post.entities?.firstArrayObject("urls")?.stringValue("title")
        )
        return candidates.firstOrNull { !it.isNullOrBlank() } ?: "X Article"
    }

    private fun buildSinglePostMarkdown(url: String, post: TweetData, authorName: String?): String {
        val text = extractPostText(post)
        return buildString {
            appendLine("# X Post")
            appendLine()
            if (!authorName.isNullOrBlank()) appendLine("- Author: $authorName")
            if (!post.createdAt.isNullOrBlank()) appendLine("- Created: ${post.createdAt}")
            appendLine("- URL: $url")
            appendLine()
            appendLine(text)
        }.trim()
    }

    private fun buildThreadMarkdown(url: String, posts: List<TweetData>, authorName: String?): String {
        return buildString {
            appendLine("# X Thread")
            appendLine()
            if (!authorName.isNullOrBlank()) appendLine("- Author: $authorName")
            appendLine("- URL: $url")
            appendLine("- Posts: ${posts.size}")
            appendLine()

            posts.forEachIndexed { index, post ->
                appendLine("## Post ${index + 1}")
                if (!post.createdAt.isNullOrBlank()) appendLine("Created: ${post.createdAt}")
                appendLine()
                appendLine(extractPostText(post))
                appendLine()
            }
        }.trim()
    }

    private fun buildArticleText(post: TweetData): String {
        val articleNode = post.article
        val fromArticle = listOf(
            articleNode?.stringValue("plain_text"),
            articleNode?.stringValue("text"),
            articleNode?.stringValue("body"),
            articleNode?.stringValue("content"),
            articleNode?.stringValue("preview_text"),
            articleNode?.stringValue("description")
        ).firstOrNull { !it.isNullOrBlank() }

        if (!fromArticle.isNullOrBlank()) {
            return fromArticle.trim()
        }

        return extractPostText(post)
    }

    private fun extractPostText(post: TweetData): String {
        val noteText = post.noteTweet?.stringValue("text")
        if (!noteText.isNullOrBlank()) {
            return noteText.trim()
        }

        val text = post.text?.trim().orEmpty()
        if (text.isNotBlank()) {
            return text
        }

        throw ExtractionProviderException(
            providerId = id,
            reason = ExtractionFailureReason.UNKNOWN,
            message = "X API post payload does not contain text"
        )
    }

    private fun extractPostId(url: String): String? {
        val match = STATUS_ID_REGEX.find(url) ?: return null
        return match.groupValues.getOrNull(1)
    }

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun createHttpError(context: String, statusCode: HttpStatusCode): ExtractionProviderException {
        val reason = when (statusCode.value()) {
            HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.FORBIDDEN.value() -> ExtractionFailureReason.BLOCKED
            HttpStatus.TOO_MANY_REQUESTS.value() -> ExtractionFailureReason.TIMEOUT
            in 500..599 -> ExtractionFailureReason.NETWORK
            else -> ExtractionFailureReason.UNKNOWN
        }
        logger.warn("[twitter] request.error context={} status={} mappedReason={}", context, statusCode, reason)
        return ExtractionProviderException(
            providerId = id,
            reason = reason,
            message = "$context with status $statusCode"
        )
    }

    private fun <T> xRequest(block: () -> T): T {
        return try {
            block()
        } catch (e: ExtractionProviderException) {
            throw e
        } catch (e: RestClientException) {
            logger.warn("[twitter] request.exception type={} message={}", e::class.simpleName, e.message)
            throw ExtractionProviderException(
                providerId = id,
                reason = ExtractionFailureReason.NETWORK,
                message = "X API request failed",
                cause = e
            )
        }
    }

    companion object {
        private val STATUS_ID_REGEX = Regex("""/(?:i/web/)?status/(\d+)""")
    }
}

private fun Map<String, Any?>.stringValue(key: String): String? {
    return this[key] as? String
}

private fun Map<String, Any?>.firstArrayObject(key: String): Map<String, Any?>? {
    val value = this[key] as? List<*> ?: return null
    val first = value.firstOrNull() as? Map<*, *> ?: return null
    return first.entries
        .filter { it.key is String }
        .associate { (k, v) -> (k as String) to v }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TweetLookupResponse(
    val data: List<TweetData>? = null,
    val includes: Includes? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class SingleTweetLookupResponse(
    val data: TweetData? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class Includes(
    val users: List<UserData>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class UserData(
    val id: String? = null,
    val name: String? = null,
    val username: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TweetData(
    val id: String? = null,
    val text: String? = null,
    @param:JsonProperty("author_id")
    val authorId: String? = null,
    @param:JsonProperty("conversation_id")
    val conversationId: String? = null,
    @param:JsonProperty("created_at")
    val createdAt: String? = null,
    @param:JsonProperty("note_tweet")
    val noteTweet: Map<String, Any?>? = null,
    val article: Map<String, Any?>? = null,
    val entities: Map<String, Any?>? = null
)
