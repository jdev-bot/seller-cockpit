package com.sellercockpit.api.service

import com.sellercockpit.domain.model.*
import jakarta.enterprise.context.ApplicationScoped
import java.math.BigDecimal
import java.math.RoundingMode

@ApplicationScoped
class PricingEngine {

    companion object {
        private val DEFAULT_PLATFORM_FEE_PERCENT = BigDecimal("0.11") // ~11% eBay
        private val DEFAULT_SHIPPING = BigDecimal("5.90")
        private val DEFAULT_PACKAGING = BigDecimal("1.50")
        private val VAT_19 = BigDecimal("0.19")
        private val ONE = BigDecimal.ONE
        private val HUNDRED = BigDecimal("100")
    }

    fun calculate(input: PricingInput): PricingRecommendation {
        return when (input.sellerMode) {
            SellerMode.PRIVATE_DECLUTTERING -> calculatePrivateDecluttering(input)
            SellerMode.PRIVATE_RESELLING -> calculatePrivateReselling(input)
            SellerMode.PROFESSIONAL -> calculateProfessional(input)
        }
    }

    private fun calculatePrivateDecluttering(input: PricingInput): PricingRecommendation {
        val marketResearch = input.marketResearch
            ?: return fallbackRecommendation(input, "No market research available")

        val low = marketResearch.estimatedMarketLow.amount
        val mid = marketResearch.estimatedMarketMid.amount
        val high = marketResearch.estimatedMarketHigh.amount

        val shipping = input.shippingCost?.amount ?: DEFAULT_SHIPPING
        val packaging = input.packagingCost?.amount ?: DEFAULT_PACKAGING
        val platformFeePct = DEFAULT_PLATFORM_FEE_PERCENT

        val fees = mid * platformFeePct
        val totalCosts = fees + shipping + packaging
        val expectedPayout = mid - totalCosts

        val feeBreakdown = FeeBreakdown(
            platformFee = Money(fees.setScale(2, RoundingMode.HALF_UP)),
            shippingCost = Money(shipping.setScale(2, RoundingMode.HALF_UP)),
            packagingCost = Money(packaging.setScale(2, RoundingMode.HALF_UP)),
            otherCosts = Money.zero(),
            totalCosts = Money(totalCosts.setScale(2, RoundingMode.HALF_UP))
        )

        return PricingRecommendation(
            quickSalePrice = Money(low.setScale(2, RoundingMode.HALF_UP)),
            recommendedPrice = Money(mid.setScale(2, RoundingMode.HALF_UP)),
            optimisticPrice = Money(high.setScale(2, RoundingMode.HALF_UP)),
            expectedPayout = Money(expectedPayout.setScale(2, RoundingMode.HALF_UP)),
            feeBreakdown = feeBreakdown,
            explanation = "Decluttering mode: quick-sale = market low, recommended = market mid, optimistic = market high. Expected payout subtracts estimated platform fees (${(platformFeePct * HUNDRED).setScale(0, RoundingMode.HALF_UP)}%), shipping (${shipping} €), and packaging (${packaging} €).",
            confidence = marketResearch.confidence
        )
    }

    private fun calculatePrivateReselling(input: PricingInput): PricingRecommendation {
        val marketResearch = input.marketResearch
            ?: return fallbackRecommendation(input, "No market research available")
        val purchasePrice = input.purchasePrice?.amount
            ?: return fallbackRecommendation(input, "Purchase price required for reselling mode")

        val mid = marketResearch.estimatedMarketMid.amount
        val high = marketResearch.estimatedMarketHigh.amount
        val low = marketResearch.estimatedMarketLow.amount

        val shipping = input.shippingCost?.amount ?: DEFAULT_SHIPPING
        val packaging = input.packagingCost?.amount ?: DEFAULT_PACKAGING
        val platformFeePct = DEFAULT_PLATFORM_FEE_PERCENT

        // Break-even: purchase + shipping + packaging + (breakEven * feePct)
        // breakEven = (purchase + shipping + packaging) / (1 - feePct)
        val fixedCosts = purchasePrice + shipping + packaging
        val breakEven = fixedCosts / (ONE - platformFeePct)
        val feesAtBreakEven = breakEven * platformFeePct

        val doNotSellBelow = maxOf(breakEven, input.desiredMinimumPrice?.amount ?: BigDecimal.ZERO)

        // Recommended: use market mid if above break-even, else suggest break-even + small buffer
        val recommended = if (mid > breakEven) mid else breakEven + BigDecimal("5.00")

        val feesAtRec = recommended * platformFeePct
        val totalCostsAtRec = feesAtRec + shipping + packaging
        val profit = recommended - purchasePrice - totalCostsAtRec
        val roi = if (purchasePrice.compareTo(BigDecimal.ZERO) != 0) (profit / purchasePrice) * HUNDRED else BigDecimal.ZERO

        val feeBreakdown = FeeBreakdown(
            platformFee = Money(feesAtRec.setScale(2, RoundingMode.HALF_UP)),
            shippingCost = Money(shipping.setScale(2, RoundingMode.HALF_UP)),
            packagingCost = Money(packaging.setScale(2, RoundingMode.HALF_UP)),
            otherCosts = Money.zero(),
            totalCosts = Money(totalCostsAtRec.setScale(2, RoundingMode.HALF_UP))
        )

        return PricingRecommendation(
            quickSalePrice = Money(low.setScale(2, RoundingMode.HALF_UP)),
            recommendedPrice = Money(recommended.setScale(2, RoundingMode.HALF_UP)),
            optimisticPrice = Money(high.setScale(2, RoundingMode.HALF_UP)),
            breakEvenPrice = Money(breakEven.setScale(2, RoundingMode.HALF_UP)),
            doNotSellBelowPrice = Money(doNotSellBelow.setScale(2, RoundingMode.HALF_UP)),
            estimatedProfit = Money(profit.setScale(2, RoundingMode.HALF_UP)),
            roiPercent = roi.setScale(1, RoundingMode.HALF_UP),
            feeBreakdown = feeBreakdown,
            explanation = "Reselling mode: break-even covers purchase price (${purchasePrice} €), shipping (${shipping} €), packaging (${packaging} €), and platform fees (${(platformFeePct * HUNDRED).setScale(0, RoundingMode.HALF_UP)}%). Profit = sale price - purchase price - total costs. ROI = profit / purchase price.",
            confidence = marketResearch.confidence
        )
    }

