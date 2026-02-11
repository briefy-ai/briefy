package com.briefy.api.api

import com.briefy.api.application.source.*
import com.briefy.api.application.topic.SourceActiveTopicResponse
import com.briefy.api.application.topic.SourceTopicSuggestionResponse
import com.briefy.api.application.topic.TopicService
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/sources")
class SourceController(
    private val sourceService: SourceService,
    private val topicService: TopicService
) {
    private val logger = LoggerFactory.getLogger(SourceController::class.java)

    @PostMapping
    fun createSource(@Valid @RequestBody request: CreateSourceRequest): ResponseEntity<SourceResponse> {
        logger.info("[controller] Create source request received url={}", request.url)
        val command = CreateSourceCommand(url = request.url)
        val source = sourceService.submitSource(command)
        logger.info("[controller] Create source request completed sourceId={} status={}", source.id, source.status)
        return ResponseEntity.status(HttpStatus.CREATED).body(source)
    }

    @GetMapping
    fun listSources(@RequestParam(required = false) status: String?): ResponseEntity<List<SourceResponse>> {
        logger.info("[controller] List sources request received status={}", status ?: "all")
        val statusEnum = status?.let { SourceStatus.valueOf(it.uppercase()) }
        val sources = sourceService.listSources(statusEnum)
        logger.info("[controller] List sources request completed count={}", sources.size)
        return ResponseEntity.ok(sources)
    }

    @GetMapping("/{id}")
    fun getSource(@PathVariable id: UUID): ResponseEntity<SourceResponse> {
        logger.info("[controller] Get source request received sourceId={}", id)
        val source = sourceService.getSource(id)
        logger.info("[controller] Get source request completed sourceId={} status={}", source.id, source.status)
        return ResponseEntity.ok(source)
    }

    @PostMapping("/{id}/retry")
    fun retryExtraction(@PathVariable id: UUID): ResponseEntity<SourceResponse> {
        logger.info("[controller] Retry extraction request received sourceId={}", id)
        val source = sourceService.retryExtraction(id)
        logger.info("[controller] Retry extraction request completed sourceId={} status={}", source.id, source.status)
        return ResponseEntity.ok(source)
    }

    @DeleteMapping("/{id}")
    fun deleteSource(@PathVariable id: UUID): ResponseEntity<Unit> {
        logger.info("[controller] Delete source request received sourceId={}", id)
        sourceService.deleteSource(id)
        logger.info("[controller] Delete source request completed sourceId={}", id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/restore")
    fun restoreSource(@PathVariable id: UUID): ResponseEntity<Unit> {
        logger.info("[controller] Restore source request received sourceId={}", id)
        sourceService.restoreSource(id)
        logger.info("[controller] Restore source request completed sourceId={}", id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/topics/suggestions")
    fun listTopicSuggestions(@PathVariable id: UUID): ResponseEntity<List<SourceTopicSuggestionResponse>> {
        logger.info("[controller] List topic suggestions request received sourceId={}", id)
        val suggestions = topicService.listSourceTopicSuggestions(id)
        logger.info(
            "[controller] List topic suggestions request completed sourceId={} count={}",
            id,
            suggestions.size
        )
        return ResponseEntity.ok(suggestions)
    }

    @GetMapping("/{id}/topics/active")
    fun listActiveTopics(@PathVariable id: UUID): ResponseEntity<List<SourceActiveTopicResponse>> {
        logger.info("[controller] List source active topics request received sourceId={}", id)
        val topics = topicService.listSourceActiveTopics(id)
        logger.info(
            "[controller] List source active topics request completed sourceId={} count={}",
            id,
            topics.size
        )
        return ResponseEntity.ok(topics)
    }

    @PostMapping("/{id}/topics/apply")
    fun applyTopicSuggestions(
        @PathVariable id: UUID,
        @RequestBody(required = false) request: TopicSuggestionApplyRequest?
    ): ResponseEntity<Unit> {
        val keepCount = request?.keepTopicLinkIds?.size ?: 0
        logger.info(
            "[controller] Apply topic suggestions request received sourceId={} keepCount={}",
            id,
            keepCount
        )
        topicService.applySourceTopicSuggestions(id, request?.keepTopicLinkIds ?: emptyList())
        logger.info("[controller] Apply topic suggestions request completed sourceId={}", id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/topics/manual")
    fun addManualTopicSuggestion(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateManualTopicRequest
    ): ResponseEntity<SourceTopicSuggestionResponse> {
        logger.info(
            "[controller] Add manual topic suggestion request received sourceId={} name={}",
            id,
            request.name
        )
        val suggestion = topicService.addManualTopicSuggestionToSource(id, request.name)
        logger.info(
            "[controller] Add manual topic suggestion request completed sourceId={} topicLinkId={}",
            id,
            suggestion.topicLinkId
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(suggestion)
    }

    @PostMapping("/archive-batch")
    fun archiveSourcesBatch(@Valid @RequestBody request: ArchiveSourcesBatchRequest): ResponseEntity<Unit> {
        logger.info("[controller] Batch archive request received count={}", request.sourceIds.size)
        sourceService.archiveSourcesBatch(request.sourceIds)
        logger.info("[controller] Batch archive request completed count={}", request.sourceIds.size)
        return ResponseEntity.noContent().build()
    }
}

data class CreateSourceRequest(
    @field:NotBlank(message = "URL is required")
    val url: String
)

data class ArchiveSourcesBatchRequest(
    @field:NotEmpty(message = "sourceIds must not be empty")
    val sourceIds: List<UUID>
)

data class TopicSuggestionApplyRequest(
    val keepTopicLinkIds: List<UUID> = emptyList()
)

data class CreateManualTopicRequest(
    @field:NotBlank(message = "name must not be blank")
    val name: String
)
