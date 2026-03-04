package com.briefy.api.api

import com.briefy.api.application.sharing.*
import com.briefy.api.domain.sharing.ShareLinkEntityType
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
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
        return ResponseEntity.ok().build()
    }

    @GetMapping("/api/public/share/{token}")
    fun resolve(@PathVariable token: String): ResponseEntity<SharedSourceResponse> {
        logger.info("[controller] Resolve share link token={}", token)
        val response = shareLinkService.resolve(token)
        return ResponseEntity.ok(response)
    }
}
