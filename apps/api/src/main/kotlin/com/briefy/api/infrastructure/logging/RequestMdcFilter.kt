package com.briefy.api.infrastructure.logging

import com.briefy.api.infrastructure.security.AuthenticatedUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID
import kotlin.text.RegexOption.IGNORE_CASE

@Component
class RequestMdcFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val traceContextAdded = addTraceContextIfMissing(request)
        MDC.put(USER_ID_KEY, resolveUserId())
        MDC.put(HTTP_METHOD_KEY, request.method)
        MDC.put(PATH_KEY, request.requestURI)

        try {
            filterChain.doFilter(request, response)
        } finally {
            if (traceContextAdded) {
                MDC.remove(TRACE_ID_KEY)
                MDC.remove(SPAN_ID_KEY)
            }
            MDC.remove(USER_ID_KEY)
            MDC.remove(HTTP_METHOD_KEY)
            MDC.remove(PATH_KEY)
        }
    }

    private fun addTraceContextIfMissing(request: HttpServletRequest): Boolean {
        if (!MDC.get(TRACE_ID_KEY).isNullOrBlank()) {
            return false
        }

        val traceparent = request.getHeader(TRACEPARENT_HEADER).orEmpty()
        val match = TRACEPARENT_REGEX.matchEntire(traceparent)
        val traceId = match?.groupValues?.get(1)?.lowercase() ?: UUID.randomUUID().toString().replace("-", "")
        val spanId = match?.groupValues?.get(2)?.lowercase() ?: traceId.take(SPAN_ID_LENGTH)

        MDC.put(TRACE_ID_KEY, traceId)
        MDC.put(SPAN_ID_KEY, spanId)
        return true
    }

    private fun resolveUserId(): String {
        val authentication = SecurityContextHolder.getContext().authentication ?: return ANONYMOUS_USER
        val principal = authentication.principal

        if (principal is AuthenticatedUser) {
            return principal.id.toString()
        }

        if (principal is String) {
            return principal.toUuidOrNull()?.toString() ?: ANONYMOUS_USER
        }

        return authentication.name.toUuidOrNull()?.toString() ?: ANONYMOUS_USER
    }

    private fun String.toUuidOrNull(): UUID? {
        return try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        private const val USER_ID_KEY = "userId"
        private const val HTTP_METHOD_KEY = "httpMethod"
        private const val PATH_KEY = "path"
        private const val TRACE_ID_KEY = "traceId"
        private const val SPAN_ID_KEY = "spanId"
        private const val TRACEPARENT_HEADER = "traceparent"
        private const val SPAN_ID_LENGTH = 16
        private const val ANONYMOUS_USER = "anonymous"
        private val TRACEPARENT_REGEX = Regex(
            "^[0-9a-f]{2}-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}$",
            IGNORE_CASE
        )
    }
}
