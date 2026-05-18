package com.sellercockpit.api.marketplace

import com.sellercockpit.domain.model.*
import com.sellercockpit.api.model.MarketplaceConnectionEntity
import com.sellercockpit.api.model.MarketplaceListingEntity
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
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * eBay inventory + offer API client with token encryption.
 *
 * eBay flow:
 * 1. User clicks "Connect eBay" -> redirect to eBay OAuth consent
 * 2. eBay redirects back with auth code -> exchange for tokens -> stored encrypted
 * 3. Create inventory item -> create offer -> publish offer
 * 4. Poll for status via getOffer/getInventoryItem
 */
@ApplicationScoped
class EbayService @Inject constructor(
    @ConfigProperty(name = "ebay.api.client-id", defaultValue = "") private val clientId: String,
    @ConfigProperty(name = "ebay.api.client-secret", defaultValue = "") private val clientSecret: String,
    @ConfigProperty(name = "ebay.api.base-url", defaultValue = "https://api.ebay.com") private val baseUrl: String,
    @ConfigProperty(name = "ebay.auth-url", defaultValue = "https://auth.ebay.com") private val authUrl: String,
    @ConfigProperty(name = "ebay.redirect-uri", defaultValue = "http://localhost:8080/api/marketplaces/ebay/callback") private val redirectUri: String,
    @ConfigProperty(name = "security.encryption-key", defaultValue = "") private val encryptionKey: String
) : MarketplaceAdapter {

    private val log = Logger.getLogger(javaClass)
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val tokenCrypto by lazy { TokenCrypto(encryptionKey) }

    // --- OAuth ---

    fun getOAuthUrl(state: String): String {
        val scopes = "https://api.ebay.com/oauth/api_scope/sell.inventory https://api.ebay.com/oauth/api_scope/sell.account"
        return "$authUrl/oauth2/authorize?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=${scopes.replace(" ", "%20")}&state=$state"
    }

    @Transactional
    fun exchangeCode(userId: UUID, code: String): Boolean {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            log.error("eBay client credentials not configured. Set EBAY_CLIENT_ID and EBAY_CLIENT_SECRET.")
            return false
        }
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
                // Encrypt tokens before storage
                val encryptedAccess = tokenCrypto.encrypt(token.access_token)
                val encryptedRefresh = token.refresh_token?.let { tokenCrypto.encrypt(it) }

                // Remove any existing tokens for this user
                EbayTokenEntity.find("userId", userId).list().forEach { it.delete() }

                val entity = EbayTokenEntity().apply {
                    id = UUID.randomUUID()
                    this.userId = userId
                    accessToken = encryptedAccess
                    refreshToken = encryptedRefresh
                    expiresAt = Instant.now().plusSeconds(token.expires_in.toLong())
                    createdAt = Instant.now()
                }
                EbayTokenEntity.persist(entity)

                // Upsert MarketplaceConnectionEntity
                val conn = MarketplaceConnectionEntity.findByUserAndPlatform(userId, MarketplacePlatform.EBAY)
                if (conn == null) {
                    MarketplaceConnectionEntity().apply {
                        id = UUID.randomUUID()
                        this.userId = userId
                        this.platform = MarketplacePlatform.EBAY
                        this.accountId = "ebay-$userId"
                        this.isActive = true
                        this.createdAt = Instant.now()
                        this.updatedAt = Instant.now()
                    }.persist()
                } else {
                    conn.isActive = true
                    conn.updatedAt = Instant.now()
                }
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

    @Transactional
    fun revokeConnection(userId: UUID) {
        EbayTokenEntity.find("userId", userId).list().forEach { it.delete() }
        val conn = MarketplaceConnectionEntity.findByUserAndPlatform(userId, MarketplacePlatform.EBAY)
        if (conn != null) {
            conn.isActive = false
            conn.updatedAt = Instant.now()
        }
    }

    fun getToken(userId: UUID): String? {
        val token = EbayTokenEntity.findValidByUser(userId) ?: return null
        val decrypted = try { tokenCrypto.decrypt(token.accessToken) } catch (e: Exception) {
            log.error("Failed to decrypt eBay access token", e)
            return null
        }
        return decrypted
    }

    fun getRefreshToken(userId: UUID): String? {
        val token = EbayTokenEntity.findValidByUser(userId) ?: return null
        return token.refreshToken?.let {
            try { tokenCrypto.decrypt(it) } catch (e: Exception) { null }
        }
    }

    @Transactional
    fun refreshToken(userId: UUID): Boolean {
        val current = EbayTokenEntity.findValidByUser(userId) ?: return false
        val refresh = current.refreshToken?.let {
            try { tokenCrypto.decrypt(it) } catch (e: Exception) { null }
        } ?: return false

        val credentials = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
        val body = "grant_type=refresh_token&refresh_token=${refresh}"

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
                current.accessToken = tokenCrypto.encrypt(token.access_token)
                if (token.refresh_token != null) {
                    current.refreshToken = tokenCrypto.encrypt(token.refresh_token)
                }
                current.expiresAt = Instant.now().plusSeconds(token.expires_in.toLong())
                true
            } else {
                log.error("eBay token refresh failed: ${response.statusCode()}")
                false
            }
        } catch (e: Exception) {
            log.error("eBay token refresh error", e)
            false
        }
    }

    // --- MarketplaceAdapter implementation ---

    override val platform = MarketplacePlatform.EBAY

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
                put("paymentPolicyId", "PAYMENT_POLICY_ID")
                put("returnPolicyId", "RETURN_POLICY_ID")
                put("fulfillmentPolicyId", "FULFILLMENT_POLICY_ID")
            }
            put("merchantLocationKey", "DEFAULT")
            putJsonArray("categoryId") { add(JsonPrimitive("9355")) }
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
        val listing = MarketplaceListingEntity.findById(UUID.fromString(listingId))
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
        throw NotImplementedError("eBay listing update requires offer revision API")
    }

    override suspend fun endListing(listingId: String, reason: EndListingReason): MarketplaceListing {
        return endListing(UUID.fromString(listingId), null)
    }

    fun endListing(listingId: UUID, userId: UUID?): MarketplaceListing {
        val entity = MarketplaceListingEntity.findById(listingId)
            ?: throw IllegalStateException("Listing not found")
        val uid = userId ?: entity.productCaseId
        val token = getToken(uid)
            ?: throw IllegalStateException("eBay token expired")

        val offerId = entity.externalListingId
            ?: throw IllegalStateException("No external offer ID")

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/sell/inventory/v1/offer/$offerId/withdraw"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in listOf(200, 201, 204)) {
            throw IllegalStateException("eBay end listing failed: ${response.body()}")
        }

        entity.status = MarketplaceListingStatus.REMOVED
        entity.updatedAt = Instant.now()

        return MarketplaceListing(
            id = ListingId(entity.id.toString()),
            productCaseId = ProductCaseId(entity.productCaseId.toString()),
            platform = MarketplacePlatform.EBAY,
            status = MarketplaceListingStatus.REMOVED,
            externalListingId = offerId,
            externalUrl = entity.externalUrl,
            title = entity.title,
            description = entity.description,
            price = Money(entity.priceAmount, entity.currency),
            createdAt = entity.createdAt.toString(),
            updatedAt = entity.updatedAt.toString()
        )
    }

    override suspend fun syncListingStatus(listingId: String): MarketplaceListingStatus {
        val uuid = UUID.fromString(listingId)
        val entity = MarketplaceListingEntity.findById(uuid) ?: return MarketplaceListingStatus.FAILED
        val token = getToken(entity.productCaseId) ?: return entity.status
        return syncListingStatusInternal(entity, token)
    }

    private fun syncListingStatusInternal(entity: MarketplaceListingEntity, token: String): MarketplaceListingStatus {
        val offerId = entity.externalListingId ?: return entity.status
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/sell/inventory/v1/offer/$offerId"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val obj = json.parseToJsonElement(response.body()).jsonObject
                val status = obj["status"]?.jsonPrimitive?.content
                val mapped = when (status?.uppercase()) {
                    "PUBLISHED" -> MarketplaceListingStatus.PUBLISHED
                    "ACTIVE" -> MarketplaceListingStatus.ACTIVE
                    "ENDED" -> MarketplaceListingStatus.REMOVED
                    "UNPUBLISHED" -> MarketplaceListingStatus.DRAFT
                    else -> entity.status
                }
                entity.status = mapped
                entity.lastSyncedAt = Instant.now()
                entity.updatedAt = Instant.now()
                mapped
            } else {
                entity.status
            }
        } catch (e: Exception) {
            log.error("eBay sync error for listing ${entity.id}", e)
            entity.status
        }
    }

    fun syncListingStatus(listingId: UUID, userId: UUID): MarketplaceListingStatus {
        val entity = MarketplaceListingEntity.findById(listingId) ?: return MarketplaceListingStatus.FAILED
        val token = getToken(userId) ?: return entity.status

        val offerId = entity.externalListingId ?: return entity.status

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/sell/inventory/v1/offer/$offerId"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()

        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val obj = json.parseToJsonElement(response.body()).jsonObject
                val status = obj["status"]?.jsonPrimitive?.content
                val mapped = when (status?.uppercase()) {
                    "PUBLISHED" -> MarketplaceListingStatus.PUBLISHED
                    "ACTIVE" -> MarketplaceListingStatus.ACTIVE
                    "ENDED" -> MarketplaceListingStatus.REMOVED
                    "UNPUBLISHED" -> MarketplaceListingStatus.DRAFT
                    else -> entity.status
                }
                entity.status = mapped
                entity.lastSyncedAt = Instant.now()
                entity.updatedAt = Instant.now()
                mapped
            } else {
                entity.status
            }
        } catch (e: Exception) {
            log.error("eBay sync error for listing $listingId", e)
            entity.status
        }
    }

    override suspend fun estimateFees(request: FeeEstimateRequest): FeeEstimate {
        val fee = request.price * java.math.BigDecimal("0.11")
        val insertionFee = Money(java.math.BigDecimal.ZERO, request.price.currency)
        val finalValueFee = Money(fee.amount, request.price.currency)
        return FeeEstimate(
            insertionFee = insertionFee,
            finalValueFee = finalValueFee,
            featuredFee = null,
            totalEstimatedFee = finalValueFee,
            currency = request.price.currency
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

/** Simple AES-GCM symmetric encryption for tokens.
 *  IMPORTANT: Use a strong, unique encryption key from environment variable.
 *  If key is missing, tokens are stored plaintext (development mode only).
 */
class TokenCrypto(private val keyHex: String) {
    private val ALGORITHM = "AES/GCM/NoPadding"
    private val IV_SIZE = 12
    private val TAG_SIZE = 128

    private val cipher by lazy { Cipher.getInstance(ALGORITHM) }

    fun encrypt(plaintext: String): String {
        if (keyHex.isBlank()) return "PLAIN:$plaintext"
        val key = SecretKeySpec(hexToBytes(keyHex), "AES")
        val iv = ByteArray(IV_SIZE).apply { java.security.SecureRandom().nextBytes(this) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(ciphertext)
    }

    fun decrypt(ciphertext: String): String {
        if (ciphertext.startsWith("PLAIN:")) return ciphertext.removePrefix("PLAIN:")
        val parts = ciphertext.split(":")
        if (parts.size != 2) throw IllegalArgumentException("Invalid encrypted token format")
        val iv = Base64.getDecoder().decode(parts[0])
        val encrypted = Base64.getDecoder().decode(parts[1])
        val key = SecretKeySpec(hexToBytes(keyHex), "AES")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
