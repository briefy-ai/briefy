package com.briefy.api.infrastructure.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class McpJson(private val objectMapper: ObjectMapper) {
    fun stringify(value: Any?): String = objectMapper.writeValueAsString(value)

    fun excerpt(text: String?, max: Int): String? {
        if (text == null) return null
        val trimmed = text.trim()
        if (trimmed.length <= max) return trimmed
        return trimmed.substring(0, max).trimEnd() + "…"
    }
}
