package com.briefy.api.infrastructure.imagegen

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
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
                .uri("/api/v1/images/generations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $apiKey")
                .body(
                    mapOf(
                        "model" to model,
                        "prompt" to prompt,
                        "size" to size,
                        "n" to 1
                    )
                )
                .retrieve()
                .body(String::class.java)
                ?.trim()
                .orEmpty()

            if (responseBody.isBlank()) {
                throw ImageGenerationException("OpenRouter image generation returned an empty response")
            }

            val response = objectMapper.readTree(responseBody)
            response.path("error").takeIf { !it.isMissingNode && !it.isNull }?.let { error ->
                val message = error.path("message").asText().ifBlank { error.toString() }
                throw ImageGenerationException("OpenRouter image generation failed: $message")
            }

            val imageNode = response.path("data")
                .takeIf { it.isArray && it.size() > 0 }
                ?.get(0)
                ?: throw ImageGenerationException("OpenRouter image generation returned no images")

            val imageBytes = when {
                imageNode.hasNonNull("b64_json") -> decodeBase64Image(imageNode.path("b64_json").asText())
                imageNode.hasNonNull("url") -> downloadImage(imageNode.path("url").asText())
                else -> throw ImageGenerationException("OpenRouter response did not include image bytes or a URL")
            }

            return normalizeToPng(imageBytes)
        } catch (ex: ImageGenerationException) {
            throw ex
        } catch (ex: RestClientException) {
            throw ImageGenerationException("OpenRouter image generation request failed", ex)
        } catch (ex: Exception) {
            throw ImageGenerationException("Failed to process OpenRouter image generation response", ex)
        }
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
