package com.briefy.api.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "ai.observability")
class AiObservabilityProperties {
    var enabled: Boolean = false
    var langfuse = Langfuse()

    class Langfuse {
        var baseUrl: String = "https://cloud.langfuse.com"
        var publicKey: String = ""
        var secretKey: String = ""
    }
}
