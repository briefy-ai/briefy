package com.briefy.api.api

import com.briefy.api.application.topic.TopicDetailResponse
import com.briefy.api.application.topic.TopicService
import com.briefy.api.application.topic.TopicSummaryResponse
import com.briefy.api.domain.knowledgegraph.topic.TopicStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
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
@RequestMapping("/api/topics")
class TopicController(
    private val topicService: TopicService
) {
    private val logger = LoggerFactory.getLogger(TopicController::class.java)

    @PostMapping
    fun createTopic(
        @Valid @RequestBody request: CreateTopicRequest
    ): ResponseEntity<TopicSummaryResponse> {
        logger.info(
            "[controller] Create topic request received name={} sourceCount={}",
            request.name,
            request.sourceIds?.size ?: 0
        )
        val topic = topicService.createTopic(request.name, request.sourceIds.orEmpty())
        logger.info("[controller] Create topic request completed topicId={}", topic.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(topic)
    }

    @GetMapping
    fun listTopics(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) q: String?
    ): ResponseEntity<List<TopicSummaryResponse>> {
        val topicStatus = status?.let { TopicStatus.valueOf(it.uppercase()) } ?: TopicStatus.ACTIVE
        logger.info("[controller] List topics request received status={} q={}", topicStatus, q ?: "")
        val topics = topicService.listTopics(topicStatus, q)
        logger.info("[controller] List topics request completed count={}", topics.size)
        return ResponseEntity.ok(topics)
    }

    @GetMapping("/{id}")
    fun getTopic(@PathVariable id: UUID): ResponseEntity<TopicDetailResponse> {
        logger.info("[controller] Get topic request received topicId={}", id)
        val topic = topicService.getTopic(id)
        logger.info("[controller] Get topic request completed topicId={} linkedSources={}", id, topic.linkedSources.size)
        return ResponseEntity.ok(topic)
    }
}

data class CreateTopicRequest(
    @field:NotBlank(message = "name must not be blank")
    val name: String,
    val sourceIds: List<UUID>? = null
)
