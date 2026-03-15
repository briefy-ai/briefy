package com.briefy.api.application.briefing.tool

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SsrfGuardTest {
    private val publicResolver: (String) -> Array<java.net.InetAddress> = { host ->
        arrayOf(java.net.InetAddress.getByAddress(host, byteArrayOf(93, 184.toByte(), 216.toByte(), 34)))
    }

    @Test
    fun `allows valid https URL`() {
        val result = SsrfGuard.validate("https://example.com/page", publicResolver)
        assertTrue(result is ToolResult.Success)
        assertEquals("example.com", (result as ToolResult.Success).data.uri.host)
    }

    @Test
    fun `allows valid http URL`() {
        val result = SsrfGuard.validate("http://example.com/page", publicResolver)
        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `blocks file scheme`() {
        val result = SsrfGuard.validate("file:///etc/passwd")
        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.SSRF_BLOCKED, (result as ToolResult.Error).code)
    }

    @Test
    fun `blocks ftp scheme`() {
        val result = SsrfGuard.validate("ftp://evil.com/file")
        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.SSRF_BLOCKED, (result as ToolResult.Error).code)
    }

    @Test
    fun `blocks data scheme`() {
        val result = SsrfGuard.validate("data:text/plain,hello")
        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.SSRF_BLOCKED, (result as ToolResult.Error).code)
    }

    @Test
    fun `rejects malformed data URI`() {
        val result = SsrfGuard.validate("data:text/html,<script>alert(1)</script>")
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `blocks localhost`() {
        val result = SsrfGuard.validate("http://localhost:8080/admin")
        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.SSRF_BLOCKED, (result as ToolResult.Error).code)
    }

    @Test
    fun `blocks dotlocal hosts`() {
        val result = SsrfGuard.validate("http://myhost.local/admin")
        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.SSRF_BLOCKED, (result as ToolResult.Error).code)
    }

    @Test
    fun `blocks dotinternal hosts`() {
        val result = SsrfGuard.validate("http://service.internal/secret")
        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.SSRF_BLOCKED, (result as ToolResult.Error).code)
    }

    @Test
    fun `blocks loopback IP 127-0-0-1`() {
        val result = SsrfGuard.validate("http://127.0.0.1:9090/admin")
        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.SSRF_BLOCKED, (result as ToolResult.Error).code)
    }

    @Test
    fun `blocks private 10-x range`() {
        val result = SsrfGuard.validate("http://10.0.0.1/internal")
        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.SSRF_BLOCKED, (result as ToolResult.Error).code)
    }

    @Test
    fun `blocks private 192-168 range`() {
        val result = SsrfGuard.validate("http://192.168.1.1/router")
        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.SSRF_BLOCKED, (result as ToolResult.Error).code)
    }

    @Test
    fun `blocks private 172-16 range`() {
        val result = SsrfGuard.validate("http://172.16.0.1/internal")
        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.SSRF_BLOCKED, (result as ToolResult.Error).code)
    }

    @Test
    fun `blocks cloud metadata IP`() {
        val result = SsrfGuard.validate("http://169.254.169.254/latest/meta-data/")
        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.SSRF_BLOCKED, (result as ToolResult.Error).code)
    }

    @Test
    fun `returns resolved address for valid URL`() {
        val result = SsrfGuard.validate("https://example.com/page", publicResolver)
        assertTrue(result is ToolResult.Success)
        val validated = (result as ToolResult.Success).data
        assertNotNull(validated.resolvedAddress)
    }

    @Test
    fun `rejects malformed URL`() {
        val result = SsrfGuard.validate("not a url at all")
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `rejects URL with no host`() {
        val result = SsrfGuard.validate("http:///path")
        assertTrue(result is ToolResult.Error)
    }
}
