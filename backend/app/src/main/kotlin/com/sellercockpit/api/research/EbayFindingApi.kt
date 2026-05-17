package com.sellercockpit.api.research

import com.fasterxml.jackson.databind.ObjectMapper
import com.sellercockpit.domain.model.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.client.ClientBuilder
import jakarta.ws.rs.client.WebTarget
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.math.BigDecimal
import java.time.Instant

/**
 * eBay Finding API v1 — official, free read-only search (no OAuth needed).
 * Uses app-id only. Supports active listings + sold items.
 *
 * Getting app-id: https://developer.ebay.com/my/keys
 */
@ApplicationScoped
class EbayFindingApi @Inject constructor(val objectMapper: ObjectMapper) {

    @ConfigProperty(name = "ebay.finding.app-id")
    lateinit var appId: String

    @ConfigProperty(name = "ebay.finding.global-id", defaultValue = "EBAY-DE")
    lateinit var globalId: String

    private val baseUrl = "https://svcs.ebay.com/services/search/FindingService/v1"
    private val client by lazy { ClientBuilder.newClient() }

    fun searchActiveListings(query: String, categoryId: String? = null, limit: Int = 25): List<RawComparable> {
        var target = buildBase("findItemsByKeywords")
            .queryParam("keywords", query)
            .queryParam("paginationInput.entriesPerPage", limit)
        categoryId?.let { target = target.queryParam("categoryId", it) }
        return parse(fetch(target), sold = false)
    }

    fun searchSoldListings(query: String, categoryId: String? = null, limit: Int = 25): List<RawComparable> {
        var target = buildBase("findCompletedItems")
            .queryParam("keywords", query)
            .queryParam("paginationInput.entriesPerPage", limit)
            .queryParam("itemFilter(0).name", "SoldItemsOnly")
            .queryParam("itemFilter(0).value", "true")
        categoryId?.let { target = target.queryParam("categoryId", it) }
        return parse(fetch(target), sold = true)
    }

    private fun buildBase(op: String): WebTarget =
        client.target(baseUrl)
            .queryParam("SECURITY-APPNAME", appId)
            .queryParam("OPERATION-NAME", op)
            .queryParam("SERVICE-VERSION", "1.13.0")
            .queryParam("RESPONSE-DATA-FORMAT", "JSON")
            .queryParam("GLOBAL-ID", globalId)
            .queryParam("outputSelector", "SellerInfo")

    private fun fetch(target: WebTarget): Map<*, *> {
        val raw = target.request()
            .header("X-EBAY-SOA-GLOBAL-ID", globalId)
            .get()
            .readEntity(String::class.java)
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(raw, Map::class.java) as Map<*, *>
    }

    private fun parse(root: Map<*, *>, sold: Boolean): List<RawComparable> {
        val respKey = if (sold) "findCompletedItemsResponse" else "findItemsByKeywordsResponse"
        val resp = (root[respKey] as? List<*>)?.firstOrNull() as? Map<*, *> ?: return emptyList()
        val searchResult = (resp["searchResult"] as? List<*>)?.firstOrNull() as? Map<*, *> ?: return emptyList()
        val items = (searchResult["item"] as? List<*>) ?: return emptyList()

        return items.mapNotBlank { item ->
            try {
                val i = item as Map<*, *>
                val title = (i["title"] as? List<*>)?.firstOrNull() as? String
                    ?: (i["title"] as? String)
                    ?: return@mapNotBlank null

                val sellStatus = (i["sellingStatus"] as? List<*>)?.firstOrNull() as? Map<*, *>
                val currentPrices = sellStatus?.get("currentPrice") as? List<*>
                val priceMap = currentPrices?.firstOrNull() as? Map<*, *>
                    ?: (sellStatus?.get("currentPrice") as? Map<*, *>)
                val priceStr = priceMap?.get("__value__") as? String ?: return@mapNotBlank null
                val price = priceStr.toBigDecimalOrNull() ?: return@mapNotBlank null
                val currency = priceMap["@currencyId"] as? String ?: "EUR"

                val cond = (i["condition"] as? List<*>)?.firstOrNull() as? Map<*, *>
                val condName = (cond?.get("conditionDisplayName") as? List<*>)?.firstOrNull() as? String

                val gallery = i["galleryURL"] as? List<*>
                val imageUrl = gallery?.firstOrNull() as? String

                val viewUrl = (i["viewItemURL"] as? List<*>)?.firstOrNull() as? String
                val location = i["location"] as? String

                val sellerInfo = (i["sellerInfo"] as? List<*>)?.firstOrNull() as? Map<*, *>
                val sellerName = (sellerInfo?.get("sellerUserName") as? List<*>)?.firstOrNull() as? String

                RawComparable(
                    platform = MarketplacePlatform.EBAY,
                    title = title,
                    priceValue = price,
                    currency = currency,
                    shippingPrice = null,
                    conditionText = condName,
                    sold = sold,
                    url = viewUrl,
                    imageUrl = imageUrl,
                    listingDate = null,
                    seller = sellerName,
                    location = location
                )
            } catch (e: Throwable) {
                null
            }
        }
    }
}

private inline fun <T, R> List<T>.mapNotBlank(transform: (T) -> R?): List<R> {
    val result = mutableListOf<R>()
    for (item in this) {
        val mapped = transform(item)
        if (mapped != null) result.add(mapped)
    }
    return result
}
