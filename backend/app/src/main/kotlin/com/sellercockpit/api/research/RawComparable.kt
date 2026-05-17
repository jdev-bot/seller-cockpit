package com.sellercockpit.api.research

import com.sellercockpit.domain.model.MarketplacePlatform
import java.math.BigDecimal
import java.time.Instant

/**
 * Raw comparable listing before normalization and scoring.
 */
data class RawComparable(
    val platform: MarketplacePlatform,
    val title: String,
    val priceValue: BigDecimal,
    val currency: String,
    val shippingPrice: BigDecimal?,
    val conditionText: String?,
    val sold: Boolean,
    val url: String?,
    val imageUrl: String?,
    val listingDate: Instant?,
    val seller: String?,
    val location: String?
)
