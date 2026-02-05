package com.briefy.api.application.source

import com.briefy.api.domain.knowledgegraph.source.*
import com.briefy.api.infrastructure.extraction.ContentExtractor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SourceService(
    private val sourceRepository: SourceRepository,
    private val contentExtractor: ContentExtractor
) {
    private val logger = LoggerFactory.getLogger(SourceService::class.java)

    @Transactional
    fun submitSource(command: CreateSourceCommand): SourceResponse {
        val normalizedUrl = Url.normalize(command.url)

        // Check for duplicate
        sourceRepository.findByUrlNormalized(normalizedUrl)?.let {
            throw SourceAlreadyExistsException(normalizedUrl)
        }

        // Create source
        val source = Source.create(command.url)
        sourceRepository.save(source)

        // Perform synchronous extraction
        return extractContent(source)
    }

    @Transactional(readOnly = true)
    fun listSources(status: SourceStatus? = null): List<SourceResponse> {
        val sources = if (status != null) {
            sourceRepository.findByStatus(status)
        } else {
            sourceRepository.findAll()
        }
        return sources.map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getSource(id: UUID): SourceResponse {
        val source = sourceRepository.findById(id)
            .orElseThrow { SourceNotFoundException(id) }
        return source.toResponse()
    }

    @Transactional
    fun retryExtraction(id: UUID): SourceResponse {
        val source = sourceRepository.findById(id)
            .orElseThrow { SourceNotFoundException(id) }

        if (source.status != SourceStatus.FAILED) {
            throw InvalidSourceStateException("Can only retry extraction for failed sources. Current status: ${source.status}")
        }

        source.retry()
        sourceRepository.save(source)

        return extractContent(source)
    }

    private fun extractContent(source: Source): SourceResponse {
        source.startExtraction()
        sourceRepository.save(source)

        try {
            val result = contentExtractor.extract(source.url.normalized)

            val content = Content.from(result.text)
            val metadata = Metadata.from(
                title = result.title,
                author = result.author,
                publishedDate = result.publishedDate,
                platform = source.url.platform,
                wordCount = content.wordCount
            )

            source.completeExtraction(content, metadata)
            sourceRepository.save(source)

            logger.info("Successfully extracted content from ${source.url.normalized}")
            return source.toResponse()
        } catch (e: Exception) {
            logger.error("Failed to extract content from ${source.url.normalized}", e)
            source.failExtraction()
            sourceRepository.save(source)
            throw ExtractionFailedException(source.url.normalized, e)
        }
    }
}
