package com.briefy.api.application.source

import java.util.UUID

class SourceNotFoundException(id: UUID) : RuntimeException("Source not found: $id")

class SourceAlreadyExistsException(normalizedUrl: String) : RuntimeException("Source already exists for URL: $normalizedUrl")

class InvalidSourceStateException(message: String) : RuntimeException(message)

class ExtractionFailedException(url: String, cause: Throwable) : RuntimeException("Failed to extract content from URL: $url", cause)
