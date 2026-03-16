package com.briefy.api.application.sharing

import com.briefy.api.application.settings.ImageGenSettingsService
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.infrastructure.imagegen.ImageStorageService
import com.briefy.api.infrastructure.imagegen.OpenRouterImageClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

data class CoverImageResult(
    val coverKey: String,
    val featuredKey: String
)

@Service
class CoverImageService(
    private val imageGenSettingsService: ImageGenSettingsService,
    private val topicLinkRepository: TopicLinkRepository,
    private val openRouterImageClient: OpenRouterImageClient,
    private val imageStorageService: ImageStorageService,
    private val coverImageCompositor: CoverImageCompositor
) {
    private val logger = LoggerFactory.getLogger(CoverImageService::class.java)

    fun generateAndStore(source: Source, userId: UUID): CoverImageResult? {
        var uploadedFeaturedKey: String? = null
        var uploadedCoverKey: String? = null
        return try {
            val config = imageGenSettingsService.resolveConfig(userId)
            if (config == null) {
                logger.info(
                    "[service] Cover image generation skipped sourceId={} userId={} reason=image_gen_not_configured",
                    source.id,
                    userId
                )
                return null
            }

            val topicNames = activeTopicNames(source.id, userId)
            val sourceTitle = sourceTitle(source)
            logger.info(
                "[service] Cover image generation started sourceId={} userId={} model={} topicCount={} titleLength={} contentChars={}",
                source.id,
                userId,
                config.model,
                topicNames.size,
                sourceTitle.length,
                source.content?.text?.length ?: 0
            )
            val prompt = buildPrompt(source, topicNames)
            val rawBytes = openRouterImageClient.generate(
                apiKey = config.apiKey,
                model = config.model,
                prompt = prompt,
                size = "1792x1024"
            )
            val coverKey = originalKey(source.id)
            val featuredKey = featuredKey(source.id)

            imageStorageService.uploadImage(coverKey, rawBytes)
            uploadedCoverKey = coverKey
            val featuredBytes = coverImageCompositor.composite(rawBytes, sourceTitle(source))
            imageStorageService.uploadImage(featuredKey, featuredBytes)
            uploadedFeaturedKey = featuredKey

            logger.info(
                "[service] Cover image generation completed sourceId={} userId={} model={} coverKey={} featuredKey={} originalBytes={} featuredBytes={}",
                source.id,
                userId,
                config.model,
                coverKey,
                featuredKey,
                rawBytes.size,
                featuredBytes.size
            )

            CoverImageResult(
                coverKey = coverKey,
                featuredKey = featuredKey
            )
        } catch (ex: Exception) {
            cleanupUploadedImage(uploadedFeaturedKey, source.id, userId)
            cleanupUploadedImage(uploadedCoverKey, source.id, userId)
            logger.warn(
                "[service] Cover image generation failed sourceId={} userId={}",
                source.id,
                userId,
                ex
            )
            null
        }
    }

    private fun buildPrompt(source: Source, topicNames: List<String>): String {
        val excerpt = source.content?.text
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.split(" ")
            ?.take(1000)
            ?.joinToString(" ")
            .orEmpty()

        return buildString {
            appendLine("Create a cinematic, atmospheric 16:9 editorial cover image.")
            appendLine("Do not include any text, letters, logos, UI, frames, or watermarks.")
            appendLine("Keep the central area relatively calm and uncluttered because title text will be overlaid later.")
            appendLine("Use a visually rich scene that reflects the source themes instead of a literal screenshot or infographic.")
            appendLine()
            appendLine("Title: ${sourceTitle(source)}")
            if (topicNames.isNotEmpty()) {
                appendLine("Topics: ${topicNames.joinToString(", ")}")
            }
            if (excerpt.isNotBlank()) {
                appendLine("Source excerpt:")
                appendLine(excerpt)
            }
        }.trim()
    }

    private fun sourceTitle(source: Source): String {
        return source.metadata?.title?.trim()?.ifBlank { null } ?: source.url.raw
    }

    private fun activeTopicNames(sourceId: UUID, userId: UUID): List<String> {
        return topicLinkRepository.findActiveTopicsBySourceIds(userId, listOf(sourceId))
            .map { it.topicName.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun originalKey(sourceId: UUID): String = "images/covers/$sourceId/original.png"

    private fun featuredKey(sourceId: UUID): String = "images/covers/$sourceId/featured.jpg"

    private fun cleanupUploadedImage(key: String?, sourceId: UUID, userId: UUID) {
        val imageKey = key?.trim()?.ifBlank { null } ?: return
        try {
            imageStorageService.deleteImage(imageKey)
        } catch (ex: Exception) {
            logger.warn(
                "[service] Cover image cleanup failed sourceId={} userId={} key={}",
                sourceId,
                userId,
                imageKey,
                ex
            )
        }
    }
}
