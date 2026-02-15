package com.briefy.api.infrastructure.extraction

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@Component
class YouTubeExtractionProvider(
    private val whisperTranscriptionClient: OpenAiWhisperTranscriptionClient,
    @param:Value("\${extraction.youtube.yt-dlp-path:yt-dlp}")
    private val ytDlpPath: String,
    @param:Value("\${extraction.youtube.ffmpeg-path:ffmpeg}")
    private val ffmpegPath: String,
    @param:Value("\${extraction.youtube.max-duration-seconds:7200}")
    private val maxDurationSeconds: Int,
    @param:Value("\${extraction.youtube.command-timeout-seconds:180}")
    private val commandTimeoutSeconds: Long
) : ExtractionProvider {
    override val id: ExtractionProviderId = ExtractionProviderId.YOUTUBE
    private val logger = LoggerFactory.getLogger(YouTubeExtractionProvider::class.java)

    override fun extract(url: String): ExtractionResult {
        val ref = YouTubeUrlParser.parse(url)
        val tempDir = Files.createTempDirectory("briefy-youtube-${ref.videoId}-").toFile()
        try {
            val metadata = fetchVideoMetadata(ref.canonicalUrl)
            if (metadata.duration > maxDurationSeconds) {
                throw IllegalArgumentException("Video is too long for v1 (max ${maxDurationSeconds}s)")
            }

            val subtitle = extractSubtitleTranscript(ref.canonicalUrl, tempDir)
            val transcriptText: String
            val transcriptSource: String
            val transcriptLanguage: String?

            if (subtitle != null && subtitle.text.isNotBlank()) {
                transcriptText = subtitle.text
                transcriptSource = "captions"
                transcriptLanguage = subtitle.language
            } else {
                val audioFile = downloadAudio(ref.canonicalUrl, tempDir)
                transcriptText = transcribeAudio(audioFile, tempDir)
                transcriptSource = "whisper"
                transcriptLanguage = null
            }

            return ExtractionResult(
                text = transcriptText,
                title = metadata.title,
                author = metadata.uploader,
                publishedDate = metadata.uploadDate,
                aiFormatted = transcriptSource == "whisper",
                videoId = ref.videoId,
                videoEmbedUrl = ref.embedUrl,
                videoDurationSeconds = metadata.duration,
                transcriptSource = transcriptSource,
                transcriptLanguage = transcriptLanguage
            )
        } catch (e: ExtractionProviderException) {
            throw e
        } catch (e: Exception) {
            logger.error("[extractor:youtube] extraction_failed url={}", url, e)
            throw ExtractionProviderException(
                providerId = id,
                reason = ExtractionFailureReason.UNKNOWN,
                message = "YouTube extraction failed for URL: $url",
                cause = e
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun fetchVideoMetadata(url: String): YouTubeMetadata {
        val output = runCommand(
            listOf(
                ytDlpPath,
                "--no-playlist",
                "--quiet",
                "--no-warnings",
                "--print",
                "id",
                "--print",
                "title",
                "--print",
                "uploader",
                "--print",
                "duration",
                "--print",
                "upload_date",
                url
            )
        )
        val lines = output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.size < 5) {
            throw IllegalStateException("Unexpected yt-dlp metadata output: '$output'")
        }

        val title = lines.getOrNull(1)
        val uploader = lines.getOrNull(2)
        val duration = lines.getOrNull(3)?.toIntOrNull() ?: 0
        val uploadDate = parseUploadDate(lines.getOrNull(4).orEmpty())
        return YouTubeMetadata(
            title = title,
            uploader = uploader,
            duration = duration,
            uploadDate = uploadDate
        )
    }

    private fun extractSubtitleTranscript(url: String, tempDir: File): SubtitleTranscript? {
        runCatching {
            runCommand(
                listOf(
                    ytDlpPath,
                    "--no-playlist",
                    "--skip-download",
                    "--write-subs",
                    "--write-auto-subs",
                    "--sub-langs",
                    "en.*,en", //TODO: support other languages
                    "--sub-format",
                    "vtt",
                    "--output",
                    File(tempDir, "captions").absolutePath,
                    url
                )
            )
        }.onFailure {
            logger.info("[extractor:youtube] captions_not_available url={} reason={}", url, it.message)
        }

        val vttFile = tempDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("vtt", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.firstOrNull()
            ?: return null

        val language = extractLanguageFromSubtitleFile(vttFile)
        val cleaned = vttFile.readText()
            .lineSequence()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.startsWith("WEBVTT") ||
                    trimmed.matches(VTT_INDEX_LINE_PATTERN) ||
                    trimmed.contains("-->") ||
                    trimmed.isBlank()
            }
            .map { it.replace(VTT_TAG_PATTERN, "").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()

        if (cleaned.isBlank()) return null
        return SubtitleTranscript(cleaned, language)
    }

    private fun downloadAudio(url: String, tempDir: File): File {
        runCommand(
            listOf(
                ytDlpPath,
                "--no-playlist",
                "-f",
                "bestaudio",
                "-x",
                "--audio-format",
                "mp3",
                "--audio-quality",
                "64K",
                "--output",
                File(tempDir, "audio.%(ext)s").absolutePath,
                url
            )
        )

        return tempDir.listFiles()
            ?.firstOrNull { it.isFile && it.name.startsWith("audio.") && it.extension == "mp3" }
            ?: throw IllegalStateException("Audio file was not generated")
    }

    private fun transcribeAudio(audioFile: File, tempDir: File): String {
        val segmentDir = File(tempDir, "segments").apply { mkdirs() }
        runCommand(
            listOf(
                ffmpegPath,
                "-hide_banner",
                "-loglevel",
                "error",
                "-i",
                audioFile.absolutePath,
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
            ?.filter { it.isFile && it.extension == "mp3" }
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
            "[extractor:youtube] command_start timeoutSeconds={} command={}",
            commandTimeoutSeconds,
            command.joinToString(" ")
        )

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val outputCollector = ProcessOutputCollector(process.inputStream, MAX_COMMAND_OUTPUT_CHARS)
        val outputThread = Thread(outputCollector, "youtube-cmd-output-${command.firstOrNull().orEmpty()}").apply {
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
                "[extractor:youtube] command_timeout elapsedMs={} command={} outputPreview={}",
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
            "[extractor:youtube] command_finished elapsedMs={} exitCode={} outputChars={} truncated={} command={}",
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

    private fun parseUploadDate(raw: String): Instant? {
        if (raw.length != 8) return null
        return runCatching {
            LocalDate.parse(raw, java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
        }.getOrNull()
    }

    private fun extractLanguageFromSubtitleFile(file: File): String? {
        val parts = file.nameWithoutExtension.split(".")
        return if (parts.size >= 2) parts.lastOrNull() else null
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
        private val VTT_INDEX_LINE_PATTERN = Regex("^\\d+$")
        private val VTT_TAG_PATTERN = Regex("<[^>]+>")
    }
}

private data class YouTubeMetadata(
    val title: String?,
    val uploader: String?,
    val duration: Int,
    val uploadDate: Instant?
)

private data class SubtitleTranscript(
    val text: String,
    val language: String?
)
