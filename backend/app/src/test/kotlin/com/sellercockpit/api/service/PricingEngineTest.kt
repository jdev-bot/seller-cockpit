package com.sellercockpit.api.service

import com.sellercockpit.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PricingEngineTest {

    private val engine = PricingEngine()

    private fun marketResearch(low: Double, mid: Double, high: Double) = MarketResearchResult(
        comparables = emptyList(),
        estimatedMarketLow = Money(BigDecimal(low.toString())),
        estimatedMarketMid = Money(BigDecimal(mid.toString())),
        estimatedMarketHigh = Money(BigDecimal(high.toString())),
        confidence = ConfidenceLevel.HIGH,
        summary = "test",
        warnings = emptyList()
    )

    private fun input(
        mode: SellerMode,
        research: MarketResearchResult,
        purchasePrice: Double? = null,
        shipping: Double? = 5.90,
        packaging: Double? = 1.50
    ) = PricingInput(
        sellerMode = mode,
        marketResearch = research,
        purchasePrice = purchasePrice?.let { Money(BigDecimal(it.toString())) },
        shippingCost = shipping?.let { Money(BigDecimal(it.toString())) },
        packagingCost = packaging?.let { Money(BigDecimal(it.toString())) },
        platformFeeEstimate = Money(BigDecimal("0"))
    )

    @Test
    fun `decluttering - returns market range with expected payout`() = runTest {
        val research = marketResearch(30.0, 50.0, 70.0)
        val result = engine.calculate(input(SellerMode.PRIVATE_DECLUTTERING, research))

        assertNotNull(result.quickSalePrice)
        assertEquals(30.0, result.quickSalePrice!!.amount.toDouble(), 0.01)
        assertEquals(50.0, result.recommendedPrice.amount.toDouble(), 0.01)
        assertEquals(70.0, result.optimisticPrice!!.amount.toDouble(), 0.01)

        assertNotNull(result.expectedPayout)
        // expectedPayout = 50 - (50*0.11 + 5.90 + 1.50) = 50 - (5.50 + 7.40) = 37.10
        assertEquals(37.10, result.expectedPayout!!.amount.toDouble(), 0.5)
        assertNotNull(result.feeBreakdown)
    }

    @Test
    fun `decluttering - without market research returns fallback`() = runTest {
        val result = engine.calculate(PricingInput(SellerMode.PRIVATE_DECLUTTERING, null))
        assertEquals(ConfidenceLevel.LOW, result.confidence)
        assertTrue(result.explanation.contains("No market research"))
    }

    @Test
    fun `private reselling - calculates break-even, profit, ROI`() = runTest {
        val research = marketResearch(30.0, 50.0, 70.0)
        val result = engine.calculate(input(SellerMode.PRIVATE_RESELLING, research, purchasePrice = 20.0))

        assertNotNull(result.breakEvenPrice)
        // breakEven = (20 + 5.90 + 1.50) / (1 - 0.11) = 27.40 / 0.89 = 30.79
        val breakEven = result.breakEvenPrice!!.amount
        assertTrue(breakEven.toDouble() > 30.0 && breakEven.toDouble() < 35.0)

        assertNotNull(result.estimatedProfit)
        assertNotNull(result.roiPercent)
        assertTrue(result.roiPercent!!.toDouble() > 0)

        assertEquals(50.0, result.recommendedPrice.amount.toDouble(), 0.01)
        assertNotNull(result.doNotSellBelowPrice)
    }

    @Test
    fun `private reselling - without purchase price returns fallback`() = runTest {
        val research = marketResearch(30.0, 50.0, 70.0)
        val result = engine.calculate(input(SellerMode.PRIVATE_RESELLING, research))
        assertEquals(ConfidenceLevel.LOW, result.confidence)
        assertTrue(result.explanation.contains("purchase price required"))
    }

    @Test
    fun `private reselling - break even is respected`() = runTest {
        val research = marketResearch(30.0, 40.0, 50.0)
        val result = engine.calculate(input(SellerMode.PRIVATE_RESELLING, research, purchasePrice = 35.0))
        // At 40 market mid, breakEven ≈ (35+5.90+1.50)/0.89 = 47.6
        // Since mid < breakEven, recommended = breakEven + buffer
        assertTrue(result.recommendedPrice.amount.toDouble() > 45.0)
        assertTrue(result.estimatedProfit!!.amount.toDouble() < 5.0) // thin margin
    }

    @Test
    fun `professional - calculates net profit, margin, VAT`() = runTest {
        val research = marketResearch(30.0, 60.0, 90.0)
        val input = PricingInput(
            sellerMode = SellerMode.PROFESSIONAL,
            marketResearch = research,
            purchasePrice = Money(BigDecimal("30.00")),
            shippingCost = Money(BigDecimal("5.90")),
            packagingCost = Money(BigDecimal("1.50")),
            taxProfile = PricingProfile(
                sellerMode = SellerMode.PROFESSIONAL,
                purchasePriceIncludesVat = false,
                taxMode = TaxMode.REGULAR_VAT,
                vatRatePercent = BigDecimal("19.00"),
                targetMarginPercent = BigDecimal("25.00")
            ),
            targetMarginPercent = BigDecimal("0.25")
        )

        val result = engine.calculate(input)

        assertNotNull(result.netProfit)
        assertNotNull(result.marginPercent)
        assertNotNull(result.taxBreakdown)
        assertNotNull(result.breakEvenPrice)

        // Margin should be ~25%
        assertTrue(result.marginPercent!!.toDouble() > 20.0)
        assertTrue(result.marginPercent!!.toDouble() < 30.0)

        // Net revenue should be gross / 1.19
        val gross = result.recommendedPrice.amount.toDouble()
        val vat = result.taxBreakdown!!.vatAmount.amount.toDouble()
        val netRev = result.taxBreakdown!!.netRevenue.amount.toDouble()
        assertEquals(gross / 1.19, netRev, 0.5)
        assertEquals(gross - netRev, vat, 0.5)

        // Net profit should be positive
        assertTrue(result.netProfit!!.amount.toDouble() > 0)
    }

    @Test
    fun `professional - respects target margin`() = runTest {
        val research = marketResearch(50.0, 80.0, 120.0)
        val input = PricingInput(
            sellerMode = SellerMode.PROFESSIONAL,
            marketResearch = research,
            purchasePrice = Money(BigDecimal("40.00")),
            shippingCost = Money(BigDecimal("5.90")),
            packagingCost = Money(BigDecimal("1.50")),
            targetMarginPercent = BigDecimal("0.40"), // aggressive 40%
            taxProfile = PricingProfile(
                sellerMode = SellerMode.PROFESSIONAL,
                purchasePriceIncludesVat = false
            )
        )

        val result = engine.calculate(input)
        // target price = (40 + 5.90 + 1.50) / (1 - 0.40 - 0.11) = 47.40 / 0.49 = 96.73
        assertTrue(result.recommendedPrice.amount.toDouble() > 80.0)
        assertTrue(result.marginPercent!!.toDouble() >= 35.0)
    }

    @Test
    fun `professional - handles purchase price with VAT`() = runTest {
        val research = marketResearch(50.0, 80.0, 120.0)
        val input = PricingInput(
            sellerMode = SellerMode.PROFESSIONAL,
            marketResearch = research,
            purchasePrice = Money(BigDecimal("119.00")), // gross, includes 19% VAT
            shippingCost = Money(BigDecimal("5.90")),
            packagingCost = Money(BigDecimal("1.50")),
            taxProfile = PricingProfile(
                sellerMode = SellerMode.PROFESSIONAL,
                purchasePriceIncludesVat = true,
                vatRatePercent = BigDecimal("19.00"),
                targetMarginPercent = BigDecimal("25.00")
            ),
            targetMarginPercent = BigDecimal("0.25")
        )

        val result = engine.calculate(input)
        // Purchase net = 119 / 1.19 = 100
        // Higher cost base should yield higher recommended price
        assertTrue(result.recommendedPrice.amount.toDouble() > 100.0)
        assertNotNull(result.taxBreakdown)
    }

    @Test
    fun `all modes - money operations are safe`() = runTest {
        val zero = Money.zero()
        assertTrue(zero.isZero())
        assertFalse(zero.isPositive())
        assertFalse(zero.isNegative())

        val a = Money(BigDecimal("10.00"))
        val b = Money(BigDecimal("5.00"))
        assertEquals(Money(BigDecimal("15.00")), a + b)
        assertEquals(Money(BigDecimal("5.00")), a - b)
    }

    @Test
    fun `decluttering - with minimum acceptable price`() = runTest {
        val research = marketResearch(30.0, 45.0, 60.0)
        val input = PricingInput(
            sellerMode = SellerMode.PRIVATE_DECLUTTERING,
            marketResearch = research,
            desiredMinimumPrice = Money(BigDecimal("50.00")),
            shippingCost = Money(BigDecimal("5.90")),
            packagingCost = Money(BigDecimal("1.50"))
        )

        val result = engine.calculate(input)
        // expectedPayout should still be calculated
        assertNotNull(result.expectedPayout)
        assertEquals(ConfidenceLevel.HIGH, result.confidence)
    }

    @Test
    fun `fee breakdown totals are consistent`() = runTest {
        val research = marketResearch(50.0, 70.0, 90.0)
        val result = engine.calculate(input(SellerMode.PRIVATE_DECLUTTERING, research))
        val fb = result.feeBreakdown!!

        // totalCosts = platformFee + shipping + packaging + other
        val expectedTotal = fb.platformFee + fb.shippingCost + fb.packagingCost + fb.otherCosts
        assertEquals(expectedTotal.amount, fb.totalCosts.amount)
    }
}
