package com.briefy.api.api

import com.briefy.api.application.auth.*
import com.briefy.api.application.briefing.BriefingNotFoundException
import com.briefy.api.application.briefing.BriefingSourceAccessException
import com.briefy.api.application.briefing.InvalidBriefingRequestException
import com.briefy.api.application.briefing.InvalidBriefingStateException
import com.briefy.api.application.annotation.InvalidSourceAnnotationStateException
import com.briefy.api.application.annotation.SourceAnnotationNotFoundException
import com.briefy.api.application.annotation.SourceAnnotationOverlapException
import com.briefy.api.application.source.*
import com.briefy.api.application.topic.InvalidTopicLinkStateException
import com.briefy.api.application.topic.TopicAlreadyExistsException
import com.briefy.api.application.topic.TopicAlreadyLinkedToSourceException
import com.briefy.api.application.topic.TopicLinkNotFoundException
import com.briefy.api.application.topic.TopicNotFoundException
import com.briefy.api.infrastructure.extraction.ExtractionProviderException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(BriefingNotFoundException::class)
    fun handleBriefingNotFound(ex: BriefingNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ErrorResponse(
                    status = HttpStatus.NOT_FOUND.value(),
                    error = "Not Found",
                    message = ex.message ?: "Briefing not found"
                )
            )
    }

    @ExceptionHandler(BriefingSourceAccessException::class)
    fun handleBriefingSourceAccess(ex: BriefingSourceAccessException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(
                ErrorResponse(
                    status = HttpStatus.FORBIDDEN.value(),
                    error = "Forbidden",
                    message = ex.message ?: "One or more sources are missing or not accessible"
                )
            )
    }

    @ExceptionHandler(InvalidBriefingRequestException::class)
    fun handleInvalidBriefingRequest(ex: InvalidBriefingRequestException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    status = HttpStatus.BAD_REQUEST.value(),
                    error = "Bad Request",
                    message = ex.message ?: "Invalid briefing request"
                )
            )
    }

    @ExceptionHandler(InvalidBriefingStateException::class)
    fun handleInvalidBriefingState(ex: InvalidBriefingStateException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    status = HttpStatus.BAD_REQUEST.value(),
                    error = "Bad Request",
                    message = ex.message ?: "Invalid briefing state"
                )
            )
    }

    @ExceptionHandler(SourceNotFoundException::class)
    fun handleSourceNotFound(ex: SourceNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                status = HttpStatus.NOT_FOUND.value(),
                error = "Not Found",
                message = ex.message ?: "Source not found"
            ))
    }

    @ExceptionHandler(SourceAnnotationNotFoundException::class)
    fun handleSourceAnnotationNotFound(ex: SourceAnnotationNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                status = HttpStatus.NOT_FOUND.value(),
                error = "Not Found",
                message = ex.message ?: "Annotation not found"
            ))
    }

    @ExceptionHandler(SourceAnnotationOverlapException::class)
    fun handleSourceAnnotationOverlap(ex: SourceAnnotationOverlapException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(
                status = HttpStatus.CONFLICT.value(),
                error = "Conflict",
                message = ex.message ?: "Selected text overlaps an existing annotation"
            ))
    }

    @ExceptionHandler(InvalidSourceAnnotationStateException::class)
    fun handleInvalidSourceAnnotationState(ex: InvalidSourceAnnotationStateException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Bad Request",
                message = ex.message ?: "Invalid annotation state"
            ))
    }

    @ExceptionHandler(BatchSourceNotFoundException::class)
    fun handleBatchSourceNotFound(ex: BatchSourceNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                status = HttpStatus.NOT_FOUND.value(),
                error = "Not Found",
                message = ex.message ?: "One or more sources not found"
            ))
    }

    @ExceptionHandler(TopicNotFoundException::class)
    fun handleTopicNotFound(ex: TopicNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ErrorResponse(
                    status = HttpStatus.NOT_FOUND.value(),
                    error = "Not Found",
                    message = ex.message ?: "Topic not found"
                )
            )
    }

    @ExceptionHandler(TopicLinkNotFoundException::class)
    fun handleTopicLinkNotFound(ex: TopicLinkNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ErrorResponse(
                    status = HttpStatus.NOT_FOUND.value(),
                    error = "Not Found",
                    message = ex.message ?: "Topic suggestion not found"
                )
            )
    }

    @ExceptionHandler(TopicAlreadyExistsException::class)
    fun handleTopicAlreadyExists(ex: TopicAlreadyExistsException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(
                ErrorResponse(
                    status = HttpStatus.CONFLICT.value(),
                    error = "Conflict",
                    message = ex.message ?: "Topic already exists"
                )
            )
    }

    @ExceptionHandler(TopicAlreadyLinkedToSourceException::class)
    fun handleTopicAlreadyLinkedToSource(ex: TopicAlreadyLinkedToSourceException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(
                ErrorResponse(
                    status = HttpStatus.CONFLICT.value(),
                    error = "Conflict",
                    message = ex.message ?: "Topic already linked to source"
                )
            )
    }

    @ExceptionHandler(SourceAlreadyExistsException::class)
    fun handleSourceAlreadyExists(ex: SourceAlreadyExistsException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(
                status = HttpStatus.CONFLICT.value(),
                error = "Conflict",
                message = ex.message ?: "Source already exists"
            ))
    }

    @ExceptionHandler(EmailAlreadyExistsException::class)
    fun handleEmailAlreadyExists(ex: EmailAlreadyExistsException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(
                status = HttpStatus.CONFLICT.value(),
                error = "Conflict",
                message = ex.message ?: "Email already in use"
            ))
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(
                status = HttpStatus.UNAUTHORIZED.value(),
                error = "Unauthorized",
                message = ex.message ?: "Invalid credentials"
            ))
    }

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(
                status = HttpStatus.UNAUTHORIZED.value(),
                error = "Unauthorized",
                message = ex.message ?: "Unauthorized"
            ))
    }

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(ex: UserNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                status = HttpStatus.NOT_FOUND.value(),
                error = "Not Found",
                message = ex.message ?: "User not found"
            ))
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(
                status = HttpStatus.FORBIDDEN.value(),
                error = "Forbidden",
                message = ex.message ?: "Access denied"
            ))
    }

    @ExceptionHandler(InvalidSourceStateException::class)
    fun handleInvalidSourceState(ex: InvalidSourceStateException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Bad Request",
                message = ex.message ?: "Invalid source state"
            ))
    }

    @ExceptionHandler(InvalidTopicLinkStateException::class)
    fun handleInvalidTopicLinkState(ex: InvalidTopicLinkStateException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    status = HttpStatus.BAD_REQUEST.value(),
                    error = "Bad Request",
                    message = ex.message ?: "Invalid topic link state"
                )
            )
    }

    @ExceptionHandler(ExtractionFailedException::class)
    fun handleExtractionFailed(ex: ExtractionFailedException): ResponseEntity<ErrorResponse> {
        logger.error("[exception] Extraction failed", ex)
        val providerMessage = (ex.cause as? ExtractionProviderException)?.message
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse(
                status = HttpStatus.UNPROCESSABLE_ENTITY.value(),
                error = "Unprocessable Entity",
                message = providerMessage ?: ex.message ?: "Failed to extract content from URL"
            ))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Bad Request",
                message = errors
            ))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Bad Request",
                message = ex.message ?: "Invalid argument"
            ))
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(
                status = HttpStatus.FORBIDDEN.value(),
                error = "Forbidden",
                message = ex.message ?: "Invalid state"
            ))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("[exception] Unexpected error", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                error = "Internal Server Error",
                message = "An unexpected error occurred"
            ))
    }
}

