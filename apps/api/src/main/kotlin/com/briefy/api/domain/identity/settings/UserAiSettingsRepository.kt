package com.briefy.api.domain.identity.settings

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserAiSettingsRepository : JpaRepository<UserAiSettings, UUID> {
    fun findByUserId(userId: UUID): UserAiSettings?
}
