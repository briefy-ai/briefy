package com.briefy.api.application.topic

import com.fasterxml.jackson.databind.exc.ValueInstantiationException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TopicSortTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `fromOrNull accepts only canonical values`() {
        assertEquals(TopicSort.MOST_FREQUENT, TopicSort.fromOrNull("most_frequent"))
        assertEquals(TopicSort.MOST_RECENT, TopicSort.fromOrNull("most_recent"))
        assertEquals(TopicSort.NEWLY_CREATED, TopicSort.fromOrNull("newly_created"))
        assertEquals(TopicSort.OLDEST, TopicSort.fromOrNull("oldest"))
        assertNull(TopicSort.fromOrNull("most-read"))
        assertNull(TopicSort.fromOrNull("recent"))
        assertNull(TopicSort.fromOrNull(null))
    }

    @Test
    fun `json creator rejects invalid sort values`() {
        assertEquals(TopicSort.MOST_FREQUENT, objectMapper.readValue("\"most_frequent\"", TopicSort::class.java))

        assertThrows(ValueInstantiationException::class.java) {
            objectMapper.readValue("\"popularity\"", TopicSort::class.java)
        }
    }
}
