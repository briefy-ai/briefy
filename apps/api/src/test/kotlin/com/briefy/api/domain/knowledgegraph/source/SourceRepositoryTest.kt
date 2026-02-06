package com.briefy.api.domain.knowledgegraph.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class SourceRepositoryTest {

    @Autowired
    lateinit var sourceRepository: SourceRepository

    @Test
    fun `repository queries are scoped by user id`() {
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()

        val sourceA = sourceRepository.save(Source.create(UUID.randomUUID(), "https://example.com/a", userA))
        sourceRepository.save(Source.create(UUID.randomUUID(), "https://example.com/b", userB))

        val userASources = sourceRepository.findByUserId(userA)
        val userBSources = sourceRepository.findByUserId(userB)

        assertEquals(1, userASources.size)
        assertEquals(sourceA.id, userASources.first().id)
        assertEquals(1, userBSources.size)

        val byIdAndUser = sourceRepository.findByIdAndUserId(sourceA.id, userA)
        val wrongUser = sourceRepository.findByIdAndUserId(sourceA.id, userB)

        assertEquals(sourceA.id, byIdAndUser?.id)
        assertNull(wrongUser)
    }
}
