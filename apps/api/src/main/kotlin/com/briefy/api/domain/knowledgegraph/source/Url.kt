package com.briefy.api.domain.knowledgegraph.source

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Embeddable
data class Url(
    @Column(name = "url_raw", nullable = false, length = 2048)
    val raw: String,

    @Column(name = "url_normalized", nullable = false, length = 2048)
    val normalized: String,

    @Column(name = "url_platform", nullable = false, length = 50)
    val platform: String
) {
    companion object {
        private val PLATFORM_PATTERNS = mapOf(
            "youtube" to listOf("youtube.com", "youtu.be"),
            "twitter" to listOf("twitter.com", "x.com"),
            "posthog" to listOf("posthog.com"),
            "reddit" to listOf("reddit.com"),
            "medium" to listOf("medium.com"),
            "substack" to listOf("substack.com"),
            "github" to listOf("github.com"),
            "arxiv" to listOf("arxiv.org"),
            "wikipedia" to listOf("wikipedia.org")
        )

        fun from(rawUrl: String): Url {
            require(rawUrl.isNotBlank()) { "URL cannot be blank" }

            val normalized = normalize(rawUrl)
            val platform = detectPlatform(normalized)

            return Url(
                raw = rawUrl,
                normalized = normalized,
                platform = platform
            )
        }

        fun normalize(url: String): String {
            val trimmed = url.trim()

            // Add https if no protocol
            val withProtocol = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                "https://$trimmed"
            } else {
                trimmed
            }

            val uri = URI.create(withProtocol)
            val scheme = uri.scheme?.lowercase()
            require(scheme == "http" || scheme == "https") { "Only http/https URLs are supported" }

            // Normalize: lowercase host, remove www., remove trailing slash, remove fragment
            val host = uri.host?.lowercase()?.removePrefix("www.")
                ?: throw IllegalArgumentException("Invalid URL: no host")

            val port = if (uri.port == -1 || uri.port == 80 || uri.port == 443) "" else ":${uri.port}"
            val path = uri.path?.trimEnd('/') ?: ""
            val canonicalQuery = canonicalizeQuery(uri.rawQuery)
            val query = canonicalQuery?.let { "?$it" } ?: ""

            return "https://$host$port$path$query"
        }

        fun detectPlatform(normalizedUrl: String): String {
            val uri = URI.create(normalizedUrl)
            val host = uri.host?.lowercase() ?: return "web"

            for ((platform, patterns) in PLATFORM_PATTERNS) {
                if (patterns.any { host == it || host.endsWith(".$it") }) {
                    return platform
                }
            }

            return "web"
        }

        fun isValid(url: String): Boolean {
            return try {
                normalize(url)
                true
            } catch (e: Exception) {
                false
            }
        }

        private val DROP_QUERY_PARAMS = setOf(
            "fbclid",
            "gclid",
            "dclid",
            "msclkid",
            "mc_cid",
            "mc_eid",
            "igshid",
            "si",
            "access_token",
            "id_token",
            "token",
            "auth",
            "authorization",
            "jwt",
            "signature",
            "sig",
            "x-amz-signature",
            "x-amz-security-token",
            "x-goog-signature"
        )

        private fun canonicalizeQuery(rawQuery: String?): String? {
            if (rawQuery.isNullOrBlank()) return null

            val canonical = rawQuery.split("&")
                .asSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { entry ->
                    val parts = entry.split("=", limit = 2)
                    val key = parts[0].trim()
                    if (key.isBlank()) {
                        null
                    } else {
                        val decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8).lowercase()
                        if (decodedKey.startsWith("utm_") || decodedKey in DROP_QUERY_PARAMS) {
                            null
                        } else {
                            val value = if (parts.size == 2) parts[1] else ""
                            key to value
                        }
                    }
                }
                .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
                .toList()

            if (canonical.isEmpty()) return null
            return canonical.joinToString("&") { (key, value) ->
                if (value.isBlank()) key else "$key=$value"
            }
        }
    }
}
