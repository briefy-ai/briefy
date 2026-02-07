package com.briefy.api.application.source

import com.briefy.api.domain.knowledgegraph.source.SourceType
import org.springframework.stereotype.Component
import java.net.URI

@Component
class SourceTypeClassifier {
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
        val host = runCatching { URI.create(normalizedUrl).host?.lowercase().orEmpty() }.getOrDefault("")
        if (host.isBlank()) return SourceType.BLOG

        if (matches(host, researchHosts)) return SourceType.RESEARCH
        if (matches(host, newsHosts)) return SourceType.NEWS
        return SourceType.BLOG
    }

    private fun matches(host: String, patterns: Set<String>): Boolean {
        return patterns.any { host == it || host.endsWith(".$it") }
    }
}
