package com.briefy.api.infrastructure.extraction

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.time.Instant
import java.time.format.DateTimeParseException

class XApiExtractionProvider(
    private val restClient: RestClient,
    private val mediaWhisperTranscriptionService: MediaWhisperTranscriptionService,
    private val bearerToken: String,
    private val timeoutMs: Long,
    private val threadMaxResults: Int,
    private val maxVideoDurationSeconds: Int,
    private val maxVideoDownloadBytes: Long,
    private val mediaDownloadTimeoutMs: Int
) : ExtractionProvider {
    override val id: ExtractionProviderId = ExtractionProviderId.X_API
    private val logger = LoggerFactory.getLogger(XApiExtractionProvider::class.java)

    init {
        require(timeoutMs > 0) { "timeoutMs must be greater than 0" }
        require(threadMaxResults in 10..100) { "threadMaxResults must be between 10 and 100" }
        require(maxVideoDurationSeconds > 0) { "maxVideoDurationSeconds must be greater than 0" }
        require(maxVideoDownloadBytes > 0) { "maxVideoDownloadBytes must be greater than 0" }
        require(mediaDownloadTimeoutMs > 0) { "mediaDownloadTimeoutMs must be greater than 0" }
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

        val root = lookupPostById(postId, includeArticleExpansions = true)
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
        val ogImageUrl = resolveOgImageUrl(rootData, root.includes)
        val primaryMedia = resolvePrimaryMedia(rootData, root.includes)

        val videoExtraction = extractVideoContent(
            url = url,
            post = rootData,
            authorName = authorName,
            media = primaryMedia
        )
        if (videoExtraction != null) {
            return ExtractionResult(
                text = videoExtraction.text,
                title = videoExtraction.title,
                author = authorName,
                publishedDate = createdAt,
                ogImageUrl = ogImageUrl,
                aiFormatted = false,
                videoDurationSeconds = videoExtraction.durationSeconds,
                transcriptSource = "whisper"
            )
        }

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
                ogImageUrl = ogImageUrl,
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
            ogImageUrl = ogImageUrl,
            aiFormatted = false
        )
    }

    private fun lookupPostById(postId: String, includeArticleExpansions: Boolean): TweetLookupResponse {
        val tweetFields = listOf(
            "article",
            "attachments",
            "author_id",
            "conversation_id",
            "created_at",
            "entities",
            "in_reply_to_user_id",
            "note_tweet",
            "referenced_tweets",
            "text"
        )

        val expansions = mutableListOf("author_id")
        if (includeArticleExpansions) {
            expansions.add("article.cover_media")
            expansions.add("article.media_entities")
        }
        expansions.add("attachments.media_keys")

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
                        .queryParam("media.fields", "media_key,type,url,preview_image_url,duration_ms,variants")
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

        val isThreadRootCandidate = rootPost.id == conversationId && rootPost.inReplyToUserId == null
        val isSelfReply = !rootPost.inReplyToUserId.isNullOrBlank() && rootPost.inReplyToUserId == authorId
        if (!isThreadRootCandidate && !isSelfReply) {
            logger.info(
                "[twitter] thread.skip reason=not_self_thread rootPostId={} conversationId={} inReplyToUserId={} authorId={}",
                rootPost.id,
                conversationId,
                rootPost.inReplyToUserId,
                authorId
            )
            return listOf(rootPost)
        }

        return try {
            val threadQuery = "conversation_id:$conversationId from:$authorId"
            logger.info(
                "[twitter] request.lookup_thread method=GET path=/2/tweets/search/recent query={} max_results={}",
                threadQuery,
                threadMaxResults.coerceIn(10, 100)
            )
            val response = xRequest {
                restClient.get()
                    .uri { builder ->
                        builder.path("/2/tweets/search/recent")
                            .queryParam("query", threadQuery)
                            .queryParam("max_results", threadMaxResults.coerceIn(10, 100))
                            .queryParam(
                                "tweet.fields",
                                "author_id,conversation_id,created_at,in_reply_to_user_id,note_tweet,text"
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

    private fun resolvePrimaryMedia(post: TweetData, includes: Includes?): MediaData? {
        val mediaByKey = includes?.media.orEmpty()
            .mapNotNull { media -> media.mediaKey?.let { it to media } }
            .toMap()

        return post.attachments?.stringList("media_keys")
            ?.asSequence()
            ?.mapNotNull { mediaByKey[it] }
            ?.firstOrNull()
    }

    private fun extractVideoContent(
        url: String,
        post: TweetData,
        authorName: String?,
        media: MediaData?
    ): XVideoExtraction? {
        val normalizedType = media?.type?.lowercase()
        if (normalizedType != "video" && normalizedType != "animated_gif") {
            return null
        }

        val durationSeconds = media.durationMs
            ?.takeIf { it > 0 }
            ?.let { (it / 1000L).toInt().coerceAtLeast(1) }
        if (durationSeconds != null && durationSeconds > maxVideoDurationSeconds) {
            logger.warn(
                "[twitter] video.extraction_fallback postId={} mediaKey={} reason=duration_exceeds_limit durationSeconds={} limitSeconds={}",
                post.id,
                media.mediaKey,
                durationSeconds,
                maxVideoDurationSeconds
            )
            return null
        }

        val variants = media.videoVariantsByBitrateDesc()
        if (variants.isEmpty()) {
            logger.warn(
                "[twitter] video.extraction_fallback postId={} mediaKey={} reason=no_downloadable_mp4_variant",
                post.id,
                media.mediaKey
            )
            return null
        }

        variants.forEach { variant ->
            try {
                val tempDir = Files.createTempDirectory("briefy-x-video-${post.id ?: "unknown"}-").toFile()
                try {
                    val mediaFile = downloadVideo(variant.url.orEmpty(), tempDir)
                    val transcript = mediaWhisperTranscriptionService.transcribe(mediaFile, tempDir)
                    if (transcript.isBlank()) {
                        throw IllegalStateException("Whisper returned an empty transcript")
                    }

                    return XVideoExtraction(
                        text = buildVideoMarkdown(url, post, authorName, transcript, durationSeconds),
                        title = resolveVideoTitle(post),
                        durationSeconds = durationSeconds
                    )
                } finally {
                    tempDir.deleteRecursively()
                }
            } catch (error: Exception) {
                logger.warn(
                    "[twitter] video.variant_failed postId={} mediaKey={} bitRate={} reason={}",
                    post.id,
                    media.mediaKey,
                    variant.bitRate,
                    error.message
                )
            }
        }

        logger.warn(
            "[twitter] video.extraction_fallback postId={} mediaKey={} reason=all_variants_failed variants={}",
            post.id,
            media.mediaKey,
            variants.size
        )
        return null
    }

    private fun downloadVideo(videoUrl: String, tempDir: File): File {
        val targetFile = File(tempDir, "video.mp4")
        val connection = (URI.create(videoUrl).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = mediaDownloadTimeoutMs
            readTimeout = mediaDownloadTimeoutMs
            instanceFollowRedirects = true
            requestMethod = "GET"
        }

        try {
            connection.connect()
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                throw IllegalStateException("Video download failed with status $statusCode")
            }

            val declaredLength = connection.contentLengthLong
            if (declaredLength > maxVideoDownloadBytes) {
                throw IllegalStateException("Video download exceeds max supported size")
            }

            connection.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_DOWNLOAD_BUFFER_BYTES)
                    var totalBytes = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        if (totalBytes > maxVideoDownloadBytes) {
                            throw IllegalStateException("Video download exceeds max supported size")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        return targetFile
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

    private fun resolveOgImageUrl(post: TweetData, includes: Includes?): String? {
        val mediaByKey = includes?.media.orEmpty()
            .mapNotNull { media -> media.mediaKey?.let { it to media } }
            .toMap()

        post.article?.stringValue("cover_media")
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?.let { return it }

        val mediaKeys = linkedSetOf<String>()
        post.article?.stringValue("cover_media")
            ?.takeIf { it.isNotBlank() && !(it.startsWith("https://") || it.startsWith("http://")) }
            ?.let { mediaKeys.add(it) }
        post.article?.arrayObjectValues("media_entities")
            ?.mapNotNull { it.stringValue("media_key") }
            ?.forEach { mediaKeys.add(it) }
        post.attachments?.stringList("media_keys")
            ?.forEach { mediaKeys.add(it) }

        mediaKeys.asSequence()
            .mapNotNull { mediaByKey[it]?.bestImageUrl() }
            .firstOrNull()
            ?.let { return it }

        return post.entities?.firstArrayObject("urls")
            ?.arrayObjectValues("images")
            ?.firstOrNull()
            ?.stringValue("url")
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

    private fun resolveVideoTitle(post: TweetData): String {
        val snippet = extractPostTextOrNull(post)
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
            .take(80)
        return if (snippet.isNotBlank()) snippet else "X Video"
    }

    private fun buildVideoMarkdown(
        url: String,
        post: TweetData,
        authorName: String?,
        transcript: String,
        durationSeconds: Int?
    ): String {
        return buildString {
            appendLine("# X Video")
            appendLine()
            if (!authorName.isNullOrBlank()) appendLine("- Author: $authorName")
            if (!post.createdAt.isNullOrBlank()) appendLine("- Created: ${post.createdAt}")
            if (durationSeconds != null) appendLine("- Duration Seconds: $durationSeconds")
            appendLine("- URL: $url")
            appendLine()

            val postText = extractPostTextOrNull(post)
            if (!postText.isNullOrBlank()) {
                appendLine("## Post")
                appendLine()
                appendLine(postText)
                appendLine()
            }

            appendLine("## Transcript")
            appendLine()
            appendLine(transcript.trim())
        }.trim()
    }

    private fun extractPostTextOrNull(post: TweetData): String? {
        return runCatching { extractPostText(post) }.getOrNull()
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
        private const val DEFAULT_DOWNLOAD_BUFFER_BYTES = 16 * 1024
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

private fun Map<String, Any?>.arrayObjectValues(key: String): List<Map<String, Any?>> {
    val value = this[key] as? List<*> ?: return emptyList()
    return value.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        map.entries
            .filter { it.key is String }
            .associate { (k, v) -> (k as String) to v }
    }
}

private fun Map<String, Any?>.stringList(key: String): List<String> {
    val value = this[key] as? List<*> ?: return emptyList()
    return value.mapNotNull { it as? String }
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
    val users: List<UserData>? = null,
    val media: List<MediaData>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class UserData(
    val id: String? = null,
    val name: String? = null,
    val username: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MediaData(
    @param:JsonProperty("media_key")
    val mediaKey: String? = null,
    val type: String? = null,
    val url: String? = null,
    @param:JsonProperty("preview_image_url")
    val previewImageUrl: String? = null,
    @param:JsonProperty("duration_ms")
    val durationMs: Long? = null,
    val variants: List<MediaVariant>? = null
) {
    fun bestImageUrl(): String? {
        val normalizedType = type?.lowercase()
        return when (normalizedType) {
            "photo" -> url ?: previewImageUrl
            else -> previewImageUrl ?: url
        }?.takeIf { it.isNotBlank() }
    }

    fun videoVariantsByBitrateDesc(): List<MediaVariant> {
        return variants.orEmpty()
            .filter { it.contentType.equals("video/mp4", ignoreCase = true) && !it.url.isNullOrBlank() }
            .sortedByDescending { it.bitRate ?: -1 }
            .map { variant -> variant.copy(url = variant.url.orEmpty()) }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MediaVariant(
    @param:JsonProperty("bit_rate")
    val bitRate: Int? = null,
    @param:JsonProperty("content_type")
    val contentType: String? = null,
    val url: String? = null
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
    @param:JsonProperty("in_reply_to_user_id")
    val inReplyToUserId: String? = null,
    @param:JsonProperty("note_tweet")
    val noteTweet: Map<String, Any?>? = null,
    val article: Map<String, Any?>? = null,
    val entities: Map<String, Any?>? = null,
    val attachments: Map<String, Any?>? = null
)

private data class XVideoExtraction(
    val text: String,
    val title: String,
    val durationSeconds: Int?
)
