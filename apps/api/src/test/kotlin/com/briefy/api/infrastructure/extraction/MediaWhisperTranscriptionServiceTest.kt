package com.briefy.api.infrastructure.extraction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class MediaWhisperTranscriptionServiceTest {
    @TempDir
    lateinit var tempDir: Path

    private val whisperTranscriptionClient: OpenAiWhisperTranscriptionClient = mock()

    @Test
    fun `transcribe joins whisper output from generated chunks`() {
        val ffmpegScript = writeExecutable(
            "fake-ffmpeg.sh",
            """
            #!/bin/sh
            for last; do :; done
            dir=${'$'}(dirname "${'$'}last")
            mkdir -p "${'$'}dir"
            touch "${'$'}dir/chunk-000.mp3"
            touch "${'$'}dir/chunk-001.mp3"
            """.trimIndent()
        )
        whenever(whisperTranscriptionClient.transcribe(any())).thenReturn("first chunk", "second chunk")

        val service = MediaWhisperTranscriptionService(
            whisperTranscriptionClient = whisperTranscriptionClient,
            ffmpegPath = ffmpegScript.absolutePath,
            commandTimeoutSeconds = 5
        )
        val mediaFile = writeFile("input.mp4", "media")

        val result = service.transcribe(mediaFile, tempDir.resolve("work").toFile())

        assertEquals("first chunk\n\nsecond chunk", result)
    }

    @Test
    fun `transcribe fails when ffmpeg creates no chunks`() {
        val ffmpegScript = writeExecutable(
            "fake-ffmpeg-empty.sh",
            """
            #!/bin/sh
            exit 0
            """.trimIndent()
        )
        val service = MediaWhisperTranscriptionService(
            whisperTranscriptionClient = whisperTranscriptionClient,
            ffmpegPath = ffmpegScript.absolutePath,
            commandTimeoutSeconds = 5
        )
        val mediaFile = writeFile("input.mp4", "media")

        assertThrows(IllegalStateException::class.java) {
            service.transcribe(mediaFile, tempDir.resolve("work-empty").toFile())
        }
    }

    private fun writeExecutable(name: String, content: String): File {
        val file = writeFile(name, content)
        check(file.setExecutable(true)) { "Could not make script executable: ${file.absolutePath}" }
        return file
    }

    private fun writeFile(name: String, content: String): File {
        return tempDir.resolve(name).toFile().apply {
            writeText(content)
        }
    }
}
