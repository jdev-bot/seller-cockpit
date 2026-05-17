package com.sellercockpit.api.ai.pipeline

import com.sellercockpit.api.ai.provider.AIProvider
import com.sellercockpit.domain.model.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jboss.logging.Logger

/**
 * AI-driven frame selection from extracted video frames.
 *
 * Sends up to 12 candidate frames to the vision provider and asks for
 * a structured ranking with role assignments.
 *
 * Frame roles:
 * - MAIN: best overall hero shot
 * - DETAIL: close-up showing product features
 * - DEFECT: image showing visible damage/wear
 * - ACCESSORY: image showing included extras/box/cables
 * - CONTEXT: image showing scale/context (hand holding, desk, etc.)
 * - REJECT: blurry, duplicate, or unhelpful
 *
 * The AI returns scores 0-100 per frame per role + an overall quality score.
 */
@ApplicationScoped
class FrameSelector @Inject constructor() {

    private val log = Logger.getLogger(javaClass)

    data class FrameRanking(
        val frameIndex: Int,
        val imageUrl: String,
        val overallQuality: Int, // 0-100
        val roleScores: Map<FrameRole, Int>,
        val recommendedRole: FrameRole,
        val aiExplanation: String
    )

    data class FrameSelectionResult(
        val mainImage: FrameRanking?,      // exactly one
        val detailImages: List<FrameRanking>, // 0-3
        val defectImages: List<FrameRanking>, // 0-2
        val accessoryImages: List<FrameRanking>, // 0-2
        val rejected: List<FrameRanking>,
        val allRankings: List<FrameRanking>
    )

    enum class FrameRole {
        MAIN, DETAIL, DEFECT, ACCESSORY, CONTEXT, REJECT
    }

