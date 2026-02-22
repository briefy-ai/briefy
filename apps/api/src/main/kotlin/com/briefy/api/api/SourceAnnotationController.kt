package com.briefy.api.api

import com.briefy.api.application.annotation.CreateSourceAnnotationCommand
import com.briefy.api.application.annotation.SourceAnnotationResponse
import com.briefy.api.application.annotation.SourceAnnotationService
import com.briefy.api.application.annotation.UpdateSourceAnnotationCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/sources/{sourceId}/annotations")
class SourceAnnotationController(
    private val sourceAnnotationService: SourceAnnotationService
) {

    @GetMapping
    fun listSourceAnnotations(@PathVariable sourceId: UUID): ResponseEntity<List<SourceAnnotationResponse>> {
        return ResponseEntity.ok(sourceAnnotationService.listSourceAnnotations(sourceId))
    }

    @PostMapping
    fun createSourceAnnotation(
        @PathVariable sourceId: UUID,
        @Valid @RequestBody request: CreateSourceAnnotationRequest
    ): ResponseEntity<SourceAnnotationResponse> {
        val annotation = sourceAnnotationService.createSourceAnnotation(
            sourceId = sourceId,
            command = CreateSourceAnnotationCommand(
                body = request.body,
                anchorQuote = request.anchorQuote,
                anchorPrefix = request.anchorPrefix,
                anchorSuffix = request.anchorSuffix,
                anchorStart = request.anchorStart,
                anchorEnd = request.anchorEnd
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(annotation)
    }

    @PatchMapping("/{annotationId}")
    fun updateSourceAnnotation(
        @PathVariable sourceId: UUID,
        @PathVariable annotationId: UUID,
        @Valid @RequestBody request: UpdateSourceAnnotationRequest
    ): ResponseEntity<SourceAnnotationResponse> {
        val annotation = sourceAnnotationService.updateSourceAnnotation(
            sourceId = sourceId,
            annotationId = annotationId,
            command = UpdateSourceAnnotationCommand(body = request.body)
        )
        return ResponseEntity.ok(annotation)
    }

    @DeleteMapping("/{annotationId}")
    fun deleteSourceAnnotation(
        @PathVariable sourceId: UUID,
        @PathVariable annotationId: UUID
    ): ResponseEntity<Unit> {
        sourceAnnotationService.deleteSourceAnnotation(sourceId, annotationId)
        return ResponseEntity.noContent().build()
    }
}

data class CreateSourceAnnotationRequest(
    @field:NotBlank(message = "body must not be blank")
    val body: String,

    @field:NotBlank(message = "anchorQuote must not be blank")
    val anchorQuote: String,

    val anchorPrefix: String = "",

    val anchorSuffix: String = "",

    @field:Min(value = 0, message = "anchorStart must be >= 0")
    val anchorStart: Int,

    @field:Min(value = 1, message = "anchorEnd must be >= 1")
    val anchorEnd: Int
)

data class UpdateSourceAnnotationRequest(
    @field:NotBlank(message = "body must not be blank")
    val body: String
)
