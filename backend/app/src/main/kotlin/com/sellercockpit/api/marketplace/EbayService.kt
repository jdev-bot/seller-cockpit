package com.sellercockpit.api.marketplace

import com.sellercockpit.domain.model.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.serialization.json.*
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.Base64

/** eBay inventory + offer API client.
 *
 * eBay flow:
 * 1. User clicks "Connect eBay" → redirect to eBay OAuth consent
 * 2. eBay redirects back with auth code → exchange for tokens → stored encrypted
 * 3. Create inventory item → create offer → publish offer
 * 4. Poll for status via getOffer/getInventoryItem
 */
@ApplicationScoped
class EbayService @Inject constructor(
    @ConfigProperty(name = "ebay.api.client-id", defaultValue = "") private val clientId: String,
    @ConfigProperty(name = "ebay.api.client-secret", defaultValue = "") private val clientSecret: String,
    @ConfigProperty(name = "ebay.api.base-url", defaultValue = "https://api.ebay.com") private val baseUrl: String,
    @ConfigProperty(name = "ebay.auth-url", defaultValue = "https://auth.ebay.com") private val authUrl: String,
    @ConfigProperty(name = "ebay.redirect-uri", defaultValue = "http://localhost:8080/api/marketplaces/ebay/callback") private val redirectUri: String
) : MarketplaceAdapter {

    private val log = Logger.getLogger(javaClass)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    override val platform = MarketplacePlatform.EBAY

    // --- OAuth ---

    fun getOAuthUrl(state: String): String {
        val scopes = "https://api.ebay.com/oauth/api_scope/sell.inventory https://api.ebay.com/oauth/api_scope/sell.account"
        return "$authUrl/oauth2/authorize?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=${scopes.replace(" ", "%20")}&state=$state"
    }

    @Transactional
    fun exchangeCode(userId: UUID, code: String): Boolean {
        val credentials = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
        val body = "grant_type=authorization_code&code=${code}&redirect_uri=$redirectUri"

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$authUrl/oauth2/token"))
            .header("Authorization", "Basic $credentials")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val token = json.decodeFromJsonElement<EbayTokenResponse>(json.parseToJsonElement(response.body()))
                val entity = EbayTokenEntity().apply {
                    id = UUID.randomUUID()
                    this.userId = userId
                    accessToken = token.access_token
                    refreshToken = token.refresh_token
                    expiresAt = Instant.now().plusSeconds(token.expires_in.toLong())
                    createdAt = Instant.now()
                }
                EbayTokenEntity.persist(entity)
                true
            } else {
                log.error("eBay OAuth exchange failed: ${response.statusCode()} ${response.body()}")
                false
            }
        } catch (e: Exception) {
            log.error("eBay OAuth exchange error", e)
            false
        }
    }

    fun getToken(userId: UUID): String? {
        val token = EbayTokenEntity.findValidByUser(userId)
        return token?.accessToken
    }

    // --- MarketplaceAdapter implementation ---

    override suspend fun connectAccount(userId: String): MarketplaceConnection {
        val uuid = UUID.fromString(userId)
        val connection = MarketplaceConnectionEntity.findByUserAndPlatform(uuid, MarketplacePlatform.EBAY)
        return if (connection != null && connection.isActive) {
            MarketplaceConnection(
                userId = userId,
                platform = MarketplacePlatform.EBAY,
                accountId = connection.accountId ?: "unknown",
                connectedAt = connection.createdAt.toString()
            )
        } else {
            throw IllegalStateException("eBay account not connected. Initiate OAuth flow first.")
        }
    }

    override suspend fun createDraftListing(productCase: ProductCase, draft: ListingDraft): MarketplaceListing {
        val token = getToken(UUID.fromString(productCase.userId.value))
            ?: throw IllegalStateException("eBay not connected or token expired")
        val sku = "SC-${productCase.id.value.take(8)}"

        // 1. Create inventory item
        val inventoryBody = buildJsonObject {
            put("product", buildJsonObject {
                put("title", draft.title)
                put("description", draft.description)
                put("aspects", JsonObject(draft.attributes.mapValues { JsonArray(it.value.split(",").map { v -> JsonPrimitive(v) }) }))
                if (draft.imageIds.isNotEmpty()) {
                    putJsonArray("imageUrls") {
                        // These would be MinIO public URLs converted by StorageService
                        draft.imageIds.forEach { add(JsonPrimitive("https://example.com/image/${it.value}")) }
                    }
                }
            })
            put("condition", draft.conditionText.uppercase().replace(" ", "_"))
            put("availability", buildJsonObject { put("shipToLocationAvailability", buildJsonObject { put("quantityAvailable", 1) }) })
            putJsonArray("localizedAspects") {}
        }

        val invRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/sell/inventory/v1/inventory_item/$sku"))
            .header("Authorization", "Bearer $token")
            .header("Content-Language", "en-US")
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(inventoryBody.toString()))
            .build()

        val invResponse = client.send(invRequest, HttpResponse.BodyHandlers.ofString())
        if (invResponse.statusCode() !in listOf(200, 201, 204)) {
            log.error("eBay inventory item creation failed: ${invResponse.statusCode()} ${invResponse.body()}")
            throw IllegalStateException("Failed to create eBay inventory item: ${invResponse.body()}")
        }

        // 2. Create offer
        val offerBody = buildJsonObject {
            put("sku", sku)
            put("marketplaceId", "EBAY_DE")
            put("format", "FIXED_PRICE")
            put("listingDescription", draft.description)
            put("availableQuantity", 1)
            put("quantityLimitPerBuyer", 1)
            putJsonObject("pricingSummary") {
                put("price", buildJsonObject {
                    put("value", draft.price.amount.toString())
                    put("currency", draft.price.currency)
                })
            }
            putJsonObject("listingPolicies") {
                put("paymentPolicyId", "PAYMENT_POLICY_ID") // Would be fetched from seller account
                put("returnPolicyId", "RETURN_POLICY_ID")
                put("fulfillmentPolicyId", "FULFILLMENT_POLICY_ID")
            }
            put("merchantLocationKey", "DEFAULT")
            putJsonArray("categoryId") { add(JsonPrimitive("9355")) } // Electronics placeholder
        }

        val offerRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/sell/inventory/v1/offer"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(offerBody.toString()))
            .build()

        val offerResponse = client.send(offerRequest, HttpResponse.BodyHandlers.ofString())
        if (offerResponse.statusCode() !in listOf(200, 201)) {
            log.error("eBay offer creation failed: ${offerResponse.statusCode()} ${offerResponse.body()}")
            throw IllegalStateException("Failed to create eBay offer: ${offerResponse.body()}")
        }

        val offerResult = json.parseToJsonElement(offerResponse.body()).jsonObject
        val offerId = offerResult["offerId"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("No offerId in eBay response")

        return MarketplaceListing(
            id = ListingId(UUID.randomUUID().toString()),
            productCaseId = productCase.id,
            platform = MarketplacePlatform.EBAY,
            status = MarketplaceListingStatus.DRAFT,
            externalListingId = offerId,
            title = draft.title,
            description = draft.description,
            price = draft.price,
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString()
        )
    }

    override suspend fun publishListing(listingId: String): MarketplaceListing {
        // Call eBay publishOffer API
        val listing = com.sellercockpit.api.model.MarketplaceListingEntity.findById(UUID.fromString(listingId))
            ?: throw IllegalStateException("Listing not found")
        val token = getToken(listing.productCaseId)
            ?: throw IllegalStateException("eBay token expired")

        val offerId = listing.externalListingId
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/sell/inventory/v1/offer/$offerId/publish"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in listOf(200, 201)) {
            throw IllegalStateException("eBay publish failed: ${response.body()}")
        }

        val result = json.parseToJsonElement(response.body()).jsonObject
        val listingUrl = result["listingId"]?.jsonPrimitive?.content?.let { "https://www.ebay.de/itm/$it" }
            ?: "https://www.ebay.de"

        listing.status = MarketplaceListingStatus.PUBLISHED
        listing.externalUrl = listingUrl
        listing.publishedAt = Instant.now()
        listing.updatedAt = Instant.now()

        return MarketplaceListing(
            id = ListingId(listing.id.toString()),
            productCaseId = ProductCaseId(listing.productCaseId.toString()),
            platform = MarketplacePlatform.EBAY,
            status = MarketplaceListingStatus.PUBLISHED,
            externalListingId = offerId,
            externalUrl = listingUrl,
            title = listing.title,
            description = listing.description,
            price = Money(listing.priceAmount, listing.currency),
            createdAt = listing.createdAt.toString(),
            updatedAt = listing.updatedAt.toString()
        )
    }

    override suspend fun updateListing(listingId: String, update: ListingUpdate): MarketplaceListing {
        // Would update via offer API
        throw NotImplementedError("eBay listing update requires offer revision API")
    }

    override suspend fun endListing(listingId: String, reason: EndListingReason): MarketplaceListing {
        // Would withdraw offer via eBay
        throw NotImplementedError("eBay end listing requires offer withdrawal API")
    }

    override suspend fun syncListingStatus(listingId: String): MarketplaceListingStatus {
        // Poll offer status
        return MarketplaceListingStatus.ACTIVE
    }

    override suspend fun estimateFees(request: FeeEstimateRequest): FeeEstimate {
        val insertionFee = Money.zero()
        val finalValueFee = request.price * java.math.BigDecimal("0.11")
        return FeeEstimate(
            platformFee = finalValueFee,
            insertionFee = insertionFee,
            finalValueFee = finalValueFee,
            totalFee = finalValueFee
        )
    }
}

@kotlinx.serialization.Serializable
data class EbayTokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val token_type: String = "Bearer",
    val expires_in: Int,
    val scope: String = ""
)
