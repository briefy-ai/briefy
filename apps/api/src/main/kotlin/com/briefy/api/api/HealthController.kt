package com.briefy.api.api

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class HealthController {
    private val logger = LoggerFactory.getLogger(HealthController::class.java)

    @GetMapping("/health")
    fun health(): ResponseEntity<String> {
        logger.info("[controller] Health check request completed")
        return ResponseEntity.ok("OK")
    }
}
