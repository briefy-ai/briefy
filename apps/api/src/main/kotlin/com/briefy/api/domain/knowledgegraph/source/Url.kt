package com.briefy.api.domain.knowledgegraph.source

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.net.URI

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

            // Normalize: lowercase host, remove www., remove trailing slash, remove fragment
            val host = uri.host?.lowercase()?.removePrefix("www.")
                ?: throw IllegalArgumentException("Invalid URL: no host")

            val path = uri.path?.trimEnd('/') ?: ""
            val query = uri.query?.let { "?$it" } ?: ""

            return "https://$host$path$query"
        }

        fun detectPlatform(normalizedUrl: String): String {
            val uri = URI.create(normalizedUrl)
            val host = uri.host?.lowercase() ?: return "web"

            for ((platform, patterns) in PLATFORM_PATTERNS) {
                if (patterns.any { host.contains(it) }) {
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
    }
}
