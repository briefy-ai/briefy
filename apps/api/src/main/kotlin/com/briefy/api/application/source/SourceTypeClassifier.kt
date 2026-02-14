package com.briefy.api.application.source

import com.briefy.api.domain.knowledgegraph.source.SourceType
import org.springframework.stereotype.Component
import java.net.URI

@Component
class SourceTypeClassifier {
    private val videoHosts = setOf(
        "youtube.com",
        "youtu.be"
    )

    private val researchHosts = setOf(
        "arxiv.org",
        "doi.org",
        "pubmed.ncbi.nlm.nih.gov",
        "ncbi.nlm.nih.gov",
        "researchgate.net",
        "acm.org",
        "ieeexplore.ieee.org",
        "springer.com",
        "nature.com",
        "sciencedirect.com"
    )

    private val newsHosts = setOf(
        "nytimes.com",
        "wsj.com",
        "ft.com",
        "bbc.com",
        "reuters.com",
        "apnews.com",
        "theguardian.com",
        "washingtonpost.com",
        "economist.com",
        "bloomberg.com"
    )

    fun classify(normalizedUrl: String): SourceType {
        val uri = runCatching { URI.create(normalizedUrl) }.getOrNull()
        val host = uri?.host?.lowercase().orEmpty()
        if (host.isBlank()) return SourceType.BLOG

        if (matches(host, videoHosts) && uri != null && isSupportedYouTubeVideoUrl(uri, host)) return SourceType.VIDEO
        if (matches(host, researchHosts)) return SourceType.RESEARCH
        if (matches(host, newsHosts)) return SourceType.NEWS
        return SourceType.BLOG
    }

    private fun matches(host: String, patterns: Set<String>): Boolean {
        return patterns.any { host == it || host.endsWith(".$it") }
    }

    private fun isSupportedYouTubeVideoUrl(uri: URI, host: String): Boolean {
        val path = uri.path.orEmpty()
        val normalizedPath = path.trimEnd('/')
        val query = parseQuery(uri.rawQuery)

        if (host == "youtu.be" || host.endsWith(".youtu.be")) {
            val videoId = normalizedPath.removePrefix("/")
            return videoId.isNotBlank()
        }

        if (normalizedPath == "/watch") {
            val videoId = query["v"].orEmpty()
            val hasPlaylist = query["list"]?.isNotBlank() == true
            return videoId.isNotBlank() && !hasPlaylist
        }

        if (normalizedPath.startsWith("/shorts/")) {
            val videoId = normalizedPath.removePrefix("/shorts/").substringBefore("/")
            return videoId.isNotBlank()
        }

        return false
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery
            .split("&")
            .asSequence()
            .filter { it.isNotBlank() }
            .map {
                val parts = it.split("=", limit = 2)
                val key = parts[0].trim()
                val value = if (parts.size == 2) parts[1].trim() else ""
                key to value
            }
            .filter { (key, _) -> key.isNotBlank() }
            .toMap()
    }
}
