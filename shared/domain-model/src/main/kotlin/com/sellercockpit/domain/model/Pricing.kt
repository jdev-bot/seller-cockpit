package com.sellercockpit.domain.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class PricingProfile(
    val sellerMode: SellerMode,
    val purchasePrice: Money? = null,
    val purchasePriceIncludesVat: Boolean? = null,
    val shippingCost: Money? = null,
    val packagingCost: Money? = null,
    val otherCosts: Money? = null,
    val platformFeeEstimate: Money? = null,
    val taxMode: TaxMode? = null,
    val vatRatePercent: BigDecimal? = null,
    val targetMarginPercent: BigDecimal? = null,
    val desiredMinimumPrice: Money? = null,
    val desiredMinimumProfit: Money? = null
)

@Serializable
data class FeeBreakdown(
    val platformFee: Money,
    val shippingCost: Money,
    val packagingCost: Money,
    val otherCosts: Money,
    val totalCosts: Money
)

@Serializable
data class TaxBreakdown(
    val vatRatePercent: BigDecimal,
    val vatAmount: Money,
    val netRevenue: Money,
    val grossRevenue: Money
)

@Serializable
data class PricingRecommendation(
    val quickSalePrice: Money? = null,
    val recommendedPrice: Money,
    val optimisticPrice: Money? = null,
    val breakEvenPrice: Money? = null,
    val doNotSellBelowPrice: Money? = null,
    val expectedPayout: Money? = null,
    val estimatedProfit: Money? = null,
    val roiPercent: BigDecimal? = null,
    val netProfit: Money? = null,
    val marginPercent: BigDecimal? = null,
    val feeBreakdown: FeeBreakdown? = null,
    val taxBreakdown: TaxBreakdown? = null,
    val explanation: String,
    val confidence: ConfidenceLevel
)

@Serializable
data class PricingInput(
    val sellerMode: SellerMode,
    val marketResearch: MarketResearchResult? = null,
    val purchasePrice: Money? = null,
    val shippingCost: Money? = null,
    val packagingCost: Money? = null,
    val platformFeeEstimate: Money? = null,
    val desiredMinimumPrice: Money? = null,
    val desiredMinimumProfit: Money? = null,
    val targetMarginPercent: BigDecimal? = null,
    val taxProfile: TaxProfile? = null
)
