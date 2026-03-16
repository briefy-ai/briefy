package com.briefy.api.application.sharing

import com.briefy.api.application.settings.ImageGenSettingsService
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.infrastructure.ai.AiAdapter
import com.briefy.api.infrastructure.imagegen.ImageStorageService
import com.briefy.api.infrastructure.imagegen.OpenRouterImageClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID
import javax.imageio.ImageIO

data class CoverImageResult(
    val coverKey: String,
    val featuredKey: String
)

@Service
class CoverImageService(
    private val imageGenSettingsService: ImageGenSettingsService,
    private val topicLinkRepository: TopicLinkRepository,
    private val aiAdapter: AiAdapter,
    private val openRouterImageClient: OpenRouterImageClient,
    private val imageStorageService: ImageStorageService,
    private val coverImageCompositor: CoverImageCompositor,
    @param:Value("\${cover-image.prompt-crafting.enabled:true}")
    private val promptCraftingEnabled: Boolean,
    @param:Value("\${cover-image.prompt-crafting.provider:google_genai}")
    private val promptCraftingProvider: String,
    @param:Value("\${cover-image.prompt-crafting.model:gemini-2.5-flash}")
    private val promptCraftingModel: String
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
            val promptSelection = buildImagePrompt(source, topicNames)
            logger.info(
                "[service] Cover image generation started sourceId={} userId={} model={} topicCount={} titleLength={} contentChars={} promptLength={} wasCrafted={}",
                source.id,
                userId,
                config.model,
                topicNames.size,
                sourceTitle.length,
                source.content?.text?.length ?: 0,
                promptSelection.prompt.length,
                promptSelection.wasCrafted
            )
            val rawBytes = openRouterImageClient.generate(
                apiKey = config.apiKey,
                model = config.model,
                prompt = promptSelection.prompt,
                size = "1792x1024"
            )
            val originalImageFormat = detectOriginalImageFormat(rawBytes)
            val coverKey = originalKey(source.id, originalImageFormat.extension)
            val featuredKey = featuredKey(source.id)

            imageStorageService.uploadImage(coverKey, rawBytes, originalImageFormat.contentType)
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

    private fun buildImagePrompt(source: Source, topicNames: List<String>): PromptSelection {
        val craftedPrompt = craftVisualPrompt(source, topicNames)
        if (craftedPrompt != null) {
            logger.info(
                "[service] Using LLM-crafted visual prompt sourceId={} promptLength={}",
                source.id,
                craftedPrompt.length
            )
            return PromptSelection(prompt = craftedPrompt, wasCrafted = true)
        }

        return PromptSelection(
            prompt = buildRawPrompt(source, topicNames),
            wasCrafted = false
        )
    }

    private fun craftVisualPrompt(source: Source, topicNames: List<String>): String? {
        if (!promptCraftingEnabled) {
            return null
        }

        val sourceExcerpt = source.content?.text
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(MAX_PROMPT_CRAFTING_CONTENT_CHARS)
            .orEmpty()

        if (sourceExcerpt.isBlank()) {
            return null
        }

        return try {
            val craftedPrompt = aiAdapter.complete(
                provider = promptCraftingProvider,
                model = promptCraftingModel,
                prompt = buildPromptCraftingPrompt(source, topicNames, sourceExcerpt),
                systemPrompt = PROMPT_CRAFTING_SYSTEM_PROMPT,
                useCase = PROMPT_CRAFTING_USE_CASE
            ).trim()

            craftedPrompt.takeIf { it.length in MIN_CRAFTED_PROMPT_LENGTH..MAX_CRAFTED_PROMPT_LENGTH }
                ?: run {
                    logger.warn(
                        "[service] Cover image prompt crafting discarded sourceId={} promptLength={} reason=out_of_bounds",
                        source.id,
                        craftedPrompt.length
                    )
                    null
                }
        } catch (ex: Exception) {
            logger.warn(
                "[service] Cover image prompt crafting failed sourceId={} provider={} model={}",
                source.id,
                promptCraftingProvider,
                promptCraftingModel,
                ex
            )
            null
        }
    }

    private fun buildPromptCraftingPrompt(source: Source, topicNames: List<String>, sourceExcerpt: String): String {
        return buildString {
            appendLine("Create a visual prompt for an editorial cover image.")
            appendLine("Title: ${sourceTitle(source)}")
            if (topicNames.isNotEmpty()) {
                appendLine("Topics: ${topicNames.joinToString(", ")}")
            }
            appendLine("Article content:")
            appendLine(sourceExcerpt)
        }.trim()
    }

    private fun buildRawPrompt(source: Source, topicNames: List<String>): String {
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

    private fun detectOriginalImageFormat(imageBytes: ByteArray): StoredImageFormat {
        ImageIO.createImageInputStream(imageBytes.inputStream()).use { imageInput ->
            requireNotNull(imageInput) { "Generated image could not be opened" }
            val reader = ImageIO.getImageReaders(imageInput).asSequence().firstOrNull()
                ?: throw IllegalArgumentException("Generated image format could not be detected")
            return try {
                when (reader.formatName.trim().lowercase()) {
                    "png" -> StoredImageFormat(extension = "png", contentType = "image/png")
                    "jpeg", "jpg" -> StoredImageFormat(extension = "jpg", contentType = "image/jpeg")
                    else -> throw IllegalArgumentException("Unsupported generated image format: ${reader.formatName}")
                }
            } finally {
                reader.dispose()
            }
        }
    }

    private fun originalKey(sourceId: UUID, extension: String): String = "images/covers/$sourceId/original.$extension"

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

    private data class PromptSelection(
        val prompt: String,
        val wasCrafted: Boolean
    )

    private data class StoredImageFormat(
        val extension: String,
        val contentType: String
    )

    companion object {
        private const val MAX_PROMPT_CRAFTING_CONTENT_CHARS = 4000
        private const val MIN_CRAFTED_PROMPT_LENGTH = 20
        private const val MAX_CRAFTED_PROMPT_LENGTH = 500
        private const val PROMPT_CRAFTING_USE_CASE = "cover_image_prompt_crafting"
        private val PROMPT_CRAFTING_SYSTEM_PROMPT = """
            You convert article context into a single visual prompt for image generation.
            Return only one 50-80 word scene description.
            Make it cinematic and editorial.
            Do not mention text, typography, letters, logos, UI, screenshots, frames, or watermarks.
            Keep the center calm and uncluttered for future title overlay.
        """.trimIndent()
    }
}
