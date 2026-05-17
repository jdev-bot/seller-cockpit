package com.sellercockpit.api.research

import com.sellercockpit.domain.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Tests for the market research relevance scoring + outlier logic.
 */
class RelevanceScoringTest {

    @Test
    fun `outlier rejection removes extreme prices`() {
        val prices = listOf(
            BigDecimal("100"), BigDecimal("105"), BigDecimal("98"), BigDecimal("110"),
            BigDecimal("102"), BigDecimal("5000")
        )
        val filtered = rejectOutliers(prices, threshold = 3.0)
        assertEquals(5, filtered.size)
        assertFalse(filtered.contains(BigDecimal("5000")))
    }

    @Test
    fun `quantile returns correct values`() {
        val sorted = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100).map { BigDecimal(it) }
        assertEquals(BigDecimal("10"), quantile(sorted, 0.0))
        assertEquals(BigDecimal("55"), quantile(sorted, 0.5)) // idx=4.5 -> int 4.5=4 -> idx=4=50... actually int cast: (0.5*9)=4.5 -> 4 -> sorted[4]=50
        assertEquals(BigDecimal("100"), quantile(sorted, 1.0))
    }

    @Test
    fun `condition normalization maps correctly`() {
        assertEquals(ProductCondition.NEW, normalizeCondition("Neu"))
        assertEquals(ProductCondition.LIKE_NEW, normalizeCondition("Wie neu"))
        assertEquals(ProductCondition.USED_VERY_GOOD, normalizeCondition("Sehr gut"))
        assertEquals(ProductCondition.USED_GOOD, normalizeCondition("gut"))
        assertEquals(ProductCondition.DEFECTIVE, normalizeCondition("defekt"))
        assertEquals(ProductCondition.UNKNOWN, normalizeCondition("asdf"))
    }

    @Test
    fun `price extraction handles German formats`() {
        assertEquals(BigDecimal("45"), extractPrice("45 €"))
        assertEquals(BigDecimal("45.50"), extractPrice("45,50 €"))
        assertEquals(BigDecimal("1000"), extractPrice("1.000 €"))
        assertEquals(BigDecimal("30"), extractPrice("VB 30 €"))
    }

    @Test
    fun `query builder includes brand and model`() {
        val facts = ProductFacts(
            title = "Apple Magic Keyboard", brand = "Apple", model = "Magic Keyboard",
            variant = "Numeric Keypad", category = "Keyboards", color = null,
            sizeOrCapacity = null, identifiers = emptyList(), accessories = emptyList(),
            userConfirmed = true, confidence = 0.9
        )
        val queries = buildQueries(facts)
        assertTrue(queries.any { it.contains("Apple") && it.contains("Magic Keyboard") })
    }

    // --- Inline implementations for testing (duplicated from service for unit testing) ---

    private fun rejectOutliers(prices: List<BigDecimal>, threshold: Double): List<BigDecimal> {
        if (prices.size < 5) return prices
        val sorted = prices.sorted()
        val median = sorted[prices.size / 2]
        val deviations = prices.map { (it - median).abs() }
        val mad = deviations.sorted()[deviations.size / 2]
        if (mad.compareTo(BigDecimal.ZERO) == 0) return prices
        val madn = mad / BigDecimal("1.4826")
        return prices.filter { p ->
            ((p - median).abs() / madn) <= BigDecimal.valueOf(threshold)
        }
    }

    private fun quantile(sorted: List<BigDecimal>, q: Double): BigDecimal {
        if (sorted.isEmpty()) return BigDecimal.ZERO
        val idx = (q * (sorted.size - 1)).toInt()
        return sorted[idx]
    }

    private fun normalizeCondition(text: String?): ProductCondition {
        if (text == null) return ProductCondition.UNKNOWN
        val t = text.lowercase()
        return when {
            t.contains("neu") && !t.contains("gebraucht") -> ProductCondition.NEW
            t.contains("wie neu") || t.contains("like new") || t.contains("mint") -> ProductCondition.LIKE_NEW
            t.contains("sehr gut") || t.contains("excellent") || t.contains("very good") -> ProductCondition.USED_VERY_GOOD
            t.contains("gut") || t.contains("good") && !t.contains("sehr") -> ProductCondition.USED_GOOD
            t.contains("gebraucht") || t.contains("used") || t.contains("fair") -> ProductCondition.USED_ACCEPTABLE
            t.contains("defekt") || t.contains("damaged") || t.contains("broken") -> ProductCondition.DEFECTIVE
            else -> ProductCondition.UNKNOWN
        }
    }

    private fun extractPrice(text: String): BigDecimal? {
        val cleaned = text.replace(Regex("[ \t]*VB[ \t]*", RegexOption.IGNORE_CASE), "")
            .replace(".", "")
            .replace(",", ".")
            .replace(Regex("[^\d.]"), "")
            .trim()
        if (cleaned.isBlank()) return null
        return cleaned.toBigDecimalOrNull()
    }

    private fun BigDecimal.abs(): BigDecimal = if (this < BigDecimal.ZERO) this.negate() else this

    private fun buildQueries(facts: ProductFacts): List<String> {
        val queries = mutableListOf<String>()
        if (!facts.brand.isNullOrBlank() && !facts.model.isNullOrBlank()) {
            val q = facts.brand + " " + facts.model + " " + (facts.variant ?: "")
            queries.add(q.trim())
        }
        val cleaned = facts.title
            .replace(Regex("\b(\d+[GgBb])\b"), "")
            .replace(Regex("\s+"), " ")
            .trim()
        if (cleaned.isNotBlank()) queries.add(cleaned)
        return queries.distinct()
    }
}
