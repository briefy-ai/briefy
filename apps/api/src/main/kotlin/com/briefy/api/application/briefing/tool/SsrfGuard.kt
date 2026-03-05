package com.briefy.api.application.briefing.tool

import java.net.InetAddress
import java.net.URI

object SsrfGuard {

    private val BLOCKED_SCHEMES = setOf("file", "ftp", "gopher", "data", "javascript")

    fun validate(rawUrl: String): ToolResult<URI> {
        val uri = try {
            URI.create(rawUrl)
        } catch (_: Exception) {
            return ToolResult.Error(ToolErrorCode.INVALID_URL, "Malformed URL: $rawUrl")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme == null || scheme in BLOCKED_SCHEMES) {
            return ToolResult.Error(ToolErrorCode.SSRF_BLOCKED, "Blocked scheme: $scheme")
        }
        if (scheme != "http" && scheme != "https") {
            return ToolResult.Error(ToolErrorCode.SSRF_BLOCKED, "Only http/https allowed, got: $scheme")
        }

        val host = uri.host
        if (host.isNullOrBlank()) {
            return ToolResult.Error(ToolErrorCode.INVALID_URL, "Missing host in URL")
        }

        if (isPrivateOrReserved(host)) {
            return ToolResult.Error(ToolErrorCode.SSRF_BLOCKED, "Blocked private/reserved host: $host")
        }

        return ToolResult.Success(uri)
    }

    internal fun isPrivateOrReserved(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) {
            return true
        }

        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (_: Exception) {
            return false
        }

        return addresses.any { addr ->
            addr.isLoopbackAddress ||
                addr.isSiteLocalAddress ||
                addr.isLinkLocalAddress ||
                addr.isAnyLocalAddress ||
                isMetadataAddress(addr)
        }
    }

    private fun isMetadataAddress(addr: InetAddress): Boolean {
        val bytes = addr.address
        // 169.254.169.254 (cloud metadata)
        if (bytes.size == 4 &&
            bytes[0] == 169.toByte() && bytes[1] == 254.toByte() &&
            bytes[2] == 169.toByte() && bytes[3] == 254.toByte()
        ) {
            return true
        }
        return false
    }
}
