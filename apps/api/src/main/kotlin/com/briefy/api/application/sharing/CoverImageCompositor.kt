package com.briefy.api.application.sharing

import org.springframework.stereotype.Component
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Component
class CoverImageCompositor {
    fun composite(baseImageBytes: ByteArray, title: String): ByteArray {
        val baseImage = ImageIO.read(baseImageBytes.inputStream())
            ?: throw IllegalArgumentException("Base image could not be decoded")
        val canvas = BufferedImage(OUTPUT_WIDTH, OUTPUT_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val graphics = canvas.createGraphics()

        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.drawImage(scaleAndCrop(baseImage), 0, 0, null)

            graphics.composite = AlphaComposite.SrcOver
            graphics.color = Color(0x3B, 0x2F, 0x1F, 102)
            graphics.fillRect(0, 0, OUTPUT_WIDTH, OUTPUT_HEIGHT)

            graphics.color = Color.WHITE
            graphics.font = Font("SansSerif", Font.BOLD, 58)
            val titleLines = TextLayoutHelper.wrapText(
                text = title,
                fontMetrics = graphics.fontMetrics,
                maxWidth = OUTPUT_WIDTH - (HORIZONTAL_PADDING * 2),
                maxLines = 3
            )

            val titleLineHeight = graphics.fontMetrics.height + 8
            val titleBlockHeight = titleLines.size * titleLineHeight

            graphics.font = Font("SansSerif", Font.PLAIN, 28)
            val subtitleFontMetrics = graphics.fontMetrics
            val subtitle = "Briefy AI"
            val subtitleGap = 28
            val totalBlockHeight = titleBlockHeight + subtitleGap + subtitleFontMetrics.height
            var currentY = ((OUTPUT_HEIGHT - totalBlockHeight) / 2) + titleAscent()

            graphics.font = Font("SansSerif", Font.BOLD, 58)
            for (line in titleLines) {
                val lineWidth = graphics.fontMetrics.stringWidth(line)
                graphics.drawString(line, (OUTPUT_WIDTH - lineWidth) / 2, currentY)
                currentY += titleLineHeight
            }

            currentY += subtitleGap - 8
            graphics.font = Font("SansSerif", Font.PLAIN, 28)
            graphics.color = Color(0xFF, 0xFF, 0xFF, 180)
            val subtitleX = (OUTPUT_WIDTH - graphics.fontMetrics.stringWidth(subtitle)) / 2
            graphics.drawString(subtitle, subtitleX, currentY)
        } finally {
            graphics.dispose()
        }

        return ByteArrayOutputStream().use { output ->
            ImageIO.write(canvas, "png", output)
            output.toByteArray()
        }
    }

    private fun scaleAndCrop(image: BufferedImage): BufferedImage {
        val scale = maxOf(
            OUTPUT_WIDTH.toDouble() / image.width.toDouble(),
            OUTPUT_HEIGHT.toDouble() / image.height.toDouble()
        )
        val scaledWidth = (image.width * scale).toInt()
        val scaledHeight = (image.height * scale).toInt()
        val offsetX = (OUTPUT_WIDTH - scaledWidth) / 2
        val offsetY = (OUTPUT_HEIGHT - scaledHeight) / 2

        val scaled = BufferedImage(OUTPUT_WIDTH, OUTPUT_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val graphics = scaled.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.drawImage(image, offsetX, offsetY, scaledWidth, scaledHeight, null)
        } finally {
            graphics.dispose()
        }
        return scaled
    }

    private fun titleAscent(): Int {
        val probe = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
        return try {
            probe.font = Font("SansSerif", Font.BOLD, 58)
            probe.fontMetrics.ascent
        } finally {
            probe.dispose()
        }
    }

    companion object {
        private const val OUTPUT_WIDTH = 1200
        private const val OUTPUT_HEIGHT = 630
        private const val HORIZONTAL_PADDING = 96
    }
}
