package com.sellercockpit.api.service

import com.sellercockpit.api.ai.pipeline.AIOrchestrator
import kotlinx.coroutines.runBlocking
import com.sellercockpit.api.ai.pipeline.FrameSelector
import com.sellercockpit.api.ai.provider.AIProviderRegistry
import com.sellercockpit.api.media.ImageOptimizationService
import com.sellercockpit.api.media.VideoFrameExtractor
import com.sellercockpit.api.model.MediaAssetEntity
import com.sellercockpit.api.repository.MediaAssetRepository
import com.sellercockpit.domain.model.MediaAssetType
import com.sellercockpit.domain.model.ProductCaseStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.UUID

/**
 * Orchestrates the full media processing pipeline:
 *
 * 1. Identify video assets among uploaded media
 * 2. Download video from MinIO to local temp
 * 3. Extract frames via ffmpeg (VideoFrameExtractor)
 * 4. Optimize extracted frames (ImageOptimizationService)
 * 5. Upload optimized frames + thumbnails back to MinIO
 * 6. Select best frames via AI (FrameSelector)
 * 7. Store frame MediaAssetEntity records
 * 8. Update product case with combined photo + frame assets
 * 9. Run AI identification pipeline on selected images
 *
 * Cost control: max 12 frames sent to AI, max 20 extracted.
 */
@ApplicationScoped
class MediaProcessingService @Inject constructor(
    private val storageService: StorageService,
    private val videoFrameExtractor: VideoFrameExtractor,
    private val imageOptimizationService: ImageOptimizationService,
    private val frameSelector: FrameSelector,
    private val aiProviderRegistry: AIProviderRegistry,
    private val mediaAssetRepository: MediaAssetRepository,
    private val aiOrchestrator: AIOrchestrator
) {

    private val log = Logger.getLogger(javaClass)

    data class ProcessingResult(
        val extractedFrameCount: Int,
        val optimizedImageCount: Int,
        val selectedMainImageId: String?,
        val newMediaAssets: List<MediaAssetEntity>
    )

    /**
     * Main entry point called by ProductCaseService.processMedia().
     *
     * @param productCaseId the product case being processed
     * @param allMediaAssets all media assets associated with the case
     * @param productCaseTitle title for AI context
     * @param productCaseStatus callback to update status on the entity
     */
    @Transactional
    fun processAndExtractFrames(
        productCaseId: UUID,
        allMediaAssets: List<MediaAssetEntity>,
        productCaseTitle: String?,
        updateStatus: (ProductCaseStatus) -> Unit
    ): ProcessingResult {
        updateStatus(ProductCaseStatus.PROCESSING_MEDIA)

        val videoAssets = allMediaAssets.filter {
            it.type == MediaAssetType.ORIGINAL_VIDEO
        }
        val photoAssets = allMediaAssets.filter {
            it.type == MediaAssetType.ORIGINAL_PHOTO
        }

        var extractedFrames: List<VideoFrameExtractor.ExtractedFrame> = emptyList()
        var optimizedImages: List<ImageOptimizationService.OptimizedImage> = emptyList()
        val newMediaAssets = mutableListOf<MediaAssetEntity>()

        // === Phase 1: Video frame extraction + optimization ===
        if (videoAssets.isNotEmpty()) {
            log.info("Processing ${videoAssets.size} video assets for case $productCaseId")

            val video = videoAssets.first() // process first video only for MVP
            val tempVideo = Files.createTempFile("sc_video_${productCaseId}", ".mp4").toFile()

            try {
                // Download video
                updateStatus(ProductCaseStatus.PROCESSING_MEDIA)
                storageService.downloadToFile(video.storageUrl, tempVideo)
                log.info("Downloaded video: ${tempVideo.length()} bytes")

                // Extract frames
                extractedFrames = videoFrameExtractor.extractFrames(tempVideo.absolutePath)
                log.info("Extracted ${extractedFrames.size} frames")

                // Optimize frames
                optimizedImages = imageOptimizationService.optimizeBatch(
                    extractedFrames.map { it.localFile }
                )
                log.info("Optimized ${optimizedImages.size} frames")

                // Upload optimized frames + thumbnails to MinIO
                optimizedImages.forEachIndexed { idx, opt ->
                    val frameId = UUID.randomUUID()
                    val frameStorageUrl = "media/$productCaseId/$frameId-frame_$idx.jpg"
                    val thumbStorageUrl = "media/$productCaseId/$frameId-frame_$idx-thumb.jpg"

                    storageService.uploadFile(frameStorageUrl, opt.localFile, "image/jpeg")
                    opt.thumbnailFile?.let {
                        storageService.uploadFile(thumbStorageUrl, it, "image/jpeg")
                    }

                    val entity = MediaAssetEntity().apply {
                        id = frameId
                        this.productCaseId = productCaseId
                        type = MediaAssetType.EXTRACTED_FRAME
                        storageUrl = frameStorageUrl
                        thumbnailUrl = opt.thumbnailFile?.let { thumbStorageUrl }
                        selectedForListing = true
                        metadata = mapOf(
                            "sourceVideo" to (video.storageUrl ?: ""),
                            "frameNumber" to (idx + 1).toString(),
                            "width" to opt.width.toString(),
                            "height" to opt.height.toString(),
                            "fileSizeBytes" to opt.fileSizeBytes.toString(),
                            "wasNormalized" to opt.wasNormalized.toString(),
                            "extractedAt" to (extractedFrames.getOrNull(idx)?.timestampSec?.toString() ?: "0")
                        )
                        createdAt = Instant.now()
                    }
                    newMediaAssets.add(entity)
                    mediaAssetRepository.persist(entity)
                }

            } finally {
                // Cleanup temp files
                tempVideo.delete()
                videoFrameExtractor.cleanupFrames(extractedFrames)
                imageOptimizationService.cleanupOptimized(optimizedImages)
            }
        }

        // === Phase 2: AI Frame Selection (from all image assets) ===
        val allImageAssets = photoAssets + newMediaAssets
        val mainImageId = if (allImageAssets.isNotEmpty()) {
            selectMainImageWithAI(allImageAssets, productCaseTitle ?: "Product")
        } else null

        // Mark selected main image
        mainImageId?.let { mainId ->
            allImageAssets.find { it.id == mainId }?.let {
                it.sortOrder = 0
                // Deselect others slightly — they stay selected but main gets priority
            }
        }

        return ProcessingResult(
            extractedFrameCount = extractedFrames.size,
            optimizedImageCount = optimizedImages.size,
            selectedMainImageId = mainImageId?.toString(),
            newMediaAssets = newMediaAssets
        )
    }

    private fun selectMainImageWithAI(
        imageAssets: List<MediaAssetEntity>,
        productTitle: String
    ): UUID? {
        val provider = aiProviderRegistry.getAnyAvailable()
        if (provider == null) {
            log.warn("No AI provider available for frame selection, using first image")
            return imageAssets.firstOrNull()?.id
        }

        val imageUrls = imageAssets.map { storageService.getPublicUrl(it.storageUrl) }

        return try {
            val result = runBlocking { frameSelector.selectFrames(imageUrls, productTitle, provider) }
            result.mainImage?.frameIndex?.let { idx ->
                imageAssets.getOrNull(idx)?.id
            } ?: imageAssets.firstOrNull()?.id
        } catch (e: Exception) {
            log.error("Frame selection failed, using first image", e)
            imageAssets.firstOrNull()?.id
        }
    }
}
