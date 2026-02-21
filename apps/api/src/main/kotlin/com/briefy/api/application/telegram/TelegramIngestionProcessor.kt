package com.briefy.api.application.telegram

import com.briefy.api.application.source.CreateSourceCommand
import com.briefy.api.application.source.SourceService
import com.briefy.api.config.TelegramProperties
import com.briefy.api.domain.conversational.telegram.TelegramIngestionJob
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.Url
import com.briefy.api.infrastructure.telegram.TelegramBotGateway
import com.briefy.api.infrastructure.telegram.TelegramUrlExtractor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TelegramIngestionProcessor(
    private val sourceService: SourceService,
    private val sourceRepository: SourceRepository,
    private val telegramBotGateway: TelegramBotGateway,
    private val telegramProperties: TelegramProperties,
    private val telegramUrlExtractor: TelegramUrlExtractor
) {
    private val logger = LoggerFactory.getLogger(TelegramIngestionProcessor::class.java)

    fun process(job: TelegramIngestionJob) {
        val extraction = telegramUrlExtractor.extract(job.payloadText, MAX_URLS_PER_MESSAGE)
        if (extraction.urls.isEmpty()) {
            telegramBotGateway.sendMessage(job.telegramChatId, "No supported URLs found in your message.")
            return
        }
        logger.info(
            "[telegram] processing_ingestion_job jobId={} telegramUserId={} telegramChatId={} telegramMessageId={} linkedUserId={} urlCount={} urls={}",
            job.id,
            job.telegramUserId,
            job.telegramChatId,
            job.telegramMessageId,
            job.linkedUserId,
            extraction.urls.size,
            extraction.urls
        )

        val results = mutableListOf<UrlIngestionResult>()
        val seenNormalizedUrls = mutableSetOf<String>()
        extraction.urls.forEach { rawUrl ->
            val normalized = try {
                Url.normalize(rawUrl)
            } catch (_: Exception) {
                results.add(UrlIngestionResult(rawUrl, UrlIngestionStatus.INVALID, null, null, "invalid_url"))
                return@forEach
            }

            if (!seenNormalizedUrls.add(normalized)) {
                return@forEach
            }

            val existingSource = sourceRepository.findByUserIdAndUrlNormalized(job.linkedUserId, normalized)
            if (existingSource != null) {
                results.add(UrlIngestionResult(rawUrl, UrlIngestionStatus.DUPLICATE, existingSource.id.toString(), existingSource.metadata?.title, null))
                return@forEach
            }

            try {
                val created = sourceService.submitSourceForUser(job.linkedUserId, CreateSourceCommand(rawUrl))
                results.add(UrlIngestionResult(rawUrl, UrlIngestionStatus.CREATED, created.id.toString(), created.metadata?.title, null))
            } catch (e: Exception) {
                logger.warn(
                    "[telegram] Source ingestion failed for telegramUserId={} url={}",
                    job.telegramUserId,
                    rawUrl,
                    e
                )
                results.add(
                    UrlIngestionResult(
                        rawUrl = rawUrl,
                        status = UrlIngestionStatus.FAILED,
                        sourceId = null,
                        sourceTitle = null,
                        error = e.message?.take(120) ?: "unknown_error"
                    )
                )
            }
        }

        val summary = buildSummary(results, extraction.skippedCount)
        logger.info(
            "[telegram] ingestion_job_completed jobId={} telegramUserId={} created={} duplicate={} invalid={} failed={} skippedCount={}",
            job.id,
            job.telegramUserId,
            results.count { it.status == UrlIngestionStatus.CREATED },
            results.count { it.status == UrlIngestionStatus.DUPLICATE },
            results.count { it.status == UrlIngestionStatus.INVALID },
            results.count { it.status == UrlIngestionStatus.FAILED },
            extraction.skippedCount
        )
        telegramBotGateway.sendMessage(job.telegramChatId, summary)
    }

    private fun buildSummary(results: List<UrlIngestionResult>, skippedCount: Int): String {
        if (results.isEmpty()) {
            return "No supported URLs found in your message."
        }

        val webBaseUrl = telegramProperties.links.webBaseUrl
        val lines = mutableListOf<String>()
        lines.add("Briefy ingestion results:")
        results.forEach { result ->
            val line = when (result.status) {
                UrlIngestionStatus.CREATED -> "- ${result.rawUrl} -> created ${sourceLink(webBaseUrl, result.sourceId, result.sourceTitle, result.rawUrl)}"
                UrlIngestionStatus.DUPLICATE -> "- ${result.rawUrl} -> duplicate ${sourceLink(webBaseUrl, result.sourceId, result.sourceTitle, result.rawUrl)}"
                UrlIngestionStatus.INVALID -> "- ${result.rawUrl} -> invalid URL"
                UrlIngestionStatus.FAILED -> "- ${result.rawUrl} -> failed (${result.error ?: "unknown_error"})"
            }
            lines.add(line)
        }
        if (skippedCount > 0) {
            lines.add("Only the first $MAX_URLS_PER_MESSAGE URLs were processed. Skipped: $skippedCount.")
        }
        return lines.joinToString("\n")
    }

    private fun sourceLink(baseUrl: String, sourceId: String?, sourceTitle: String?, rawUrl: String): String {
        if (sourceId == null) return ""
        val normalizedBase = baseUrl.trimEnd('/')
        val url = "$normalizedBase/sources/$sourceId"
        val label = sourceTitle ?: (runCatching { java.net.URI(rawUrl).host }.getOrNull() ?: rawUrl)
        return "[$label]($url)"
    }

    companion object {
        private const val MAX_URLS_PER_MESSAGE = 10
    }
}

private data class UrlIngestionResult(
    val rawUrl: String,
    val status: UrlIngestionStatus,
    val sourceId: String?,
    val sourceTitle: String?,
    val error: String?
)

private enum class UrlIngestionStatus {
    CREATED,
    DUPLICATE,
    INVALID,
    FAILED
}
