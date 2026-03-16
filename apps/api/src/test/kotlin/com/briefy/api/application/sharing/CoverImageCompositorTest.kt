package com.briefy.api.application.sharing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class CoverImageCompositorTest {
    private val compositor = CoverImageCompositor()

    @Test
    fun `composite returns valid png output`() {
        val result = compositor.composite(samplePngBytes(Color(0x22, 0x55, 0x88)), "A generated cover image")

        val image = requireNotNull(ImageIO.read(result.inputStream()))
        assertNotNull(image)
        assertEquals(1200, image.width)
        assertEquals(630, image.height)
    }

    @Test
    fun `composite handles blank title`() {
        val result = compositor.composite(samplePngBytes(Color(0x44, 0x66, 0x33)), "   ")

        val image = requireNotNull(ImageIO.read(result.inputStream()))
        assertNotNull(image)
        assertEquals(1200, image.width)
        assertEquals(630, image.height)
    }

    @Test
    fun `composite handles long title`() {
        val result = compositor.composite(
            samplePngBytes(Color(0x88, 0x44, 0x33)),
            "A very long generated title that should wrap across multiple lines without failing or producing an invalid output image for social sharing previews"
        )

        val image = requireNotNull(ImageIO.read(result.inputStream()))
        assertNotNull(image)
        assertEquals(1200, image.width)
        assertEquals(630, image.height)
    }

    private fun samplePngBytes(color: Color): ByteArray {
        val image = BufferedImage(1400, 900, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = color
            graphics.fillRect(0, 0, image.width, image.height)
        } finally {
            graphics.dispose()
        }

        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }
}
