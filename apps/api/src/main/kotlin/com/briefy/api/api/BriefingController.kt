package com.briefy.api.api

import com.briefy.api.application.briefing.BriefingResponse
import com.briefy.api.application.briefing.BriefingService
import com.briefy.api.application.briefing.CreateBriefingCommand
import com.briefy.api.domain.knowledgegraph.briefing.BriefingStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/briefings")
class BriefingController(
    private val briefingService: BriefingService
) {
    private val logger = LoggerFactory.getLogger(BriefingController::class.java)

    @PostMapping
    fun createBriefing(@Valid @RequestBody request: CreateBriefingRequest): ResponseEntity<BriefingResponse> {
        logger.info(
            "[controller] Create briefing request received sourceCount={} enrichmentIntent={}",
            request.sourceIds.size,
            request.enrichmentIntent
        )

        val briefing = briefingService.createBriefing(
            CreateBriefingCommand(
                sourceIds = request.sourceIds,
                enrichmentIntent = request.enrichmentIntent
            )
        )
        logger.info("[controller] Create briefing request completed briefingId={} status={}", briefing.id, briefing.status)
        return ResponseEntity.status(HttpStatus.CREATED).body(briefing)
    }

    @GetMapping
    fun listBriefings(@RequestParam(required = false) status: String?): ResponseEntity<List<BriefingResponse>> {
        logger.info("[controller] List briefings request received status={}", status ?: "all")
        val statusFilter = status?.let { BriefingStatus.valueOf(it.uppercase()) }
        val briefings = briefingService.listBriefings(statusFilter)
        logger.info("[controller] List briefings request completed count={}", briefings.size)
        return ResponseEntity.ok(briefings)
    }

    @GetMapping("/{id}")
    fun getBriefing(@PathVariable id: UUID): ResponseEntity<BriefingResponse> {
        logger.info("[controller] Get briefing request received briefingId={}", id)
        val briefing = briefingService.getBriefing(id)
        logger.info("[controller] Get briefing request completed briefingId={} status={}", briefing.id, briefing.status)
        return ResponseEntity.ok(briefing)
    }

    @PostMapping("/{id}/approve")
    fun approveBriefing(@PathVariable id: UUID): ResponseEntity<BriefingResponse> {
        logger.info("[controller] Approve briefing request received briefingId={}", id)
        val briefing = briefingService.approvePlan(id)
        logger.info("[controller] Approve briefing request completed briefingId={} status={}", briefing.id, briefing.status)
        return ResponseEntity.ok(briefing)
    }

    @PostMapping("/{id}/retry")
    fun retryBriefing(@PathVariable id: UUID): ResponseEntity<BriefingResponse> {
        logger.info("[controller] Retry briefing request received briefingId={}", id)
        val briefing = briefingService.retryBriefing(id)
        logger.info("[controller] Retry briefing request completed briefingId={} status={}", briefing.id, briefing.status)
        return ResponseEntity.ok(briefing)
    }
}

data class CreateBriefingRequest(
    @field:NotEmpty(message = "sourceIds must contain at least one source")
    val sourceIds: List<UUID>,
    @field:NotBlank(message = "enrichmentIntent is required")
    val enrichmentIntent: String
)
