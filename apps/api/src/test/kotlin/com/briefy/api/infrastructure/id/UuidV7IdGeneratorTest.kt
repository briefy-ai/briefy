package com.briefy.api.infrastructure.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UuidV7IdGeneratorTest {

    private val idGenerator = UuidV7IdGenerator()

    @Test
    fun `newId generates uuid version 7`() {
        val uuid = idGenerator.newId()
        assertEquals(7, uuid.version())
        assertEquals(2, uuid.variant())
    }
}