data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val timestamp: Instant = Instant.now()
)
    @ExceptionHandler(BriefingNotFoundException::class)
    fun handleBriefingNotFound(ex: BriefingNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ErrorResponse(
                    status = HttpStatus.NOT_FOUND.value(),
                    error = "Not Found",
                    message = ex.message ?: "Briefing not found"
                )
            )
    }

    @ExceptionHandler(BriefingSourceAccessException::class)
    fun handleBriefingSourceAccess(ex: BriefingSourceAccessException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(
                ErrorResponse(
                    status = HttpStatus.FORBIDDEN.value(),
                    error = "Forbidden",
                    message = ex.message ?: "One or more sources are missing or not accessible"
                )
            )
    }

    @ExceptionHandler(InvalidBriefingRequestException::class)
    fun handleInvalidBriefingRequest(ex: InvalidBriefingRequestException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    status = HttpStatus.BAD_REQUEST.value(),
                    error = "Bad Request",
                    message = ex.message ?: "Invalid briefing request"
                )
            )
    }

    @ExceptionHandler(InvalidBriefingStateException::class)
    fun handleInvalidBriefingState(ex: InvalidBriefingStateException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    status = HttpStatus.BAD_REQUEST.value(),
                    error = "Bad Request",
                    message = ex.message ?: "Invalid briefing state"
                )
            )
    }
