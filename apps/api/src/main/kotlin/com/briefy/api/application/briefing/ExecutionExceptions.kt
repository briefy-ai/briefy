package com.briefy.api.application.briefing

import java.util.UUID

class ExecutionRunNotFoundException(entity: String, id: UUID) : RuntimeException("$entity not found: $id")

class ExecutionIllegalTransitionException(message: String) : RuntimeException(message)
