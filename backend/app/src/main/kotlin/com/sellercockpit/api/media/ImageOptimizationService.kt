package com.sellercockpit.api.media

import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Three-stage image optimization:
 * 1. Normalize — auto-correct brightness/contrast for underexposed frames
 * 2. Resize — downscale to marketplace-friendly dimensions (max 2048px)
 * 3. Quality conversion — encode as progressive JPEG at configurable quality
 *
 * All processing uses the Java AWT imaging stack (no native deps beyond ffmpeg
 * which is already installed). For WebP/AVIF support, consider adding TwelveMonkeys
 * or native cwebp in future iterations.
 */
@ApplicationScoped
class ImageOptimizationService {

    private val log = Logger.getLogger(javaClass)

    data class OptimizationConfig(
        val maxDimension: Int = 2048,
        val thumbnailMax: Int = 400,
        val jpegQuality: Float = 0.88f,
        val autoNormalize: Boolean = true,
        val sharpenAmount: Float = 0.3f
    )

    data class OptimizedImage(
        val localFile: File,
        val width: Int,
        val height: Int,
        val fileSizeBytes: Long,
        val format: String, // "jpeg"
        val wasNormalized: Boolean,
        val thumbnailFile: File?
    )

    /**
     * Optimize a raw frame (from ffmpeg extraction or photo upload).
     * Produces a marketplace-ready image + thumbnail.
     */
    fun optimizeImage(
        sourceFile: File,
        config: OptimizationConfig = OptimizationConfig()
    ): OptimizedImage {
        log.info("Optimizing image: ${sourceFile.name}")

        val image = ImageIO.read(sourceFile)
            ?: throw IllegalArgumentException("Cannot read image: ${sourceFile.absolutePath}")

        var working = image

        // Step 1: Normalize brightness/contrast
        val wasNormalized = if (config.autoNormalize && isUnderexposed(working)) {
            working = normalizeExposure(working)
            true
        } else false

        // Step 2: Resize if too large
        val (newW, newH) = calculateNewDimensions(working.width, working.height, config.maxDimension)
        if (newW != working.width || newH != working.height) {
            working = resize(working, newW, newH)
        }

        // Step 3a: Unsharp mask (light sharpening for digital softness from video frames)
        working = unsharpMask(working, config.sharpenAmount)

        // Step 3b: Encode as JPEG
        val outputDir = Files.createTempDirectory("sc_opt_${UUID.randomUUID().toString().take(8)}")
        val optimizedFile = outputDir.resolve("optimized.jpg").toFile()
        writeJpeg(working, optimizedFile, config.jpegQuality)

        // Step 4: Thumbnail
        val thumbFile = if (config.thumbnailMax > 0) {
            val (tw, th) = calculateNewDimensions(working.width, working.height, config.thumbnailMax)
            val thumb = resize(working, tw, th)
            val f = outputDir.resolve("thumb.jpg").toFile()
            writeJpeg(thumb, f, 0.75f)
            f
        } else null

        return OptimizedImage(
            localFile = optimizedFile,
            width = working.width,
            height = working.height,
            fileSizeBytes = optimizedFile.length(),
            format = "jpeg",
            wasNormalized = wasNormalized,
            thumbnailFile = thumbFile
        )
    }

    /** Batch optimize multiple frames. Returns in same order. */
    fun optimizeBatch(
        sourceFiles: List<File>,
        config: OptimizationConfig = OptimizationConfig()
    ): List<OptimizedImage> {
        return sourceFiles.map { optimizeImage(it, config) }
    }

    // --- Internal image processing ---

    private fun isUnderexposed(img: BufferedImage): Boolean {
        val raster = img.raster
        val pixels = raster.getPixels(0, 0, img.width, img.height, null as IntArray?)
        val sampleSize = pixels.size.coerceAtMost(img.width * img.height * 3) // RGB = 3 bands
        if (sampleSize == 0) return false
        val avg = pixels.take(sampleSize).average()
        return avg < 80 // threshold: < 80/255 average brightness = underexposed
    }

    private fun normalizeExposure(img: BufferedImage): BufferedImage {
        // Simple histogram stretch: expand pixel range to full 0-255
        val raster = img.raster
        val width = img.width
        val height = img.height
        val bands = raster.numBands
        val pixels = raster.getPixels(0, 0, width, height, null as IntArray?)

        val min = pixels.minOrNull() ?: 0
        val max = pixels.maxOrNull() ?: 255
        if (max <= min) return img

        val scale = 255.0 / (max - min)
        for (i in pixels.indices) {
            val v = ((pixels[i] - min) * scale).toInt().coerceIn(0, 255)
            pixels[i] = v
        }

        val out = BufferedImage(width, height, img.type)
        out.raster.setPixels(0, 0, width, height, pixels)
        return out
    }

    private fun resize(img: BufferedImage, targetW: Int, targetH: Int): BufferedImage {
        val scaled = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB)
        val g2 = scaled.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_LANCZOS)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.drawImage(img, 0, 0, targetW, targetH, null)
        g2.dispose()
        return scaled
    }

    private fun unsharpMask(img: BufferedImage, amount: Float): BufferedImage {
        // Light unsharp mask using a simple 3x3 kernel approximation
        // For production, use ConvolveOp with proper kernel
        if (amount <= 0f) return img
        val kernel = floatArrayOf(
            0f, -amount, 0f,
            -amount, 1 + 4 * amount, -amount,
            0f, -amount, 0f
        )
        val op = java.awt.image.ConvolveOp(
            java.awt.image.Kernel(3, 3, kernel),
            java.awt.image.ConvolveOp.EDGE_NO_OP,
            null
        )
        return op.filter(img, null)
    }

    private fun writeJpeg(img: BufferedImage, file: File, quality: Float) {
        val iter = javax.imageio.ImageIO.getImageWritersByFormatName("jpg")
        val writer = iter.next()
        val param = writer.defaultWriteParam
        param.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
        param.compressionQuality = quality

        FileOutputStream(file).use { fos ->
            val ios = javax.imageio.stream.MemoryCacheImageOutputStream(fos)
            writer.output = ios
            writer.write(null, javax.imageio.IIOImage(img, null, null), param)
            ios.close()
        }
        writer.dispose()
    }

    private fun calculateNewDimensions(w: Int, h: Int, max: Int): Pair<Int, Int> {
        if (w <= max && h <= max) return w to h
        val ratio = w.toFloat() / h.toFloat()
        return if (w > h) {
            max to (max / ratio).toInt()
        } else {
            (max * ratio).toInt() to max
        }
    }

    fun cleanupOptimized(images: List<OptimizedImage>) {
        images.map { it.localFile.parentFile }.distinct().forEach { parent ->
            try {
                parent?.listFiles()?.forEach { it.delete() }
                parent?.delete()
            } catch (e: Exception) {
                log.warn("Failed to cleanup optimized images", e)
            }
        }
    }
}