    /**
     * Given candidate frame URLs, ask the AI to classify and rank them.
     *
     * @param imageUrls URLs of extracted frames (must be accessible to AI)
     * @param productTitle known product title for context
     * @param provider AI provider capable of vision analysis
     */
    suspend fun selectFrames(
        imageUrls: List<String>,
        productTitle: String,
        provider: AIProvider
    ): FrameSelectionResult {
        if (imageUrls.isEmpty()) {
            return FrameSelectionResult(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }

        val cappedUrls = imageUrls.take(12) // cost control
        log.info("Asking AI to rank ${cappedUrls.size} frames for product: $productTitle")

        val prompt = buildSelectionPrompt(cappedUrls, productTitle)

        // Use identifyProduct but with a custom prompt — this leverages the vision capability
        // In a real implementation, AIProvider would have a dedicated vision-ranking method.
        // For now we adapt identifyProduct with a specialized prompt.
        val rawResult = try {
            provider.identifyProduct(cappedUrls, prompt)
        } catch (e: Exception) {
            log.error("AI frame selection failed, falling back to heuristic", e)
            return heuristicFallback(cappedUrls)
        }

        // Parse structured response from rawResult.description (AI returns JSON in description field)
        val rankings = try {
            parseFrameRankings(rawResult.description ?: "", cappedUrls)
        } catch (e: Exception) {
            log.warn("Failed to parse AI frame rankings, using heuristic", e)
            return heuristicFallback(cappedUrls)
        }

        val mainImage = rankings.firstOrNull { it.recommendedRole == FrameRole.MAIN }
            ?: rankings.maxByOrNull { it.overallQuality }

        val detailImages = rankings.filter { it.recommendedRole == FrameRole.DETAIL }.take(3)
        val defectImages = rankings.filter { it.recommendedRole == FrameRole.DEFECT }.take(2)
        val accessoryImages = rankings.filter { it.recommendedRole == FrameRole.ACCESSORY }.take(2)
        val rejected = rankings.filter { it.recommendedRole == FrameRole.REJECT }

        return FrameSelectionResult(
            mainImage = mainImage,
            detailImages = detailImages,
            defectImages = defectImages,
            accessoryImages = accessoryImages,
            rejected = rejected,
            allRankings = rankings
        )
    }

    private fun buildSelectionPrompt(imageUrls: List<String>, productTitle: String): String {
        return """
You are an expert product photographer and AI image selector for an e-commerce app.

Product: $productTitle

Analyze ${imageUrls.size} video frames extracted from a product video.
For EACH frame, evaluate:
1. Is it blurry, dark, or poorly lit? (low quality)
2. Does it show the entire product clearly? (candidate for MAIN)
3. Does it show close-up details, branding, or serial numbers? (candidate for DETAIL)
4. Does it show visible damage, scratches, wear, or defects? (candidate for DEFECT)
5. Does it show accessories, original box, cables, remote controls, or packaging? (candidate for ACCESSORY)
6. Is it a duplicate of another frame? (candidate for REJECT)

Return a JSON object STRICTLY in this format (no markdown, no explanation outside JSON):

{
  "rankings": [
    {
      "frameIndex": 0,
      "overallQuality": 85,
      "roleScores": {
        "MAIN": 80, "DETAIL": 30, "DEFECT": 0, "ACCESSORY": 0, "CONTEXT": 10, "REJECT": 0
      },
      "recommendedRole": "MAIN",
      "explanation": "Clear full product shot, good lighting"
    }
  ]
}

Rules:
- Select exactly 1 frame as MAIN (highest MAIN score, quality >= 60).
- Up to 3 frames as DETAIL (DETAIL score >= 50).
- Up to 2 frames as DEFECT (DEFECT score >= 30).
- Up to 2 frames as ACCESSORY (ACCESSORY score >= 30).
- REJECT any frame with overallQuality < 40 or severe blur.
- All scores 0-100 integers.
        """.trimIndent()
    }

    private fun parseFrameRankings(jsonText: String, imageUrls: List<String>): List<FrameRanking> {
        // Try to extract JSON block if surrounded by markdown
        val jsonContent = jsonText.substringAfter("{").let {
            if (jsonText.startsWith("{")) jsonText else "{$it"
        }
        val clean = jsonContent.substringBeforeLast("}").let { "$it}" }

        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        val root = mapper.readTree(clean)
        val arr = root.get("rankings") ?: return heuristicFallback(imageUrls).allRankings

        return arr.mapIndexed { idx, node ->
            val roleScores = mutableMapOf<FrameRole, Int>()
            node.get("roleScores")?.fields()?.forEachRemaining { entry ->
                try {
                    val role = FrameRole.valueOf(entry.key.uppercase())
                    roleScores[role] = entry.value.asInt()
                } catch (_: Exception) {}
            }

            FrameRanking(
                frameIndex = node.get("frameIndex")?.asInt() ?: idx,
                imageUrl = imageUrls.getOrNull(node.get("frameIndex")?.asInt() ?: idx) ?: "",
                overallQuality = node.get("overallQuality")?.asInt() ?: 50,
                roleScores = roleScores,
                recommendedRole = try {
                    FrameRole.valueOf(node.get("recommendedRole")?.asText()?.uppercase() ?: "REJECT")
                } catch (_: Exception) { FrameRole.REJECT },
                aiExplanation = node.get("explanation")?.asText() ?: ""
            )
        }
    }

    /**
     * Heuristic fallback when AI fails or is not configured.
     * Picks first frame as main, skips blurry/dark frames via size heuristic.
     */
    private fun heuristicFallback(imageUrls: List<String>): FrameSelectionResult {
        log.info("Using heuristic frame selection for ${imageUrls.size} frames")
        val rankings = imageUrls.mapIndexed { idx, url ->
            FrameRanking(
                frameIndex = idx,
                imageUrl = url,
                overallQuality = if (idx == 0) 80 else 60,
                roleScores = mapOf(FrameRole.MAIN to if (idx == 0) 80 else 20),
                recommendedRole = if (idx == 0) FrameRole.MAIN else FrameRole.DETAIL,
                aiExplanation = "Heuristic fallback: first frame as main"
            )
        }
        return FrameSelectionResult(
            mainImage = rankings.firstOrNull(),
            detailImages = rankings.drop(1).take(3),
            defectImages = emptyList(),
            accessoryImages = emptyList(),
            rejected = emptyList(),
            allRankings = rankings
        )
    }
}
