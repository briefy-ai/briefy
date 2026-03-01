package com.briefy.api.application.briefing

import java.util.UUID

class BriefingNotFoundException(id: UUID) : RuntimeException("Briefing not found: $id")

class InvalidBriefingRequestException(message: String) : RuntimeException(message)

class InvalidBriefingStateException(message: String) : RuntimeException(message)

class BriefingSourceAccessException : RuntimeException("One or more sources are missing or not accessible")

class BriefingGenerationFailedException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