    private fun calculateProfessional(input: PricingInput): PricingRecommendation {
        val marketResearch = input.marketResearch
            ?: return fallbackRecommendation(input, "No market research available")
        val purchasePrice = input.purchasePrice?.amount
            ?: return fallbackRecommendation(input, "Purchase price required for professional mode")

        val purchaseNet = if (input.purchasePriceIncludesVat == true) {
            purchasePrice / (ONE + (input.taxProfile?.vatRatePercent ?: (VAT_19 * HUNDRED)) / HUNDRED)
        } else purchasePrice

        val vatRate = input.taxProfile?.vatRatePercent?.divide(HUNDRED) ?: VAT_19
        val shipping = input.shippingCost?.amount ?: DEFAULT_SHIPPING
        val packaging = input.packagingCost?.amount ?: DEFAULT_PACKAGING
        val other = input.otherCosts?.amount ?: BigDecimal.ZERO
        val targetMargin = input.targetMarginPercent?.divide(HUNDRED) ?: BigDecimal("0.25")
        val platformFeePct = DEFAULT_PLATFORM_FEE_PERCENT

        // Target-margin price: (purchaseNet + shipping + packaging + other) / (1 - targetMargin - platformFeePct)
        val costBase = purchaseNet + shipping + packaging + other
        val denominator = ONE - targetMargin - platformFeePct
        val targetPrice = if (denominator > BigDecimal.ZERO) costBase / denominator else costBase * BigDecimal("2.0")

        val platformFees = targetPrice * platformFeePct
        val grossRevenue = targetPrice
        val netRevenue = grossRevenue / (ONE + vatRate)
        val vatAmount = grossRevenue - netRevenue
        val totalCosts = purchaseNet + platformFees + shipping + packaging + other
        val netProfit = netRevenue - totalCosts
        val margin = if (grossRevenue.compareTo(BigDecimal.ZERO) != 0) (netProfit / grossRevenue) * HUNDRED else BigDecimal.ZERO
        val breakEven = costBase / (ONE - platformFeePct)

        val feeBreakdown = FeeBreakdown(
            platformFee = Money(platformFees.setScale(2, RoundingMode.HALF_UP)),
            shippingCost = Money(shipping.setScale(2, RoundingMode.HALF_UP)),
            packagingCost = Money(packaging.setScale(2, RoundingMode.HALF_UP)),
            otherCosts = Money(other.setScale(2, RoundingMode.HALF_UP)),
            totalCosts = Money(totalCosts.setScale(2, RoundingMode.HALF_UP))
        )

        val taxBreakdown = TaxBreakdown(
            vatRatePercent = vatRate * HUNDRED,
            vatAmount = Money(vatAmount.setScale(2, RoundingMode.HALF_UP)),
            netRevenue = Money(netRevenue.setScale(2, RoundingMode.HALF_UP)),
            grossRevenue = Money(grossRevenue.setScale(2, RoundingMode.HALF_UP))
        )

        return PricingRecommendation(
            quickSalePrice = Money(marketResearch.estimatedMarketLow.amount.setScale(2, RoundingMode.HALF_UP)),
            recommendedPrice = Money(targetPrice.setScale(2, RoundingMode.HALF_UP)),
            optimisticPrice = Money(marketResearch.estimatedMarketHigh.amount.setScale(2, RoundingMode.HALF_UP)),
            breakEvenPrice = Money(breakEven.setScale(2, RoundingMode.HALF_UP)),
            netProfit = Money(netProfit.setScale(2, RoundingMode.HALF_UP)),
            marginPercent = margin.setScale(1, RoundingMode.HALF_UP),
            feeBreakdown = feeBreakdown,
            taxBreakdown = taxBreakdown,
            explanation = "Professional mode: net profit = net revenue (gross / (1+VAT)) - (purchase net + platform fees + shipping + packaging + other). Margin = net profit / gross price. Target margin ${(targetMargin * HUNDRED).setScale(0, RoundingMode.HALF_UP)}% applied. VAT rate ${(vatRate * HUNDRED).setScale(0, RoundingMode.HALF_UP)}%.",
            confidence = marketResearch.confidence
        )
    }

    private fun fallbackRecommendation(input: PricingInput, reason: String): PricingRecommendation = PricingRecommendation(
        quickSalePrice = null,
        recommendedPrice = Money(BigDecimal.ZERO),
        optimisticPrice = null,
        explanation = "Pricing unavailable: $reason. Please provide more information.",
        confidence = ConfidenceLevel.LOW
    )
}
