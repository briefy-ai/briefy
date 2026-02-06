package com.briefy.api.infrastructure.id

import com.github.f4b6a3.uuid.UuidCreator
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class UuidV7IdGenerator : IdGenerator {
    override fun newId(): UUID = UuidCreator.getTimeOrderedEpoch()
}
