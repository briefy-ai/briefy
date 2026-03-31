package com.briefy.api.infrastructure.ai

import org.springframework.stereotype.Component

@Component
class AiPayloadSanitizer {
    fun sanitize(input: String): String {
        if (input.isBlank()) return input

        return input
            .replace(API_KEY_PATTERN, "[REDACTED]")
            .replace(BEARER_PATTERN, "Bearer [REDACTED]")
            .replace(SECRET_ASSIGNMENT_PATTERN, "$1=[REDACTED]")
    }

    companion object {
        private val API_KEY_PATTERN = Regex("""(?i)\b(?:sk|pk)-[a-z0-9_-]{10,}\b""")
        private val BEARER_PATTERN = Regex("""(?i)\bBearer\s+[a-z0-9._-]{10,}\b""")
        private val SECRET_ASSIGNMENT_PATTERN = Regex("""(?i)\b(api[_-]?key|secret|token|password)\s*=\s*[^\s,;]+""")
    }
}
