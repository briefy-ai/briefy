package com.briefy.api.infrastructure.id

import java.util.UUID

interface IdGenerator {
    fun newId(): UUID
}
