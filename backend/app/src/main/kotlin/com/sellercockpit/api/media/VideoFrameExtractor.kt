package com.sellercockpit.api.media

import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Extracts frames from uploaded video using local ffmpeg.
 * Supports both periodic sampling and manual timestamp selection.
 *
 * Strategy:
 * - First frame (0.5s) — usually shows the whole product
 * - Every 2 seconds for short videos (< 10s)
 * - Every 3 seconds for medium (10-30s)
 * - Every 5 seconds for longer videos
 * - Up to 20 frames max for cost control
 *
 * All frames saved as JPEG in a temp directory, uploaded to MinIO,
 * then temp files are cleaned up.
 */
@ApplicationScoped
class VideoFrameExtractor {

    private val log = Logger.getLogger(javaClass)

    data class ExtractionConfig(
        val maxFrames: Int = 20,
        val shortVideoIntervalSec: Double = 2.0,
        val mediumVideoIntervalSec: Double = 3.0,
        val longVideoIntervalSec: Double = 5.0,
        val qualityJpeg: Int = 85,
        val targetWidth: Int = 1200,
        val targetHeight: Int = 1200
    )

    data class ExtractedFrame(
        val frameNumber: Int,
        val timestampSec: Double,
        val localFile: File,
        val filename: String
    )

    /**
     * Extract candidate frames from a video file (local path or temp download).
     * Returns list of local frame files that must be cleaned up by caller.
     */
    fun extractFrames(
        videoPath: String,
        config: ExtractionConfig = ExtractionConfig()
    ): List<ExtractedFrame> {
        val videoFile = File(videoPath)
        require(videoFile.exists()) { "Video file not found: $videoPath" }

        // Get video duration
        val duration = getVideoDuration(videoPath)
        log.info("Video duration: ${duration}s for $videoPath")

        val interval = when {
            duration <= 10.0 -> config.shortVideoIntervalSec
            duration <= 30.0 -> config.mediumVideoIntervalSec
            else -> config.longVideoIntervalSec
        }

        val numFrames = ((duration - 1.0) / interval).toInt().coerceIn(1, config.maxFrames)

        val outputDir = Files.createTempDirectory("sc_frames_${UUID.randomUUID().toString().take(8)}")
        val frames = mutableListOf<ExtractedFrame>()

        for (i in 1..numFrames) {
            val timestamp = 0.5 + (i - 1) * interval // start at 0.5s, not 0
            val frameFile = outputDir.resolve("frame_${i.toString().padStart(3, '0')}.jpg").toFile()

            val cmd = listOf(
                "ffmpeg", "-y",
                "-ss", "%.2f".format(timestamp), // seek to timestamp
                "-i", videoPath,
                "-vframes", "1", // extract exactly 1 frame
                "-q:v", "2", // high quality (lower = better, 1-5 range)
                "-vf", "scale=${config.targetWidth}:${config.targetHeight}:force_original_aspect_ratio=decrease", // resize
                frameFile.absolutePath
            )

            val proc = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()

            val finished = proc.waitFor(30, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                log.warn("ffmpeg timeout at ${timestamp}s for frame $i")
                continue
            }

            if (proc.exitValue() == 0 && frameFile.exists() && frameFile.length() > 1024) {
                frames.add(ExtractedFrame(i, timestamp, frameFile, frameFile.name))
                log.debug("Extracted frame $i at ${timestamp}s")
            } else {
                log.warn("Failed to extract frame $i at ${timestamp}s (exit=${proc.exitValue()})")
            }
        }

        log.info("Extracted ${frames.size}/$numFrames frames from $videoPath")
        return frames
    }

    fun getVideoDuration(videoPath: String): Double {
        val cmd = listOf(
            "ffprobe", "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            videoPath
        )
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor(5, TimeUnit.SECONDS)
        return output.toDoubleOrNull() ?: 10.0 // fallback
    }

    fun getVideoInfo(videoPath: String): VideoInfo {
        // width x height
        val wCmd = listOf(
            "ffprobe", "-v", "error",
            "-select_streams", "v:0",
            "-show_entries", "stream=width,height",
            "-of", "csv=s=x:p=0",
            videoPath
        )
        val p = ProcessBuilder(wCmd).redirectErrorStream(true).start()
        val dim = p.inputStream.bufferedReader().readText().trim()
        val parts = dim.split("x")
        return VideoInfo(
            width = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            height = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            duration = getVideoDuration(videoPath)
        )
    }

    data class VideoInfo(
        val width: Int,
        val height: Int,
        val duration: Double
    )

    /**
     * Deletes all extracted frame files and their parent directory.
     * Call this after frames are uploaded to storage.
     */
    fun cleanupFrames(frames: List<ExtractedFrame>) {
        frames.map { it.localFile }.distinctBy { it.parent }.forEach { file ->
            try {
                file.parentFile?.listFiles()?.forEach { it.delete() }
                file.parentFile?.delete()
            } catch (e: Exception) {
                log.warn("Failed to cleanup temp frames", e)
            }
        }
    }
}
