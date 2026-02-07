package com.briefy.api.api

import com.briefy.api.application.source.*
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
    private val sourceService: SourceService
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
