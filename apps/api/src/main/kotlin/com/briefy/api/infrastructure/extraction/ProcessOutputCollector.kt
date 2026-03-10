package com.briefy.api.infrastructure.extraction

import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

internal class ProcessOutputCollector(
    private val inputStream: InputStream,
    private val maxChars: Int
) : Runnable {
    private val buffer = StringBuilder()

    @Volatile
    private var truncated = false

    override fun run() {
        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val chunk = CharArray(4096)
            while (true) {
                val read = reader.read(chunk)
                if (read < 0) break
                append(chunk, read)
            }
        }
    }

    @Synchronized
    fun snapshot(): String = buffer.toString()

    fun wasTruncated(): Boolean = truncated

    @Synchronized
    private fun append(chunk: CharArray, read: Int) {
        if (buffer.length >= maxChars) {
            truncated = true
            return
        }
        val remaining = maxChars - buffer.length
        val toAppend = minOf(remaining, read)
        if (toAppend > 0) {
            buffer.append(chunk, 0, toAppend)
        }
        if (toAppend < read) {
            truncated = true
        }
    }
}
