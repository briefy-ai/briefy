package com.briefy.api.api.oauth

import com.briefy.api.application.oauthserver.ConnectedAppInfo
import com.briefy.api.application.oauthserver.OAuthServerService
import com.briefy.api.infrastructure.security.CurrentUserProvider
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/connected-apps")
class OAuthConnectedAppsController(
    private val oauthServerService: OAuthServerService,
    private val currentUserProvider: CurrentUserProvider
) {
    private val logger = LoggerFactory.getLogger(OAuthConnectedAppsController::class.java)

    @GetMapping
    fun listConnectedApps(): ResponseEntity<List<ConnectedAppInfo>> {
        val userId = currentUserProvider.requireUserId()
        val apps = oauthServerService.listActiveGrants(userId)
        return ResponseEntity.ok(apps)
    }

    @DeleteMapping("/{grantId}")
    fun revokeConnectedApp(@PathVariable grantId: UUID): ResponseEntity<Void> {
        val userId = currentUserProvider.requireUserId()
        logger.info("[oauth] Revoking connected app grantId={} userId={}", grantId, userId)
        oauthServerService.revokeGrant(grantId, userId)
        return ResponseEntity.noContent().build()
    }
}
