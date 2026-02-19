package com.briefy.api.domain.enrichment

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AgentPersonaRepository : JpaRepository<AgentPersona, UUID> {
    @Query(
        """
        select p
        from AgentPersona p
        where p.useCase = :useCase
          and (p.isSystem = true or p.userId = :userId)
        order by p.isSystem desc, p.createdAt asc
        """
    )
    fun findForUseCase(
        @Param("userId") userId: UUID,
        @Param("useCase") useCase: AgentPersonaUseCase
    ): List<AgentPersona>
}
