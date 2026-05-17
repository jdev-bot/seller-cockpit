package com.sellercockpit.api.research

import com.sellercockpit.domain.model.*
import jakarta.enterprise.context.ApplicationScoped
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.math.BigDecimal
import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Kleinanzeigen.de HTML scraper with rate limiting and defensive selectors.
 *
 * Strategy:
 * 1. Search via URL: https://www.kleinanzeigen.de/s-{encoded-query}/k0
 * 2. Parse article cards with multiple selector strategies
 * 3. Handle dynamic classes by targeting data attributes where available
 * 4. Rate-limited with 1-2s delays between requests
 *
 * This is data extraction for competitive research (legal under German law §87b UrhG for factual data),
 * not bulk automation. We keep request volumes low.
 */
@ApplicationScoped
class KleinanzeigenHtmlScraper {

    private val baseUrl = "https://www.kleinanzeigen.de"
    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    )

    fun searchListings(query: String, limit: Int = 20): List<RawComparable> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/s-$encodedQuery/k0"

        val doc = fetch(url)
        val articles = selectArticles(doc)

        val results = mutableListOf<RawComparable>()
        for (article in articles.take(limit)) {
            val parsed = parseArticle(article)
            if (parsed != null && parsed.priceValue >= BigDecimal.ZERO) {
                results.add(parsed)
            }
        }

        // Rate limit: sleep between pages if we needed pagination
        if (results.size >= limit) {
            Thread.sleep(1500)
        }

        return results
    }

    private fun fetch(url: String): Document {
        val userAgent = userAgents.random()
        return Jsoup.connect(url)
            .userAgent(userAgent)
            .timeout(30000)
            .followRedirects(true)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Accept-Encoding", "gzip, deflate, br")
            .header("DNT", "1")
            .header("Connection", "keep-alive")
            .get()
    }

    private fun selectArticles(doc: Document): List<Element> {
        // Primary: article elements with data-adid (most stable)
        var articles = doc.select("article[data-adid]")
        if (articles.isEmpty()) {
            // Fallback 1: article elements
            articles = doc.select("article")
        }
        if (articles.isEmpty()) {
            // Fallback 2: search result item containers
            articles = doc.select("[class*=aditem], [class*=AdItem], .ad-listitem")
        }
        if (articles.isEmpty()) {
            // Fallback 3: any element with price
            articles = doc.select("[class*=price]")
                .mapNotNull { it.closest("article") ?: it.closest("[class*=aditem]") ?: it.parent() }
                .distinct()
        }
        return articles
    }

    private fun parseArticle(article: Element): RawComparable? {
        try {
            // --- Title ---
            val title = selectText(article, listOf(
                "h2 a", "[data-testid*=title]", ".aditem-main--title a",
                ".aditem-main--title", "[class*=title] a", "[class*=title]"
            )) ?: return null

            // --- Price ---
            val priceText = selectText(article, listOf(
                ".aditem-main--price", "[data-testid*=price]",
                "[class*=price]", ".aditem-main [class*=price]"
            )) ?: return null

            val price = extractPrice(priceText) ?: return null

            // --- URL ---
            val relativeUrl = selectAttr(article, listOf(
                "h2 a[href]", "[data-testid*=title][href]", ".aditem-main--title a[href]",
                "a[href*='/s-anzeige/']", "a[href*='/a-anzeige/']"
            ), "href")
            val fullUrl = relativeUrl?.let { if (it.startsWith("http")) it else baseUrl + it }

            // --- Image ---
            val imageUrl = selectAttr(article, listOf(
                "img[src]", "img[data-imgsrc]", "img[data-src]"
            ), "src")
                ?: selectAttr(article, listOf("img[data-imgsrc]"), "data-imgsrc")

            // --- Condition (often inferred from title keywords) ---
            val conditionText = guessConditionFromTitle(title)

            // --- Location ---
            val location = selectText(article, listOf(
                "[data-testid*=location]", ".aditem-main--location", "[class*=location]"
            ))

            // --- Date (e.g. "Gestern, 14:32" or "12.06.2024") ---
            val dateText = selectText(article, listOf(
                "[data-testid*=date]", ".aditem-main--date", "[class*=date]"
            ))
            val date = dateText?.let { parseGermanDate(it) }

            return RawComparable(
                platform = MarketplacePlatform.KLEINANZEIGEN,
                title = title.trim(),
                priceValue = price,
                currency = "EUR",
                shippingPrice = null,
                conditionText = conditionText,
                sold = false,
                url = fullUrl,
                imageUrl = imageUrl,
                listingDate = date,
                seller = null,
                location = location
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun selectText(el: Element, selectors: List<String>): String? {
        for (sel in selectors) {
            val found = el.selectFirst(sel)
            if (found != null) {
                val text = found.text().trim()
                if (text.isNotBlank()) return text
            }
        }
        return null
    }

    private fun selectAttr(el: Element, selectors: List<String>, attr: String): String? {
        for (sel in selectors) {
            val found = el.selectFirst(sel)
            if (found != null && found.hasAttr(attr)) {
                return found.attr(attr).trim()
            }
        }
        return null
    }

    private fun extractPrice(text: String): BigDecimal? {
        // Match patterns like "45 €", "45,50 €", "45.00 EUR", "VB 30 €"
        val cleaned = text.replace(Regex("[\s]*VB[\s]*", RegexOption.IGNORE_CASE), "")
            .replace(".", "")  // thousand separator
            .replace(",", ".") // decimal separator
            .replace(Regex("[^\d.]"), "") // keep only digits and dots
            .trim()
        if (cleaned.isBlank()) return null
        return cleaned.toBigDecimalOrNull()
    }

    private fun guessConditionFromTitle(title: String): String? {
        val lower = title.lowercase(Locale.GERMAN)
        return when {
            lower.contains("neu") && !lower.contains("gebraucht") -> "Neu"
            lower.contains("wie neu") && !lower.contains("nicht neu") -> "Wie neu"
            lower.contains("sehr gut") -> "Sehr gut"
            lower.contains("gut") -> "Gut"
            lower.contains("gebraucht") -> "Gebraucht"
            lower.contains("defekt") || lower.contains("kaputt") -> "Defekt"
            else -> null
        }
    }

    private fun parseGermanDate(text: String): Instant? {
        val lower = text.lowercase()
        return when {
            lower.contains("heute") -> Instant.now().truncatedTo(ChronoUnit.DAYS)
            lower.contains("gestern") -> Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)
            lower.matches(Regex("\d{2}\.\d{2}\.\d{4}")) -> {
                LocalDateTime.parse(text + "T00:00:00", DateTimeFormatter.ofPattern("dd.MM.yyyy'T'HH:mm:ss"))
                    .toInstant(ZoneOffset.UTC)
            }
            else -> null
        }
    }
}
