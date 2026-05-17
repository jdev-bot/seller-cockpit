package com.sellercockpit.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MarketComparable(
    val id: String,
    val platform: MarketplacePlatform,
    val title: String,
    val price: Money,
    val shippingPrice: Money? = null,
    val condition: ProductCondition? = null,
    val listingType: String? = null,
    val sold: Boolean? = null,
    val soldAt: String? = null,
    val url: String? = null,
    val imageUrl: String? = null,
    val relevanceScore: Double = 0.0,
    val notes: String? = null
)

@Serializable
data class MarketResearchResult(
    val comparables: List<MarketComparable> = emptyList(),
    val estimatedMarketLow: Money,
    val estimatedMarketMid: Money,
    val estimatedMarketHigh: Money,
    val confidence: ConfidenceLevel,
    val summary: String,
    val warnings: List<String> = emptyList()
)
