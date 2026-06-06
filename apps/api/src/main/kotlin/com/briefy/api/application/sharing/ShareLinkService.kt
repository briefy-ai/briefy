package com.briefy.api.application.sharing

import com.briefy.api.application.source.OriginalVideoAudioService
import com.briefy.api.application.source.NarrationContentHashing
import com.briefy.api.domain.knowledgegraph.source.AudioContent
import com.briefy.api.domain.knowledgegraph.source.SharedAudioCacheRepository
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceType
import com.briefy.api.domain.sharing.ShareLink
import com.briefy.api.domain.sharing.ShareLinkEntityType
import com.briefy.api.domain.sharing.ShareLinkRepository
import com.briefy.api.infrastructure.imagegen.ImageStorageService
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.briefy.api.infrastructure.tts.AudioStorageService
import com.briefy.api.infrastructure.tts.ElevenLabsTtsProperties
import com.briefy.api.infrastructure.tts.TtsProviderType
import com.briefy.api.infrastructure.tts.TtsVoiceResolver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.util.HtmlUtils
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Point2D
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
    private val imageStorageService: ImageStorageService,
    private val ttsVoiceResolver: TtsVoiceResolver,
    private val elevenLabsProperties: ElevenLabsTtsProperties,
    private val originalVideoAudioService: OriginalVideoAudioService,
    private val coverImageService: CoverImageService,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(ShareLinkService::class.java)

    fun create(request: CreateShareLinkRequest): ShareLinkResponse {
        val userId = currentUserProvider.requireUserId()
        logger.info("[service] Creating share link userId={} entityType={} entityId={}", userId, request.entityType, request.entityId)

        val source = validateEntityOwnership(userId, request.entityType, request.entityId)
        val shouldGenerateCover = request.generateCoverImage && source != null && !source.hasGeneratedCoverImage()
        if (request.generateCoverImage && source != null && !shouldGenerateCover) {
            logger.info(
                "[service] Cover image generation skipped sourceId={} userId={} reason=existing_generated_image",
                source.id,
                userId
            )
        }
        val coverResult = if (shouldGenerateCover) {
            coverImageService.generateAndStore(source, userId)
        } else {
            null
        }

        val shareLink = transactionTemplate.execute<ShareLink> {
            val createdShareLink = ShareLink(
                token = ShareLink.generateToken(),
                entityType = request.entityType,
                entityId = request.entityId,
                userId = userId,
                expiresAt = request.expiresAt
            )
            shareLinkRepository.save(createdShareLink)

            if (coverResult != null && request.entityType == ShareLinkEntityType.SOURCE) {
                val sourceToUpdate = sourceRepository.findByIdAndUserId(request.entityId, userId)
                    ?: throw IllegalArgumentException("Source not found: ${request.entityId}")
                sourceToUpdate.coverImageKey = coverResult.coverKey
                sourceToUpdate.featuredImageKey = coverResult.featuredKey
                sourceToUpdate.updatedAt = Instant.now()
                sourceRepository.save(sourceToUpdate)
            }

            createdShareLink
        } ?: throw IllegalStateException("Failed to create share link")

        logger.info("[service] Share link created id={} userId={}", shareLink.id, userId)
        return shareLink.toResponse(coverImageGenerated = coverResult != null)
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

        // Serve the generated featured image if available
        val featuredKey = source.featuredImageKey?.trim()?.ifBlank { null }
        if (featuredKey != null) {
            return try {
                imageStorageService.downloadImage(featuredKey)
            } catch (ex: Exception) {
                logger.warn("[service] Failed to download featured image key={}, falling back to rendered", featuredKey, ex)
                try { renderOgImage(source) } catch (_: Exception) { loadDefaultOgImage() }
            }
        }

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

    private fun validateEntityOwnership(userId: UUID, entityType: ShareLinkEntityType, entityId: UUID): Source? {
        return when (entityType) {
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
                coverImageUrl = resolveFeaturedImageUrl(source),
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

        val videoId = source.metadata?.videoId?.takeIf { source.url.platform.equals("youtube", ignoreCase = true) && !it.isNullOrBlank() }
        if (!videoId.isNullOrBlank()) {
            return try {
                buildSharedSourceAudio(originalVideoAudioService.findCachedAudio(videoId) ?: return null)
            } catch (ex: Exception) {
                logger.warn(
                    "[service] Share source audio cache presign failed sourceId={} videoId={}",
                    source.id,
                    videoId,
                    ex
                )
                return null
            }
        }

        val contentText = source.content?.text?.takeIf { it.isNotBlank() } ?: return null
        val cachedAudio = NarrationContentHashing.lookupHashes(contentText, source.metadata?.transcriptLanguage)
            .firstNotNullOfOrNull { hash ->
                sharedAudioCacheRepository.findFirstByContentHashOrderByCreatedAtDesc(hash)
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
            val voiceId = audioContent.voiceId ?: legacyVoiceId(providerType)
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

    private fun legacyVoiceId(providerType: TtsProviderType): String {
        return when (providerType) {
            TtsProviderType.ELEVENLABS -> elevenLabsProperties.voiceId
            TtsProviderType.INWORLD -> ttsVoiceResolver.resolveVoiceId(TtsProviderType.INWORLD, "en")
        }
    }

    private fun resolveOgImageUrl(source: Source, baseUrl: String, token: String): String {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')

        // If a featured image exists, serve it through our own og-image endpoint
        // (not the presigned S3 URL, which social crawlers often can't fetch)
        if (!source.featuredImageKey.isNullOrBlank()) {
            return if (normalizedBaseUrl.isBlank()) {
                "/api/public/og-image/$token"
            } else {
                "$normalizedBaseUrl/api/public/og-image/$token"
            }
        }

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

    private fun resolveFeaturedImageUrl(source: Source): String? {
        val featuredImageKey = source.featuredImageKey?.trim()?.ifBlank { null } ?: return null
        return try {
            imageStorageService.generatePresignedGetUrl(featuredImageKey)
        } catch (ex: Exception) {
            logger.warn(
                "[service] Share featured image presign failed sourceId={} key={}",
                source.id,
                featuredImageKey,
                ex
            )
            null
        }
    }

    private val soraFont: Font by lazy { loadBundledFont("fonts/Sora-VF.ttf", fallback = "SansSerif") }
    private val monoFont: Font by lazy { loadBundledFont("fonts/JetBrainsMono-VF.ttf", fallback = "Monospaced") }

    private fun loadBundledFont(resourcePath: String, fallback: String): Font {
        return try {
            javaClass.classLoader.getResourceAsStream(resourcePath)?.use { stream ->
                Font.createFont(Font.TRUETYPE_FONT, stream)
            } ?: Font(fallback, Font.PLAIN, 12)
        } catch (ex: Exception) {
            logger.warn("[service] Failed to load bundled font {} — falling back to {}", resourcePath, fallback, ex)
            Font(fallback, Font.PLAIN, 12)
        }
    }

    private fun renderOgImage(source: Source): ByteArray {
        val image = BufferedImage(OG_IMAGE_WIDTH, OG_IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)

            paintBackground(g)
            paintGrid(g)
            paintGrain(g)
            paintBrandRow(g, source)
            val titleEndY = paintTitle(g, source)
            paintMeta(g, source, titleEndY)
        } finally {
            g.dispose()
        }

        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "jpg", output)
            output.toByteArray()
        }
    }

    private fun paintBackground(g: java.awt.Graphics2D) {
        g.paint = GradientPaint(
            0f, 0f, Color(0x1A, 0x0E, 0x0E),
            OG_IMAGE_WIDTH.toFloat(), OG_IMAGE_HEIGHT.toFloat(), Color(0x2C, 0x14, 0x14)
        )
        g.fillRect(0, 0, OG_IMAGE_WIDTH, OG_IMAGE_HEIGHT)

        g.paint = RadialGradientPaint(
            Point2D.Float(0f, 0f),
            OG_IMAGE_WIDTH * 0.9f,
            floatArrayOf(0f, 1f),
            arrayOf(Color(0x60, 0x30, 0x2E, 178), Color(0x60, 0x30, 0x2E, 0)),
            MultipleGradientPaint.CycleMethod.NO_CYCLE
        )
        g.fillRect(0, 0, OG_IMAGE_WIDTH, OG_IMAGE_HEIGHT)

        g.paint = RadialGradientPaint(
            Point2D.Float(OG_IMAGE_WIDTH * 0.85f, OG_IMAGE_HEIGHT * 1.1f),
            OG_IMAGE_WIDTH * 1.1f,
            floatArrayOf(0f, 0.55f),
            arrayOf(Color(0xBC, 0x67, 0x53, 140), Color(0xBC, 0x67, 0x53, 0)),
            MultipleGradientPaint.CycleMethod.NO_CYCLE
        )
        g.fillRect(0, 0, OG_IMAGE_WIDTH, OG_IMAGE_HEIGHT)
    }

    private fun paintGrid(g: java.awt.Graphics2D) {
        g.color = Color(0xFA, 0xF4, 0xF2, 10)
        g.stroke = BasicStroke(1f)
        var x = OG_GRID_SIZE
        while (x < OG_IMAGE_WIDTH) { g.drawLine(x, 0, x, OG_IMAGE_HEIGHT); x += OG_GRID_SIZE }
        var y = OG_GRID_SIZE
        while (y < OG_IMAGE_HEIGHT) { g.drawLine(0, y, OG_IMAGE_WIDTH, y); y += OG_GRID_SIZE }
    }

    private fun paintGrain(g: java.awt.Graphics2D) {
        val grain = BufferedImage(OG_IMAGE_WIDTH, OG_IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val rnd = java.util.Random(0x6B1EFC)
        for (y in 0 until OG_IMAGE_HEIGHT) {
            for (x in 0 until OG_IMAGE_WIDTH) {
                val v = rnd.nextInt(256)
                grain.setRGB(x, y, (0xFF shl 24) or (v shl 16) or (v shl 8) or v)
            }
        }
        val prev = g.composite
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.06f)
        g.drawImage(grain, 0, 0, null)
        g.composite = prev
    }

    private fun paintBrandRow(g: java.awt.Graphics2D, source: Source) {
        g.font = soraFont.deriveFont(Font.BOLD, 28f)
        val brandFm = g.fontMetrics
        val rowTop = OG_VERTICAL_PADDING
        val brandBaseline = rowTop + brandFm.ascent
        val rowCenter = rowTop + brandFm.height / 2

        val dotSize = 14
        g.color = Color(0xBC, 0x67, 0x53)
        g.fillOval(OG_IMAGE_HORIZONTAL_PADDING, rowCenter - dotSize / 2, dotSize, dotSize)

        g.color = Color(0xFA, 0xF4, 0xF2)
        g.drawString("Briefy AI", OG_IMAGE_HORIZONTAL_PADDING + dotSize + 14, brandBaseline)

        val tagText = source.sourceType.name.uppercase()
        g.font = monoFont.deriveFont(Font.BOLD, 18f)
        val tagFm = g.fontMetrics
        val tagPadX = 16
        val tagPadY = 9
        val tagW = tagFm.stringWidth(tagText) + tagPadX * 2
        val tagH = tagFm.ascent + tagPadY * 2
        val tagX = OG_IMAGE_WIDTH - OG_IMAGE_HORIZONTAL_PADDING - tagW
        val tagY = rowCenter - tagH / 2
        g.color = Color(0xD8, 0xAB, 0x99, 90)
        g.stroke = BasicStroke(1f)
        g.drawRoundRect(tagX, tagY, tagW, tagH, 10, 10)
        g.color = Color(0xD8, 0xAB, 0x99)
        g.drawString(tagText, tagX + tagPadX, tagY + tagPadY + tagFm.ascent)
    }

    private fun paintTitle(g: java.awt.Graphics2D, source: Source): Int {
        val title = source.metadata?.title?.trim()?.ifBlank { null } ?: source.url.raw
        g.font = soraFont.deriveFont(Font.BOLD, 70f)
        g.color = Color(0xFA, 0xF4, 0xF2)
        val fm = g.fontMetrics
        val titleLines = TextLayoutHelper.wrapText(
            text = title,
            fontMetrics = fm,
            maxWidth = OG_IMAGE_WIDTH - (OG_IMAGE_HORIZONTAL_PADDING * 2),
            maxLines = 3
        )
        val lineHeight = (fm.height * 0.95).toInt()
        val blockHeight = titleLines.size * lineHeight
        val startY = (OG_IMAGE_HEIGHT - blockHeight) / 2 + fm.ascent - 20
        var y = startY
        for (line in titleLines) {
            g.drawString(line, OG_IMAGE_HORIZONTAL_PADDING, y)
            y += lineHeight
        }
        return y
    }

    private fun paintMeta(g: java.awt.Graphics2D, source: Source, titleEndY: Int) {
        val author = source.metadata?.author?.trim()?.ifBlank { null }
        val metaText = when {
            author != null -> if (author.startsWith("@")) author else "@$author"
            else -> hostnameOf(source.url.raw)
        } ?: return

        g.font = monoFont.deriveFont(Font.PLAIN, 20f)
        g.color = Color(0xD8, 0xAB, 0x99)
        val fm = g.fontMetrics
        val y = OG_IMAGE_HEIGHT - OG_VERTICAL_PADDING + fm.ascent - fm.height
        g.drawString(metaText, OG_IMAGE_HORIZONTAL_PADDING, y.coerceAtLeast(titleEndY + 40))
    }

    private fun hostnameOf(raw: String): String? = try {
        java.net.URI(raw).host?.removePrefix("www.")
    } catch (_: Exception) { null }

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
        private const val OG_VERTICAL_PADDING = 80
        private const val OG_GRID_SIZE = 56
    }
}
