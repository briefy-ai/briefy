package com.briefy.api.infrastructure.ai

import org.springframework.ai.chat.model.ChatResponse

internal object GoogleGenAiSpringSupport {
    fun extractText(response: ChatResponse): String {
        val generations = response.results
        if (generations.isEmpty()) {
            return ""
        }

        val indexedTexts = generations.map { generation ->
            IndexedText(
                candidateIndex = extractCandidateIndex(generation.output.metadata["candidateIndex"]),
                text = generation.output.text.orEmpty().trim()
            )
        }

        val firstCandidateParts = indexedTexts.filter { it.candidateIndex == 0 }
        val selectedTexts = if (firstCandidateParts.isNotEmpty()) {
            firstCandidateParts
        } else {
            indexedTexts
        }

        return selectedTexts
            .map { it.text }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()
    }

    fun unwrapFailure(error: RuntimeException): RuntimeException {
        val rootCause = rootCause(error)
        val message = rootCause.message?.takeIf { it.isNotBlank() }
            ?: error.message?.takeIf { it.isNotBlank() }
            ?: "Google GenAI call failed"

        return if (rootCause === error) {
            error
        } else {
            RuntimeException(message, rootCause)
        }
    }

    private fun extractCandidateIndex(value: Any?): Int? {
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun rootCause(error: Throwable): Throwable {
        var current = error
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }

    private data class IndexedText(
        val candidateIndex: Int?,
        val text: String
    )
}
