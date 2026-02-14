package com.briefy.api.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "telegram")
class TelegramProperties {
    var integration = Integration()
    var bot = Bot()
    var webhook = Webhook()
    var links = Links()
    var ingestion = Ingestion()

    class Integration {
        var enabled: Boolean = false
    }

    class Bot {
        var token: String = ""
        var username: String = ""
    }

    class Webhook {
        var url: String = ""
        var secretToken: String = ""
    }

    class Links {
        var webBaseUrl: String = "http://localhost:3000"
    }

    class Ingestion {
        var worker = Worker()

        class Worker {
            var pollMs: Long = 5000
            var batchSize: Int = 5
            var maxAttempts: Int = 3
        }
    }
}
