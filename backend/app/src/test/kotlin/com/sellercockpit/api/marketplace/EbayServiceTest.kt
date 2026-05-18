package com.sellercockpit.api.marketplace

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@QuarkusTest
class EbayServiceTest {

    @Inject
    lateinit var ebayService: EbayService

    @Test
    fun `fee estimation returns reasonable values for known price`() {
        val fees = ebayService.estimateFees(
            price = java.math.BigDecimal("100.00"),
            category = "CELLPHONES"
        )
        assertNotNull(fees)
        assertTrue(fees.insertionFee.amount.compareTo(java.math.BigDecimal.ZERO) >= 0)
        assertTrue(fees.finalValueFee.amount.compareTo(java.math.BigDecimal("10.00")) >= 0) // ~10% min
        assertTrue(fees.total.amount.compareTo(java.math.BigDecimal("100.00")) <= 0)
    }

    @Test
    fun `fee estimation varies by category`() {
        val electronicsFees = ebayService.estimateFees(
            price = java.math.BigDecimal("500.00"),
            category = "ELECTRONICS"
        )
        val clothingFees = ebayService.estimateFees(
            price = java.math.BigDecimal("500.00"),
            category = "CLOTHING"
        )
        // Electronics typically higher final value fee than clothing
        assertTrue(electronicsFees.finalValueFee.amount >= clothingFees.finalValueFee.amount)
    }

    @Test
    fun `buildEbayPayload returns structured JSON`() {
        val payload = ebayService.buildEbayPayload(
            draft = com.sellercockpit.domain.model.ListingDraft(
                id = com.sellercockpit.domain.model.ListingDraftId("draft-1"),
                productCaseId = com.sellercockpit.domain.model.ProductCaseId("case-1"),
                platform = com.sellercockpit.domain.model.MarketplacePlatform.EBAY,
                title = "Test iPhone",
                description = "Great condition iPhone 13",
                category = "CELLPHONES",
                conditionText = "USED_EXCELLENT",
                price = com.sellercockpit.domain.model.Money(java.math.BigDecimal("499.00"), "EUR"),
                imageIds = emptyList(),
                attributes = emptyMap(),
                warnings = emptyList(),
                readyToPublish = true
            )
        )
        assertTrue(payload.contains("availability"))
        assertTrue(payload.contains("condition"))
        assertTrue(payload.contains("product"))
    }
}
