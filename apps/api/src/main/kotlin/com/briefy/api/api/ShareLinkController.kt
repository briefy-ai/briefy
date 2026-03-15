package com.briefy.api.api

import com.briefy.api.application.sharing.*
import com.briefy.api.domain.sharing.ShareLinkEntityType
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class ShareLinkController(
    private val shareLinkService: ShareLinkService
) {
    private val logger = LoggerFactory.getLogger(ShareLinkController::class.java)

    @PostMapping("/api/v1/share-links")
    fun create(@Valid @RequestBody request: CreateShareLinkRequest): ResponseEntity<ShareLinkResponse> {
        logger.info("[controller] Create share link entityType={} entityId={}", request.entityType, request.entityId)
        val response = shareLinkService.create(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/api/v1/share-links")
    fun list(
        @RequestParam entityType: ShareLinkEntityType,
        @RequestParam entityId: UUID
    ): ResponseEntity<List<ShareLinkResponse>> {
        logger.info("[controller] List share links entityType={} entityId={}", entityType, entityId)
        val links = shareLinkService.list(entityType, entityId)
        return ResponseEntity.ok(links)
    }

    @DeleteMapping("/api/v1/share-links/{id}")
    fun revoke(@PathVariable id: UUID): ResponseEntity<Void> {
        logger.info("[controller] Revoke share link id={}", id)
        shareLinkService.revoke(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/api/public/share/{token}")
    fun resolve(@PathVariable token: String): ResponseEntity<SharedSourceResponse> {
        logger.info("[controller] Resolve share link token={}…", token.take(8))
        val response = shareLinkService.resolve(token)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/api/public/share/{token}/audio")
    fun resolveAudio(@PathVariable token: String): ResponseEntity<ShareLinkAudioResponse> {
        logger.info("[controller] Resolve share link audio token={}…", token.take(8))
        val response = shareLinkService.resolveAudio(token)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/api/public/share-html/{token}", produces = [MediaType.TEXT_HTML_VALUE])
    fun shareHtml(
        @PathVariable token: String,
        @RequestHeader("Host", defaultValue = "") host: String,
        @RequestHeader("X-Forwarded-Proto", defaultValue = "https") proto: String
    ): ResponseEntity<String> {
        logger.info("[controller] Build share html token={}…", token.take(8))
        val html = shareLinkService.buildShareHtml(token, buildBaseUrl(host, proto))
        return ResponseEntity.ok(html)
    }

    @GetMapping("/api/public/og-image/{token}", produces = ["image/png"])
    fun shareOgImage(@PathVariable token: String): ResponseEntity<ByteArray> {
        logger.info("[controller] Build share og:image token={}…", token.take(8))
        val imageBytes = shareLinkService.buildOgImage(token)
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(imageBytes)
    }

    private fun buildBaseUrl(host: String, proto: String): String {
        val normalizedHost = host.trim()
        if (normalizedHost.isBlank()) {
            return ""
        }
        val normalizedProto = proto.trim().ifBlank { "https" }
        return "${normalizedProto}://${normalizedHost}".trimEnd('/')
    }
}
