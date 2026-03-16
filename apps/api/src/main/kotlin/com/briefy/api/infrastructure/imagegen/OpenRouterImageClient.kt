package com.briefy.api.infrastructure.imagegen

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

class ImageGenerationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@Component
class OpenRouterImageClient(
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper
) {
    private val restClient = restClientBuilder.baseUrl("https://openrouter.ai").build()
    private val downloadClient = restClientBuilder.build()

    fun generate(
        apiKey: String,
        model: String,
        prompt: String,
        size: String = "1792x1024"
    ): ByteArray {
        require(apiKey.isNotBlank()) { "OpenRouter API key must not be blank" }
        require(model.isNotBlank()) { "OpenRouter image model must not be blank" }
        require(prompt.isNotBlank()) { "Image prompt must not be blank" }

        try {
            val responseBody = restClient.post()
                .uri("/api/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $apiKey")
                .body(buildRequestBody(model, prompt, size))
                .retrieve()
                .body(String::class.java)
                ?.trim()
                .orEmpty()

            if (responseBody.isBlank()) {
                throw ImageGenerationException("OpenRouter image generation returned an empty response")
            }

            return extractImageBytes(responseBody)
        } catch (ex: ImageGenerationException) {
            throw ex
        } catch (ex: HttpStatusCodeException) {
            throw ImageGenerationException(
                "OpenRouter image generation request failed with status ${ex.statusCode.value()}: ${responsePreview(ex.responseBodyAsString)}",
                ex
                )
        } catch (ex: RestClientException) {
            throw ImageGenerationException("OpenRouter image generation request failed", ex)
        } catch (ex: Exception) {
            throw ImageGenerationException("Failed to process OpenRouter image generation response", ex)
        }
    }

    private fun buildRequestBody(model: String, prompt: String, size: String): Map<String, Any> {
        return mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to prompt
                )
            ),
            "modalities" to outputModalities(model),
            "image_config" to mapOf(
                "aspect_ratio" to aspectRatio(size)
            ),
            "stream" to false
        )
    }

    private fun extractImageBytes(responseBody: String): ByteArray {
        val response = objectMapper.readTree(responseBody)
        response.path("error").takeIf { !it.isMissingNode && !it.isNull }?.let { error ->
            val message = error.path("message").asText().ifBlank { error.toString() }
            throw ImageGenerationException("OpenRouter image generation failed: $message")
        }

        val message = response.path("choices")
            .takeIf { it.isArray && it.size() > 0 }
            ?.get(0)
            ?.path("message")
            ?: throw ImageGenerationException("OpenRouter image generation returned no choices")

        val imageNode = message.path("images")
            .takeIf { it.isArray && it.size() > 0 }
            ?.get(0)
            ?: throw ImageGenerationException("OpenRouter image generation returned no images")

        val imageReference = imageNode.path("image_url").takeIf { !it.isMissingNode && !it.isNull }
            ?: imageNode.path("imageUrl").takeIf { !it.isMissingNode && !it.isNull }
            ?: throw ImageGenerationException("OpenRouter image generation response did not include image_url")

        val url = imageReference.path("url").asText().ifBlank {
            throw ImageGenerationException("OpenRouter image generation returned an empty image URL")
        }

        val imageBytes = when {
            url.startsWith("data:", ignoreCase = true) -> decodeDataUrl(url)
            else -> downloadImage(url)
        }

        return normalizeToPng(imageBytes)
    }

    private fun outputModalities(model: String): List<String> {
        return if (model.startsWith("google/", ignoreCase = true)) {
            listOf("image", "text")
        } else {
            listOf("image")
        }
    }

    private fun aspectRatio(size: String): String {
        return when (size.trim()) {
            "1792x1024" -> "16:9"
            "1024x1024" -> "1:1"
            "1024x1536" -> "2:3"
            "1536x1024" -> "3:2"
            else -> "16:9"
        }
    }

    private fun decodeDataUrl(dataUrl: String): ByteArray {
        val base64Index = dataUrl.indexOf("base64,")
        if (base64Index == -1) {
            throw ImageGenerationException("OpenRouter returned an unsupported image data URL")
        }
        return decodeBase64Image(dataUrl.substring(base64Index + "base64,".length))
    }

    private fun responsePreview(responseBody: String?): String {
        val trimmed = responseBody?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        return trimmed.take(240).ifBlank { "empty response body" }
    }

    private fun decodeBase64Image(encoded: String): ByteArray {
        return try {
            Base64.getDecoder().decode(encoded)
        } catch (ex: IllegalArgumentException) {
            throw ImageGenerationException("OpenRouter returned invalid base64 image data", ex)
        }
    }

    private fun downloadImage(url: String): ByteArray {
        if (url.isBlank()) {
            throw ImageGenerationException("OpenRouter returned an empty image URL")
        }

        return downloadClient.get()
            .uri(url)
            .retrieve()
            .body(ByteArray::class.java)
            ?: throw ImageGenerationException("Image download returned an empty body")
    }

    private fun normalizeToPng(imageBytes: ByteArray): ByteArray {
        val image = ImageIO.read(imageBytes.inputStream())
            ?: throw ImageGenerationException("Generated image could not be decoded")

        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }
}
