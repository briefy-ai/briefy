package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.Briefing
import com.briefy.api.domain.knowledgegraph.briefing.BriefingPlanStep
import com.briefy.api.domain.knowledgegraph.source.Source
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.security.MessageDigest

@Service
class ExecutionFingerprintService(
    private val objectMapper: ObjectMapper
) {

    fun compute(
        briefing: Briefing,
        orderedSources: List<Source>,
        orderedPlanSteps: List<BriefingPlanStep>
    ): String {
        val payload = linkedMapOf<String, Any?>(
            "version" to 1,
            "briefingId" to briefing.id.toString(),
            "enrichmentIntent" to briefing.enrichmentIntent.name,
            "sources" to orderedSources
                .sortedBy { it.id }
                .map { source ->
                linkedMapOf(
                    "sourceId" to source.id.toString(),
                    "title" to (source.metadata?.title ?: source.url.normalized),
                    "url" to source.url.normalized,
                    "textSha256" to sha256Hex(source.content?.text.orEmpty())
                )
            },
            "plan" to orderedPlanSteps
                .sortedBy { it.stepOrder }
                .map { step ->
                    linkedMapOf(
                        "stepOrder" to step.stepOrder,
                        "personaName" to step.personaName,
                        "task" to step.task
                    )
                }
        )

        return sha256Hex(objectMapper.writeValueAsString(payload))
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
