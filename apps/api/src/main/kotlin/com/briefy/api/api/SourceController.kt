package com.briefy.api.api

import com.briefy.api.application.source.*
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/sources")
class SourceController(
    private val sourceService: SourceService
) {

    @PostMapping
    fun createSource(@Valid @RequestBody request: CreateSourceRequest): ResponseEntity<SourceResponse> {
        val command = CreateSourceCommand(url = request.url)
        val source = sourceService.submitSource(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(source)
    }

    @GetMapping
    fun listSources(@RequestParam status: String?): ResponseEntity<List<SourceResponse>> {
        val statusEnum = status?.let {
            try {
                SourceStatus.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        val sources = sourceService.listSources(statusEnum)
        return ResponseEntity.ok(sources)
    }

    @GetMapping("/{id}")
    fun getSource(@PathVariable id: UUID): ResponseEntity<SourceResponse> {
        val source = sourceService.getSource(id)
        return ResponseEntity.ok(source)
    }

    @PostMapping("/{id}/retry")
    fun retryExtraction(@PathVariable id: UUID): ResponseEntity<SourceResponse> {
        val source = sourceService.retryExtraction(id)
        return ResponseEntity.ok(source)
    }
}

data class CreateSourceRequest(
    @field:NotBlank(message = "URL is required")
    val url: String
)
