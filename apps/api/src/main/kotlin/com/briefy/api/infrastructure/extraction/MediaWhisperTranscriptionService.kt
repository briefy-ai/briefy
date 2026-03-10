package com.briefy.api.infrastructure.extraction

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@Component
class MediaWhisperTranscriptionService(
    private val whisperTranscriptionClient: OpenAiWhisperTranscriptionClient,
    @param:Value("\${extraction.youtube.ffmpeg-path:ffmpeg}")
    private val ffmpegPath: String,
    @param:Value("\${extraction.youtube.command-timeout-seconds:180}")
    private val commandTimeoutSeconds: Long
) {
    private val logger = LoggerFactory.getLogger(MediaWhisperTranscriptionService::class.java)

    fun transcribe(mediaFile: File, tempDir: File): String {
        require(mediaFile.exists()) { "Media file does not exist: ${mediaFile.absolutePath}" }
        require(tempDir.exists() || tempDir.mkdirs()) { "Could not create temp dir: ${tempDir.absolutePath}" }

        val segmentDir = File(tempDir, "segments").apply { mkdirs() }
        runCommand(
            listOf(
                ffmpegPath,
                "-hide_banner",
                "-loglevel",
                "error",
                "-i",
                mediaFile.absolutePath,
                "-vn",
                "-f",
                "segment",
                "-segment_time",
                "900",
                "-c:a",
                "libmp3lame",
                "-b:a",
                "64k",
                File(segmentDir, "chunk-%03d.mp3").absolutePath
            )
        )

        val chunks = segmentDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("mp3", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()

        if (chunks.isEmpty()) {
            throw IllegalStateException("No audio chunks were created for transcription")
        }

        return chunks.joinToString("\n\n") { whisperTranscriptionClient.transcribe(it) }.trim()
    }

    private fun runCommand(command: List<String>): String {
        val startedAtNanos = System.nanoTime()
        logger.info(
            "[extractor:media-transcription] command_start timeoutSeconds={} command={}",
            commandTimeoutSeconds,
            command.joinToString(" ")
        )

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val outputCollector = ProcessOutputCollector(process.inputStream, MAX_COMMAND_OUTPUT_CHARS)
        val outputThread = Thread(outputCollector, "media-transcription-output-${command.firstOrNull().orEmpty()}").apply {
            isDaemon = true
            start()
        }
        val completed = process.waitFor(commandTimeoutSeconds, TimeUnit.SECONDS)

        if (!completed) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
            outputThread.join(5_000)
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
            val outputPreview = outputCollector.snapshot().take(COMMAND_OUTPUT_PREVIEW_CHARS)
            logger.error(
                "[extractor:media-transcription] command_timeout elapsedMs={} command={} outputPreview={}",
                elapsedMs,
                command.joinToString(" "),
                outputPreview
            )
            throw IllegalStateException("Command timed out after ${elapsedMs}ms: ${command.joinToString(" ")}")
        }

        outputThread.join(5_000)
        val output = outputCollector.snapshot()
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
        logger.info(
            "[extractor:media-transcription] command_finished elapsedMs={} exitCode={} outputChars={} truncated={} command={}",
            elapsedMs,
            process.exitValue(),
            output.length,
            outputCollector.wasTruncated(),
            command.joinToString(" ")
        )

        if (process.exitValue() != 0) {
            val outputPreview = output.take(COMMAND_OUTPUT_PREVIEW_CHARS)
            throw IllegalStateException(
                "Command failed (exit=${process.exitValue()}): ${command.joinToString(" ")}; output=$outputPreview"
            )
        }
        return output
    }

    private class ProcessOutputCollector(
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

    companion object {
        private const val MAX_COMMAND_OUTPUT_CHARS = 500_000
        private const val COMMAND_OUTPUT_PREVIEW_CHARS = 4_000
    }
}
