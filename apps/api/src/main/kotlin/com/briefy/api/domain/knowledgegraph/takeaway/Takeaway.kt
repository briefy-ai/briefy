package com.briefy.api.domain.knowledgegraph.takeaway

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "takeaways")
class Takeaway(
    @Id
    val id: UUID = UUID.randomUUID(),
)
