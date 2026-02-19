package com.briefy.api.infrastructure.ai

import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

enum class AiErrorCategory {
    TIMEOUT,
    PROVIDER_UNAVAILABLE,
    VALIDATION,
    UNKNOWN;

    companion object {
        fun from(error: Throwable): AiErrorCategory {
            if (error is IllegalArgumentException) return VALIDATION

            val root = rootCause(error)
            val message = root.message.orEmpty().lowercase()
            val typeNames = collectTypeNames(root)

            if (root is TimeoutException || root is SocketTimeoutException || message.contains("timeout")) {
                return TIMEOUT
            }

            if (
                message.contains("503") ||
                message.contains("502") ||
                message.contains("service unavailable") ||
                message.contains("connection refused") ||
                message.contains("connection reset") ||
                message.contains("network")
            ) {
                return PROVIDER_UNAVAILABLE
            }

            if (typeNames.any { it.contains("httpservererror") || it.contains("resourceaccessexception") }) {
                return PROVIDER_UNAVAILABLE
            }

            return UNKNOWN
        }

        private fun rootCause(error: Throwable): Throwable {
            var current = error
            while (current.cause != null && current.cause !== current) {
                current = current.cause!!
            }
            return current
        }

        private fun collectTypeNames(error: Throwable): Set<String> {
            val names = mutableSetOf<String>()
            var current: Class<*>? = error.javaClass
            while (current != null) {
                names.add(current.simpleName.orEmpty().lowercase())
                current = current.superclass
            }
            return names
        }
    }
}
