package com.sellercockpit.domain.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class FeeEstimateRequest(
    val categoryId: String,
    val price: Money,
    val shippingCost: Money? = null,
    val listingFormat: String = "FIXED_PRICE"
)

@Serializable
data class FeeEstimate(
    val insertionFee: Money,
    val finalValueFee: Money,
    val featuredFee: Money? = null,
    val totalEstimatedFee: Money,
    val currency: String
)
