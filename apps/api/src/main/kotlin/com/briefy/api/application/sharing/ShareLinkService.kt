package com.briefy.api.application.sharing

import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.sharing.ShareLink
import com.briefy.api.domain.sharing.ShareLinkEntityType
import com.briefy.api.domain.sharing.ShareLinkRepository
import com.briefy.api.infrastructure.security.CurrentUserProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ShareLinkService(
    private val shareLinkRepository: ShareLinkRepository,
    private val sourceRepository: SourceRepository,
    private val currentUserProvider: CurrentUserProvider
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
        logger.info("[service] Share link created id={} token={} userId={}", shareLink.id, shareLink.token, userId)
        return shareLink.toResponse()
    }

    @Transactional(readOnly = true)
    fun resolve(token: String): SharedSourceResponse {
        val shareLink = shareLinkRepository.findByToken(token)
            ?: throw ShareLinkNotFoundException(token)

        if (shareLink.revokedAt != null) {
            throw ShareLinkNotFoundException(token)
        }
        if (shareLink.expiresAt != null && Instant.now().isAfter(shareLink.expiresAt)) {
            throw ShareLinkExpiredException(token)
        }

        return when (shareLink.entityType) {
            ShareLinkEntityType.SOURCE -> resolveSource(shareLink)
            ShareLinkEntityType.BRIEFING -> throw ShareLinkNotFoundException(token) // not yet supported
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

        require(shareLink.userId == userId) { "Not authorized to revoke this share link" }

        shareLink.revoke()
        shareLinkRepository.save(shareLink)
        logger.info("[service] Share link revoked id={} userId={}", shareLinkId, userId)
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

    private fun resolveSource(shareLink: ShareLink): SharedSourceResponse {
        val source = sourceRepository.findById(shareLink.entityId).orElse(null)
            ?: throw ShareLinkNotFoundException(shareLink.token)

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
                content = source.content?.text
            )
        )
    }
}
