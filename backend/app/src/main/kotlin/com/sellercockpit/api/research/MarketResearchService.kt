package com.sellercockpit.api.research

import com.sellercockpit.domain.model.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Unified marketplace research service.
 *
 * Orchestrates eBay Finding API + Kleinanzeigen scraper,
 * normalizes results, scores relevance, rejects outliers,
 * and produces a transparent MarketResearchResult.
 */
@ApplicationScoped
class MarketResearchService @Inject constructor(
    private val ebayFindingApi: EbayFindingApi,
    private val kleinanzeigenScraper: KleinanzeigenHtmlScraper
) {

    private val log = Logger.getLogger(MarketResearchService::class.java)

    @ConfigProperty(name = "research.ebay.enabled", defaultValue = "true")
    var ebayEnabled: Boolean = true

    @ConfigProperty(name = "research.kleinanzeigen.enabled", defaultValue = "true")
    var kleinanzeigenEnabled: Boolean = true

    @ConfigProperty(name = "research.max-results-per-source", defaultValue = "25")
    var maxResults: Int = 25

    @ConfigProperty(name = "research.outlier-z-threshold", defaultValue = "3.0")
    var outlierThreshold: Double = 3.0

    @ConfigProperty(name = "research.min-confidence", defaultValue = "LOW")
    lateinit var minConfidence: String

    /**
     * Run research for a product case. Returns a deterministic result.
     */
    suspend fun research(productCase: ProductCase): MarketResearchResult = withContext(Dispatchers.IO) {
        val productFacts = productCase.productFacts
            ?: return@withContext MarketResearchResult(
                comparables = emptyList(),
                estimatedMarketLow = Money(BigDecimal.ZERO),
                estimatedMarketMid = Money(BigDecimal.ZERO),
                estimatedMarketHigh = Money(BigDecimal.ZERO),
                confidence = ConfidenceLevel.LOW,
                summary = "No product facts available. Run AI processing first.",
                warnings = listOf("Product facts missing")
            )

        val queries = buildSearchQueries(productFacts)
        log.info("Researching product case ${productCase.id} with queries: $queries")

        // Parallel fetch
        val ebayActive = if (ebayEnabled) async { queries.flatMap { ebayFindingApi.searchActiveListings(it, limit = maxResults) } } else null
        val ebaySold = if (ebayEnabled) async { queries.flatMap { ebayFindingApi.searchSoldListings(it, limit = maxResults / 2) } } else null
        val kzListings = if (kleinanzeigenEnabled) async { queries.flatMap { kleinanzeigenScraper.searchListings(it, limit = maxResults) } } else null

        val raw = mutableListOf<RawComparable>()
        ebayActive?.await()?.let { raw.addAll(it) }
        ebaySold?.await()?.let { raw.addAll(it) }
        kzListings?.await()?.let { raw.addAll(it) }

        if (raw.isEmpty()) {
            return@withContext MarketResearchResult(
                comparables = emptyList(),
                estimatedMarketLow = Money(BigDecimal.ZERO),
                estimatedMarketMid = Money(BigDecimal.ZERO),
                estimatedMarketHigh = Money(BigDecimal.ZERO),
                confidence = ConfidenceLevel.LOW,
                summary = "No comparable listings found for '${productFacts.title}'.",
                warnings = listOf("Zero results from all sources")
            )
        }

        // Normalize and score
        val scored = raw.map { normalizeAndScore(it, productFacts) }
            .sortedByDescending { it.relevanceScore }

        // Outlier rejection (modified Z-score using median absolute deviation)
        val prices = scored.map { it.price.amount }
        val filtered = rejectOutliers(scored)

        val warnings = mutableListOf<String>()
        if (ebayEnabled && ebayActive != null && raw.none { it.platform == MarketplacePlatform.EBAY && it.sold }) {
            warnings.add("No sold eBay listings found — active listings used as proxy")
        }
        if (kleinanzeigenEnabled && kzListings != null && raw.none { it.platform == MarketplacePlatform.KLEINANZEIGEN }) {
            warnings.add("Kleinanzeigen results empty — may be blocked or no matches")
        }
        if (filtered.size < scored.size) {
            warnings.add("${scored.size - filtered.size} outliers removed from ${scored.size} listings")
        }

        // Compute bands
        val marketRange = computeMarketBands(filtered.map { it.price.amount })
        val confidence = computeConfidence(filtered.size, productFacts.confidence, warnings)

        MarketResearchResult(
            comparables = filtered,
            estimatedMarketLow = marketRange?.first ?: Money(BigDecimal.ZERO),
            estimatedMarketMid = marketRange?.second ?: Money(BigDecimal.ZERO),
            estimatedMarketHigh = marketRange?.third ?: Money(BigDecimal.ZERO),
            confidence = confidence,
            summary = buildSummary(productFacts.title, marketRange, filtered, confidence),
            warnings = warnings
        )
    }

    /** Build search queries from product facts. */
    private fun buildSearchQueries(facts: ProductFacts): List<String> {
        val queries = mutableListOf<String>()
        val brand = facts.brand
        val model = facts.model
        val variant = facts.variant

        // Best query: brand + model + variant
        if (!brand.isNullOrBlank() && !model.isNullOrBlank()) {
            val q = buildString {
                append(brand)
                append(" ")
                append(model)
                if (!variant.isNullOrBlank()) {
                    append(" ")
                    append(variant)
                }
            }
            queries.add(q)
        }
        // Fallback: title-only query (cleaned)
        val cleanedTitle = facts.title
            .replace(Regex("\b(\d+[GgBb])\b"), "")      // remove storage specs for broader match
            .replace(Regex("\s+"), " ")
            .trim()
        if (cleanedTitle.isNotBlank() && cleanedTitle != queries.firstOrNull()) {
            queries.add(cleanedTitle)
        }
        // Broad query: brand + model without variant
        if (!brand.isNullOrBlank() && !model.isNullOrBlank()) {
            val broad = "$brand $model"
            if (broad != queries.firstOrNull() && broad != cleanedTitle) {
                queries.add(broad)
            }
        }
        return queries.distinct().take(3)
    }

    /** Normalize condition text and score relevance. */
    private fun normalizeAndScore(raw: RawComparable, facts: ProductFacts): MarketComparable {
        val normalizedCondition = normalizeCondition(raw.conditionText)
        val targetCondition = ProductCondition.valueOf(facts.condition.name)

        val brandMatch = raw.title.contains(facts.brand ?: "", ignoreCase = true)
        val modelMatch = raw.title.contains(facts.model ?: "", ignoreCase = true)
        val variantMatch = facts.variant?.let { raw.title.contains(it, ignoreCase = true) } ?: false
        val conditionMatch = normalizedCondition == targetCondition
        val conditionNear = normalizedCondition.ordinal.let {
            Math.abs(it - targetCondition.ordinal) <= 1
        }

        val relevance = calculateRelevance(
            brandMatch = brandMatch,
            modelMatch = modelMatch,
            variantMatch = variantMatch,
            conditionMatch = conditionMatch,
            conditionNear = conditionNear,
            sold = raw.sold,
            platform = raw.platform
        )

        return MarketComparable(
            id = generateId(raw),
            platform = raw.platform,
            title = raw.title,
            price = Money(raw.priceValue),
            shippingPrice = raw.shippingPrice?.let { Money(it) },
            condition = normalizedCondition,
            listingType = ListingType.FIXED_PRICE,
            sold = raw.sold,
            soldAt = null,
            url = raw.url,
            imageUrl = raw.imageUrl,
            relevanceScore = relevance,
            notes = raw.location?.let { "Location: $it" }
        )
    }

    private fun calculateRelevance(
        brandMatch: Boolean,
        modelMatch: Boolean,
        variantMatch: Boolean,
        conditionMatch: Boolean,
        conditionNear: Boolean,
        sold: Boolean,
        platform: MarketplacePlatform
    ): Double {
        var score = 0.0
        if (brandMatch) score += 0.25
        if (modelMatch) score += 0.30
        if (variantMatch) score += 0.15
        if (conditionMatch) score += 0.15
        else if (conditionNear) score += 0.05
        if (sold) score += 0.10          // sold = price was accepted by market
        if (platform == MarketplacePlatform.EBAY) score += 0.05  // higher data quality
        return score.coerceAtMost(1.0)
    }

    /** Modified Z-score using median absolute deviation. Robust to small datasets. */
    private fun rejectOutliers(listings: List<MarketComparable>): List<MarketComparable> {
        if (listings.size < 5) return listings  // not enough data

        val values = listings.map { it.price.amount }
        val sorted = values.sorted()
        val median = sorted[values.size / 2]

        val deviations = values.map { (it - median).abs() }
        val mad = deviations.sorted().let { it[it.size / 2] }
        if (mad.compareTo(BigDecimal.ZERO) == 0) return listings

        val madn = mad / BigDecimal("1.4826")
        val threshold = BigDecimal.valueOf(outlierThreshold)

        return listings.filter { listing ->
            val z = (listing.price.amount - median).abs()
            val mz = z / madn
            mz <= threshold || listing.sold == true  // keep sold items even if outliers (rare items)
        }
    }

    /** Compute low/mid/high bands using quantiles. */
    private fun computeMarketBands(prices: List<BigDecimal>): Triple<Money, Money, Money>? {
        if (prices.isEmpty()) return null
        val sorted = prices.sorted()
        return Triple(
            Money(quantile(sorted, 0.10)),
            Money(quantile(sorted, 0.50)),
            Money(quantile(sorted, 0.90))
        )
    }

    private fun quantile(sorted: List<BigDecimal>, q: Double): BigDecimal {
        if (sorted.isEmpty()) return BigDecimal.ZERO
        val idx = (q * (sorted.size - 1)).toInt()
        return sorted[idx]
    }

    private fun computeConfidence(count: Int, productConfidence: Double, warnings: List<String>): ConfidenceLevel {
        return when {
            count <= 2 -> ConfidenceLevel.LOW
            count <= 5 && warnings.isEmpty() -> ConfidenceLevel.LOW
            count >= 10 && productConfidence >= 0.8 && warnings.isEmpty() -> ConfidenceLevel.HIGH
            count >= 5 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
    }

    private fun buildSummary(title: String, range: Triple<Money, Money, Money>?, results: List<MarketComparable>, confidence: ConfidenceLevel): String {
        if (range == null) return "No market data found for this item."
        val ebayCount = results.count { it.platform == MarketplacePlatform.EBAY }
        val kzCount = results.count { it.platform == MarketplacePlatform.KLEINANZEIGEN }
        val soldCount = results.count { it.sold == true }

        return buildString {
            append("Research on '"$title"')")
            append(" found ${results.size} comparable listing${if (results.size != 1) "s" else ""}")
            append(" ($ebayCount eBay, $kzCount Kleinanzeigen")
            if (soldCount > 0) append(", $soldCount sold")
            append("). ")
            append("Mid-market estimate: ${range.second.amount.toPlainString()} EUR")
            append(" (range ${range.first.amount.toPlainString()}–${range.third.amount.toPlainString()} EUR).")
            append(" Confidence: ${confidence.name.lowercase()}, replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}.")
        }
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

    private fun generateId(raw: RawComparable): String =
        java.util.Base64.getEncoder().encodeToString("${raw.platform}_${raw.title}_${raw.priceValue}".toByteArray())
}

private fun BigDecimal.abs(): BigDecimal = if (this < BigDecimal.ZERO) this.negate() else this
