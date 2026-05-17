package com.sellercockpit.api.service

import com.sellercockpit.api.model.ProductCaseEntity
import com.sellercockpit.api.model.MediaAssetEntity
import com.sellercockpit.domain.model.*
import jakarta.enterprise.context.ApplicationScoped
import java.math.BigDecimal

@ApplicationScoped
class MockAIOrchestrator {

    fun runPipeline(entity: ProductCaseEntity, mediaAssets: List<MediaAssetEntity>): AIPipelineResult {
        // In a real implementation, this would call GPT-4V / Claude 3 Vision on the media
        // For MVP mock, we return plausible data based on media count
        val hasMedia = mediaAssets.isNotEmpty()
        val hasVideo = mediaAssets.any { it.type == MediaAssetType.ORIGINAL_VIDEO }

        return AIPipelineResult(
            productFacts = if (hasMedia) ProductFacts(
                title = "Detected Product (mock)",
                brand = "Sample Brand",
                model = "Model XYZ",
                category = "Electronics",
                variant = null,
                color = null,
                sizeOrCapacity = null,
                identifiers = emptyList(),
                accessories = if (hasVideo) listOf("Original cable seen") else emptyList(),
                userConfirmed = false,
                confidence = 0.75
            ) else null,
            conditionAssessment = if (hasMedia) ConditionAssessment(
                condition = ProductCondition.USED_GOOD,
                visibleDefects = listOf("Minor scratches on surface"),
                functionalityConfirmed = null,
                missingInformation = emptyList(),
                confidence = 0.65,
                userConfirmed = false
            ) else null,
            missingQuestions = if (hasMedia) mutableListOf(
                "Does the item work normally?",
                "Is original packaging included?",
                "Are all accessories present?"
            ) else mutableListOf("No media detected. Please upload photos or video."),
            complianceWarnings = emptyList()
        )
    }

    fun runResearch(entity: ProductCaseEntity): MarketResearchResult {
        // Mock research: return a plausible market range based on product title
        val basePrice = when {
            entity.title?.contains("phone", ignoreCase = true) == true -> BigDecimal("250.00")
            entity.title?.contains("keyboard", ignoreCase = true) == true -> BigDecimal("45.00")
            entity.title?.contains("monitor", ignoreCase = true) == true -> BigDecimal("120.00")
            else -> BigDecimal("55.00")
        }
        val low = basePrice * BigDecimal("0.80")
        val mid = basePrice
        val high = basePrice * BigDecimal("1.25")

        return MarketResearchResult(
            comparables = listOf(
                MarketComparable(
                    id = "mock-1",
                    platform = MarketplacePlatform.EBAY,
                    title = "Comparable item A",
                    price = Money(mid, "EUR"),
                    shippingPrice = Money(BigDecimal("4.99")),
                    condition = ProductCondition.USED_GOOD,
                    listingType = "auction",
                    sold = true,
                    relevanceScore = 0.85
                ),
                MarketComparable(
                    id = "mock-2",
                    platform = MarketplacePlatform.KLEINANZEIGEN,
                    title = "Comparable item B",
                    price = Money(low, "EUR"),
                    condition = ProductCondition.USED_GOOD,
                    listingType = "fixed",
                    sold = false,
                    relevanceScore = 0.70
                )
            ),
            estimatedMarketLow = Money(low.setScale(2, java.math.RoundingMode.HALF_UP)),
            estimatedMarketMid = Money(mid.setScale(2, java.math.RoundingMode.HALF_UP)),
            estimatedMarketHigh = Money(high.setScale(2, java.math.RoundingMode.HALF_UP)),
            confidence = ConfidenceLevel.MEDIUM,
            summary = "Found ${if (entity.title != null) 2 else 0} comparable listings. Market range ${low.setScale(0, java.math.RoundingMode.HALF_UP)}-${high.setScale(0, java.math.RoundingMode.HALF_UP)} €. Most likely selling price around ${mid.setScale(0, java.math.RoundingMode.HALF_UP)} €.",
            warnings = if (entity.productFacts?.confidence ?: 0.0 < 0.6) listOf("Low product confidence — verify product details before pricing.") else emptyList()
        )
    }
}

data class AIPipelineResult(
    val productFacts: ProductFacts?,
    val conditionAssessment: ConditionAssessment?,
    val missingQuestions: List<String>,
    val complianceWarnings: List<String>
)
