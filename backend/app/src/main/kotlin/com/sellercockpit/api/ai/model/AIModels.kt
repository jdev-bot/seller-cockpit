package com.sellercockpit.api.ai.model

import com.sellercockpit.domain.model.*
import kotlinx.serialization.Serializable

@Serializable
data class ProductRecognitionResult(
    val title: String,
    val brand: String?,
    val model: String?,
    val category: String?,
    val variant: String?,
    val color: String?,
    val sizeOrCapacity: String?,
    val identifierType: String?,
    val identifierValue: String?,
    val accessories: List<String>,
    val confidence: Double,
    val reasoning: String
)

@Serializable
data class ConditionAssessmentResult(
    val condition: ProductCondition,
    val visibleDefects: List<String>,
    val functionalityConfirmed: Boolean?,
    val missingInformation: List<String>,
    val confidence: Double,
    val reasoning: String
)

@Serializable
data class ListingDraftRequest(
    val productFacts: ProductFacts,
    val conditionAssessment: ConditionAssessment,
    val platform: MarketplacePlatform,
    val pricing: PricingRecommendation?
)

@Serializable
data class ListingDraftResult(
    val title: String,
    val description: String,
    val conditionText: String,
    val attributes: Map<String, String>,
    val warnings: List<String>
)

@Serializable
data class PriceResearchResult(
    val estimatedMarketLow: Money,
    val estimatedMarketMid: Money,
    val estimatedMarketHigh: Money,
    val comparables: List<MarketComparable>,
    val confidence: ConfidenceLevel,
    val summary: String,
    val warnings: List<String>
)
