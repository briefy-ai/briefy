package com.briefy.api.application.annotation

import java.util.UUID

class SourceAnnotationNotFoundException(id: UUID) : RuntimeException("Annotation not found: $id")
class SourceAnnotationOverlapException : RuntimeException("Selected text overlaps an existing annotation")
class InvalidSourceAnnotationStateException(message: String) : RuntimeException(message)
