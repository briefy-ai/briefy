package com.briefy.api.infrastructure.extraction

import java.net.URI

data class YouTubeVideoRef(
    val videoId: String,
    val canonicalUrl: String,
    val embedUrl: String
)

object YouTubeUrlParser {
    private val validVideoId = Regex("^[A-Za-z0-9_-]{11}$")

    fun parse(url: String): YouTubeVideoRef {
        val uri = URI.create(url)
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path.orEmpty().trimEnd('/')
        val query = parseQuery(uri.rawQuery)

        val videoId = when {
            host == "youtu.be" -> path.removePrefix("/")
            host.endsWith("youtube.com") && path == "/watch" -> query["v"].orEmpty()
            host.endsWith("youtube.com") && path.startsWith("/shorts/") -> path.removePrefix("/shorts/")
            else -> throw IllegalArgumentException("Unsupported YouTube URL format")
        }

        if (!validVideoId.matches(videoId)) {
            throw IllegalArgumentException("Invalid YouTube video ID")
        }
        if (query.containsKey("list")) {
            throw IllegalArgumentException("Playlists are not supported in v1")
        }

        return YouTubeVideoRef(
            videoId = videoId,
            canonicalUrl = "https://youtube.com/watch?v=$videoId",
            embedUrl = "https://www.youtube.com/embed/$videoId"
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&")
            .asSequence()
            .filter { it.isNotBlank() }
            .map {
                val parts = it.split("=", limit = 2)
                val key = parts[0]
                val value = if (parts.size == 2) parts[1] else ""
                key to value
            }
            .toMap()
    }
}
