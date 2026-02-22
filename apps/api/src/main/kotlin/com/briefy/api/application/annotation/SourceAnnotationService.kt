package com.briefy.api.application.annotation

import com.briefy.api.domain.knowledgegraph.annotation.SourceAnnotation
import com.briefy.api.domain.knowledgegraph.annotation.SourceAnnotationArchiveCause
import com.briefy.api.domain.knowledgegraph.annotation.SourceAnnotationRepository
import com.briefy.api.domain.knowledgegraph.annotation.SourceAnnotationStatus
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.application.source.SourceNotFoundException
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.security.CurrentUserProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SourceAnnotationService(
    private val sourceAnnotationRepository: SourceAnnotationRepository,
    private val sourceRepository: SourceRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val idGenerator: IdGenerator
) {

    @Transactional(readOnly = true)
    fun listSourceAnnotations(sourceId: UUID): List<SourceAnnotationResponse> {
        val userId = currentUserProvider.requireUserId()
        ensureSourceOwned(sourceId, userId)

        return sourceAnnotationRepository.findBySourceIdAndUserIdAndStatusOrderByAnchorStartAsc(
            sourceId = sourceId,
            userId = userId,
            status = SourceAnnotationStatus.ACTIVE
        ).map { it.toResponse() }
    }

    @Transactional
    fun createSourceAnnotation(sourceId: UUID, command: CreateSourceAnnotationCommand): SourceAnnotationResponse {
        val userId = currentUserProvider.requireUserId()
        val source = ensureSourceOwned(sourceId, userId)

        require(source.status == SourceStatus.ACTIVE) {
            "Cannot annotate source in status ${source.status}"
        }
        require(command.anchorEnd > command.anchorStart) {
            "anchorEnd must be greater than anchorStart"
        }

        val hasOverlap = sourceAnnotationRepository.existsOverlappingSelection(
            sourceId = sourceId,
            userId = userId,
            status = SourceAnnotationStatus.ACTIVE,
            selectionStart = command.anchorStart,
            selectionEnd = command.anchorEnd
        )

        if (hasOverlap) {
            throw SourceAnnotationOverlapException()
        }

        val annotation = SourceAnnotation.create(
            id = idGenerator.newId(),
            sourceId = sourceId,
            userId = userId,
            body = command.body,
            anchorQuote = command.anchorQuote,
            anchorPrefix = command.anchorPrefix,
            anchorSuffix = command.anchorSuffix,
            anchorStart = command.anchorStart,
            anchorEnd = command.anchorEnd
        )

        return sourceAnnotationRepository.save(annotation).toResponse()
    }

    @Transactional
    fun updateSourceAnnotation(
        sourceId: UUID,
        annotationId: UUID,
        command: UpdateSourceAnnotationCommand
    ): SourceAnnotationResponse {
        val userId = currentUserProvider.requireUserId()
        ensureSourceOwned(sourceId, userId)

        val annotation = sourceAnnotationRepository.findByIdAndSourceIdAndUserId(annotationId, sourceId, userId)
            ?: throw SourceAnnotationNotFoundException(annotationId)

        if (annotation.status != SourceAnnotationStatus.ACTIVE) {
            throw InvalidSourceAnnotationStateException("Can only edit active annotations")
        }

        annotation.editBody(command.body)
        return sourceAnnotationRepository.save(annotation).toResponse()
    }

    @Transactional
    fun deleteSourceAnnotation(sourceId: UUID, annotationId: UUID) {
        val userId = currentUserProvider.requireUserId()
        ensureSourceOwned(sourceId, userId)

        val annotation = sourceAnnotationRepository.findByIdAndSourceIdAndUserId(annotationId, sourceId, userId)
            ?: throw SourceAnnotationNotFoundException(annotationId)

        if (annotation.status == SourceAnnotationStatus.ACTIVE) {
            annotation.archive(SourceAnnotationArchiveCause.USER_ACTION)
            sourceAnnotationRepository.save(annotation)
        }
    }

    @Transactional
    fun archiveActiveAnnotationsForSource(sourceId: UUID, userId: UUID) {
        val activeAnnotations = sourceAnnotationRepository.findBySourceIdAndUserIdAndStatus(
            sourceId = sourceId,
            userId = userId,
            status = SourceAnnotationStatus.ACTIVE
        )

        activeAnnotations.forEach { annotation ->
            annotation.archive(SourceAnnotationArchiveCause.SOURCE_ARCHIVED)
        }

        if (activeAnnotations.isNotEmpty()) {
            sourceAnnotationRepository.saveAll(activeAnnotations)
        }
    }

    @Transactional
    fun restoreSourceArchivedAnnotations(sourceId: UUID, userId: UUID) {
        val archivedFromSource = sourceAnnotationRepository.findBySourceIdAndUserIdAndStatusAndArchivedCause(
            sourceId = sourceId,
            userId = userId,
            status = SourceAnnotationStatus.ARCHIVED,
            archivedCause = SourceAnnotationArchiveCause.SOURCE_ARCHIVED
        )

        archivedFromSource.forEach { annotation ->
            annotation.restore(SourceAnnotationArchiveCause.SOURCE_RESTORED)
        }

        if (archivedFromSource.isNotEmpty()) {
            sourceAnnotationRepository.saveAll(archivedFromSource)
        }
    }

    private fun ensureSourceOwned(sourceId: UUID, userId: UUID) = sourceRepository.findByIdAndUserId(sourceId, userId)
        ?: throw SourceNotFoundException(sourceId)
}
