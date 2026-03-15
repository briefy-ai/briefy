package com.briefy.api.application.sharing

import com.briefy.api.application.settings.TtsSettingsService
import com.briefy.api.application.source.NarrationContentHashing
import com.briefy.api.domain.knowledgegraph.source.AudioContent
import com.briefy.api.domain.knowledgegraph.source.SharedAudioCacheRepository
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceType
import com.briefy.api.domain.sharing.ShareLink
import com.briefy.api.domain.sharing.ShareLinkEntityType
import com.briefy.api.domain.sharing.ShareLinkRepository
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.briefy.api.infrastructure.tts.AudioStorageService
import com.briefy.api.infrastructure.tts.NarrationLanguageResolver
import com.briefy.api.infrastructure.tts.TtsProviderType
import com.briefy.api.infrastructure.tts.TtsVoiceResolver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.util.HtmlUtils
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import javax.imageio.ImageIO

@Service
class ShareLinkService(
    private val shareLinkRepository: ShareLinkRepository,
    private val sourceRepository: SourceRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val sharedAudioCacheRepository: SharedAudioCacheRepository,
    private val audioStorageService: AudioStorageService,
    private val ttsSettingsService: TtsSettingsService,
    private val narrationLanguageResolver: NarrationLanguageResolver,
    private val ttsVoiceResolver: TtsVoiceResolver
) {
    private val logger = LoggerFactory.getLogger(ShareLinkService::class.java)

    @Transactional
    fun create(request: CreateShareLinkRequest): ShareLinkResponse {
        val userId = currentUserProvider.requireUserId()
        logger.info("[service] Creating share link userId={} entityType={} entityId={}", userId, request.entityType, request.entityId)

        validateEntityOwnership(userId, request.entityType, request.entityId)

        val shareLink = ShareLink(
            token = ShareLink.generateToken(),
            entityType = request.entityType,
            entityId = request.entityId,
            userId = userId,
            expiresAt = request.expiresAt
        )
        shareLinkRepository.save(shareLink)
        logger.info("[service] Share link created id={} userId={}", shareLink.id, userId)
        return shareLink.toResponse()
    }

    @Transactional(readOnly = true)
    fun resolve(token: String): SharedSourceResponse {
        val shareLink = resolveActiveShareLink(token)

        return when (shareLink.entityType) {
            ShareLinkEntityType.SOURCE -> resolveSource(shareLink)
            ShareLinkEntityType.BRIEFING -> throw ShareLinkNotFoundException(token) // not yet supported
        }
    }

    @Transactional(readOnly = true)
    fun buildShareHtml(token: String, baseUrl: String): String {
        val shareLink = resolveActiveShareLink(token)
        if (shareLink.entityType != ShareLinkEntityType.SOURCE) {
            throw ShareLinkNotFoundException(token)
        }

        val source = resolveSourceEntity(shareLink)
        val sourceTitle = source.metadata?.title?.trim()?.ifBlank { null } ?: source.url.raw
        val title = "$sourceTitle - Briefy AI"
        val description = source.content?.text
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(200)
            ?.ifBlank { "Shared from Briefy AI" }
            ?: "Shared from Briefy AI"
        val ogImageUrl = resolveOgImageUrl(source, baseUrl, token)
        val ogUrl = if (baseUrl.isBlank()) "/share/$token" else "${baseUrl.trimEnd('/')}/share/$token"

        return """
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8"/>
    <title>${escapeHtml(title)}</title>
    <meta property="og:title" content="${escapeHtml(title)}"/>
    <meta property="og:image" content="${escapeHtmlAttribute(ogImageUrl)}"/>
    <meta property="og:description" content="${escapeHtmlAttribute(description)}"/>
    <meta property="og:url" content="${escapeHtmlAttribute(ogUrl)}"/>
    <meta property="og:type" content="article"/>
    <meta property="og:site_name" content="Briefy AI"/>
    <meta name="twitter:card" content="summary_large_image"/>
  </head>
  <body>
    <script>window.location.replace('/share/${escapeJavaScriptPathSegment(token)}');</script>
  </body>
</html>
        """.trimIndent()
    }

    @Transactional(readOnly = true)
    fun buildOgImage(token: String): ByteArray {
        val shareLink = try {
            resolveActiveShareLink(token)
        } catch (_: ShareLinkNotFoundException) {
            return loadDefaultOgImage()
        } catch (_: ShareLinkExpiredException) {
            return loadDefaultOgImage()
        }

        if (shareLink.entityType != ShareLinkEntityType.SOURCE) {
            return loadDefaultOgImage()
        }

        val source = sourceRepository.findById(shareLink.entityId).orElse(null) ?: return loadDefaultOgImage()
        return try {
            renderOgImage(source)
        } catch (_: Exception) {
            loadDefaultOgImage()
        }
    }

    @Transactional(readOnly = true)
    fun list(entityType: ShareLinkEntityType, entityId: UUID): List<ShareLinkResponse> {
        val userId = currentUserProvider.requireUserId()
        return shareLinkRepository.findByUserIdAndEntityTypeAndEntityId(userId, entityType, entityId)
            .filter { it.isActive }
            .map { it.toResponse() }
    }

    @Transactional
    fun revoke(shareLinkId: UUID) {
        val userId = currentUserProvider.requireUserId()
        val shareLink = shareLinkRepository.findById(shareLinkId).orElse(null)
            ?: throw ShareLinkNotFoundException(shareLinkId.toString())

        if (shareLink.userId != userId) {
            throw ShareLinkNotFoundException(shareLinkId.toString())
        }

        shareLink.revoke()
        shareLinkRepository.save(shareLink)
        logger.info("[service] Share link revoked id={} userId={}", shareLinkId, userId)
    }

    private fun resolveActiveShareLink(token: String): ShareLink {
        val shareLink = shareLinkRepository.findByToken(token)
            ?: throw ShareLinkNotFoundException(token)

        if (shareLink.revokedAt != null) {
            throw ShareLinkNotFoundException(token)
        }
        if (shareLink.expiresAt != null && Instant.now().isAfter(shareLink.expiresAt)) {
            throw ShareLinkExpiredException(token)
        }
        return shareLink
    }

    private fun validateEntityOwnership(userId: UUID, entityType: ShareLinkEntityType, entityId: UUID) {
        when (entityType) {
            ShareLinkEntityType.SOURCE -> {
                sourceRepository.findByIdAndUserId(entityId, userId)
                    ?: throw IllegalArgumentException("Source not found: $entityId")
            }
            ShareLinkEntityType.BRIEFING -> {
                throw IllegalArgumentException("Briefing sharing is not yet supported")
            }
        }
    }

    private fun resolveSourceEntity(shareLink: ShareLink): Source {
        return sourceRepository.findById(shareLink.entityId).orElse(null)
            ?: throw ShareLinkNotFoundException(shareLink.token)
    }

    private fun resolveSource(shareLink: ShareLink): SharedSourceResponse {
        val source = resolveSourceEntity(shareLink)

        return SharedSourceResponse(
            entityType = shareLink.entityType,
            expiresAt = shareLink.expiresAt,
            source = SharedSourceData(
                title = source.metadata?.title,
                url = source.url.raw,
                sourceType = source.sourceType.name.lowercase(),
                author = source.metadata?.author,
                publishedDate = source.metadata?.publishedDate,
                readingTimeMinutes = source.metadata?.estimatedReadingTime,
                content = source.content?.text,
                audio = resolveSharedNarration(source)
            )
        )
    }

    @Transactional(readOnly = true)
    fun resolveAudio(token: String): ShareLinkAudioResponse {
        val shareLink = resolveActiveShareLink(token)
        if (shareLink.entityType != ShareLinkEntityType.SOURCE) {
            throw ShareLinkNotFoundException(token)
        }

        val source = resolveSourceEntity(shareLink)
        val audio = resolveSharedNarration(source)
            ?: throw ShareLinkAudioUnavailableException(token)

        return ShareLinkAudioResponse(audioUrl = audio.audioUrl)
    }

    private fun resolveSharedNarration(source: Source): SharedSourceAudioData? {
        source.audioContent?.let { audioContent ->
            return buildSharedSourceAudio(audioContent)
        }

        val contentText = source.content?.text?.takeIf { it.isNotBlank() } ?: return null
        val preferredProvider = ttsSettingsService.resolvePreferredProvider(source.userId) ?: return null
        val languageCode = narrationLanguageResolver.resolve(source.metadata?.transcriptLanguage, contentText)
        val voiceId = ttsVoiceResolver.resolveVoiceId(preferredProvider.providerType, languageCode)
        val cachedAudio = NarrationContentHashing.lookupHashes(contentText)
            .firstNotNullOfOrNull { hash ->
                sharedAudioCacheRepository.findByContentHashAndProviderTypeAndVoiceIdAndModelId(
                    hash,
                    preferredProvider.providerType,
                    voiceId,
                    preferredProvider.modelId
                )
            } ?: return null

        return try {
            SharedSourceAudioData(
                audioUrl = audioStorageService.generatePresignedGetUrl(
                    cachedAudio.contentHash,
                    cachedAudio.providerType,
                    cachedAudio.voiceId,
                    cachedAudio.modelId
                ),
                durationSeconds = cachedAudio.durationSeconds,
                format = cachedAudio.format
            )
        } catch (ex: Exception) {
            logger.warn(
                "[service] Share narration cache presign failed sourceId={} contentHash={}",
                source.id,
                cachedAudio.contentHash,
                ex
            )
            null
        }
    }

    private fun buildSharedSourceAudio(audioContent: AudioContent): SharedSourceAudioData? {
        return try {
            val providerType = audioContent.providerType ?: TtsProviderType.ELEVENLABS
            val voiceId = audioContent.voiceId ?: return null
            SharedSourceAudioData(
                audioUrl = audioStorageService.generatePresignedGetUrl(
                    audioContent.contentHash,
                    providerType,
                    voiceId,
                    audioContent.modelId
                ),
                durationSeconds = audioContent.durationSeconds,
                format = audioContent.format
            )
        } catch (ex: Exception) {
            logger.warn(
                "[service] Share narration source presign failed contentHash={}",
                audioContent.contentHash,
                ex
            )
            null
        }
    }

    private fun resolveOgImageUrl(source: Source, baseUrl: String, token: String): String {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        val metadataOgImageUrl = source.metadata?.ogImageUrl?.trim()
        if (!metadataOgImageUrl.isNullOrBlank()) {
            return metadataOgImageUrl
        }

        val videoId = source.metadata?.videoId?.trim()
        if (source.sourceType == SourceType.VIDEO && !videoId.isNullOrBlank()) {
            return "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        }

        if (token.isNotBlank()) {
            return if (normalizedBaseUrl.isBlank()) {
                "/api/public/og-image/$token"
            } else {
                "$normalizedBaseUrl/api/public/og-image/$token"
            }
        }

        return if (normalizedBaseUrl.isBlank()) {
            "/og-images/default.png"
        } else {
            "$normalizedBaseUrl/og-images/default.png"
        }
    }

    private fun renderOgImage(source: Source): ByteArray {
        val image = BufferedImage(OG_IMAGE_WIDTH, OG_IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.color = Color(0x0A, 0x0A, 0x0A)
            graphics.fillRect(0, 0, OG_IMAGE_WIDTH, OG_IMAGE_HEIGHT)

            graphics.font = Font("SansSerif", Font.PLAIN, 28)
            graphics.color = Color(0x80, 0x80, 0x80)
            graphics.drawString("Briefy AI", 72, 72)

            val title = source.metadata?.title?.trim()?.ifBlank { null } ?: source.url.raw
            graphics.font = Font("SansSerif", Font.BOLD, 48)
            graphics.color = Color(0xFF, 0xFF, 0xFF)
            val titleLines = wrapText(
                text = title,
                fontMetrics = graphics.fontMetrics,
                maxWidth = OG_IMAGE_WIDTH - (OG_IMAGE_HORIZONTAL_PADDING * 2),
                maxLines = 3
            )

            val lineHeight = graphics.fontMetrics.height + 8
            val blockHeight = titleLines.size * lineHeight
            var y = ((OG_IMAGE_HEIGHT - blockHeight) / 2) + graphics.fontMetrics.ascent
            for (line in titleLines) {
                val x = (OG_IMAGE_WIDTH - graphics.fontMetrics.stringWidth(line)) / 2
                graphics.drawString(line, x, y)
                y += lineHeight
            }

            val subtitleParts = listOfNotNull(
                source.metadata?.author?.trim()?.ifBlank { null },
                source.sourceType.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            )
            val subtitle = subtitleParts.joinToString(" • ").ifBlank { "Briefy AI" }

            graphics.font = Font("SansSerif", Font.PLAIN, 30)
            graphics.color = Color(0xA0, 0xA0, 0xA0)
            val subtitleX = (OG_IMAGE_WIDTH - graphics.fontMetrics.stringWidth(subtitle)) / 2
            val subtitleY = (y + 28).coerceAtMost(OG_IMAGE_HEIGHT - 56)
            graphics.drawString(subtitle, subtitleX, subtitleY)
        } finally {
            graphics.dispose()
        }

        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }

    private fun wrapText(text: String, fontMetrics: FontMetrics, maxWidth: Int, maxLines: Int): List<String> {
        val normalizedText = text.replace(Regex("\\s+"), " ").trim().ifBlank { "Briefy AI" }
        val words = normalizedText.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        fun flushLine() {
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder()
            }
        }

        for (word in words) {
            val candidate = if (currentLine.isEmpty()) word else "${currentLine} $word"
            if (fontMetrics.stringWidth(candidate) <= maxWidth) {
                currentLine = StringBuilder(candidate)
                continue
            }

            flushLine()
            if (lines.size == maxLines) {
                break
            }

            if (fontMetrics.stringWidth(word) <= maxWidth) {
                currentLine.append(word)
            } else {
                currentLine.append(ellipsize(word, fontMetrics, maxWidth))
                flushLine()
            }
        }
        flushLine()

        if (lines.isEmpty()) {
            return listOf("Briefy AI")
        }
        if (lines.size > maxLines) {
            return lines.take(maxLines)
        }
        if (lines.size == maxLines && words.joinToString(" ") != lines.joinToString(" ")) {
            lines[maxLines - 1] = ellipsize(lines[maxLines - 1], fontMetrics, maxWidth)
        }
        return lines
    }

    private fun ellipsize(text: String, fontMetrics: FontMetrics, maxWidth: Int): String {
        if (fontMetrics.stringWidth(text) <= maxWidth) {
            return text
        }
        val ellipsis = "..."
        var candidate = text
        while (candidate.isNotEmpty() && fontMetrics.stringWidth("$candidate$ellipsis") > maxWidth) {
            candidate = candidate.dropLast(1)
        }
        return if (candidate.isEmpty()) ellipsis else "$candidate$ellipsis"
    }

    private fun loadDefaultOgImage(): ByteArray {
        val resourcePaths = listOf(
            "static/og-images/default.png",
            "og-images/default.png"
        )

        for (path in resourcePaths) {
            val stream = javaClass.classLoader.getResourceAsStream(path) ?: continue
            stream.use { return it.readBytes() }
        }

        return renderOgImage(
            Source.create(
                id = UUID.randomUUID(),
                rawUrl = "https://briefy.ai",
                userId = UUID.randomUUID(),
                sourceType = SourceType.BLOG
            )
        )
    }

    private fun escapeHtml(value: String): String = HtmlUtils.htmlEscape(value)

    private fun escapeHtmlAttribute(value: String): String = HtmlUtils.htmlEscape(value)

    private fun escapeJavaScriptPathSegment(value: String): String = value.replace("'", "")

    companion object {
        private const val OG_IMAGE_WIDTH = 1200
        private const val OG_IMAGE_HEIGHT = 630
        private const val OG_IMAGE_HORIZONTAL_PADDING = 96
    }
}
