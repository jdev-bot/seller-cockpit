package com.sellercockpit.api.marketplace

import com.sellercockpit.domain.model.FeeEstimateRequest
import com.sellercockpit.domain.model.Money
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

@QuarkusTest
class EbayServiceTest {

    @Inject
    lateinit var ebayService: EbayService

    @Test
    fun `fee estimation returns reasonable values for known price`() = runTest {
        val fees = ebayService.estimateFees(
            FeeEstimateRequest(
                price = Money(BigDecimal("100.00")),
                categoryId = "CELLPHONES"
            )
        )
        assertNotNull(fees)
        assertTrue(fees.insertionFee.amount.compareTo(BigDecimal.ZERO) >= 0)
        assertTrue(fees.finalValueFee.amount.compareTo(BigDecimal("10.00")) >= 0) // ~10% min
        assertTrue(fees.totalEstimatedFee.amount.compareTo(BigDecimal("100.00")) <= 0)
    }

    @Test
    fun `fee estimation varies by category`() = runTest {
        val electronicsFees = ebayService.estimateFees(
            FeeEstimateRequest(
                price = Money(BigDecimal("500.00")),
                categoryId = "ELECTRONICS"
            )
        )
        val clothingFees = ebayService.estimateFees(
            FeeEstimateRequest(
                price = Money(BigDecimal("500.00")),
                categoryId = "CLOTHING"
            )
        )
        // Electronics typically higher final value fee than clothing
        assertTrue(electronicsFees.finalValueFee.amount >= clothingFees.finalValueFee.amount)
    }

    @Test
    fun `platform is ebay`() {
        assertEquals(com.sellercockpit.domain.model.MarketplacePlatform.EBAY, ebayService.platform)
    }
}
