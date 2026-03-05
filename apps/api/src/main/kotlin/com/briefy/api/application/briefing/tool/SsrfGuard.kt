package com.briefy.api.application.briefing.tool

import java.net.InetAddress
import java.net.URI

data class ValidatedUrl(val uri: URI, val resolvedAddress: InetAddress)

object SsrfGuard {

    private val BLOCKED_SCHEMES = setOf("file", "ftp", "gopher", "data", "javascript")

    fun validate(rawUrl: String): ToolResult<ValidatedUrl> {
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

        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) {
            return ToolResult.Error(ToolErrorCode.SSRF_BLOCKED, "Blocked private/reserved host: $host")
        }

        val resolvedAddress = resolveAndValidate(host)
            ?: return ToolResult.Error(ToolErrorCode.SSRF_BLOCKED, "Blocked private/reserved host: $host")

        return ToolResult.Success(ValidatedUrl(uri, resolvedAddress))
    }

    private fun resolveAndValidate(host: String): InetAddress? {
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (_: Exception) {
            // Fail closed: DNS failure = blocked
            return null
        }

        if (addresses.isEmpty()) return null

        val blocked = addresses.any { addr ->
            addr.isLoopbackAddress ||
                addr.isSiteLocalAddress ||
                addr.isLinkLocalAddress ||
                addr.isAnyLocalAddress ||
                isMetadataAddress(addr)
        }
        if (blocked) return null

        return addresses.first()
    }

    private fun isMetadataAddress(addr: InetAddress): Boolean {
        val bytes = addr.address
        if (bytes.size == 4 &&
            bytes[0] == 169.toByte() && bytes[1] == 254.toByte() &&
            bytes[2] == 169.toByte() && bytes[3] == 254.toByte()
        ) {
            return true
        }
        return false
    }
}
