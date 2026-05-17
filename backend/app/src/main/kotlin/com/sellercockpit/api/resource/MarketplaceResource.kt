package com.sellercockpit.api.resource

import com.sellercockpit.api.marketplace.EbayService
import com.sellercockpit.api.marketplace.KleinanzeigenService
import com.sellercockpit.api.model.*
import com.sellercockpit.api.service.ProductCaseService
import com.sellercockpit.domain.model.*
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.net.URLEncoder
import java.util.UUID

@Path("/api/marketplaces")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class MarketplaceResource @Inject constructor(
    private val productCaseService: ProductCaseService,
    private val ebayService: EbayService,
    private val kleinanzeigenService: KleinanzeigenService
) {

    // --- eBay OAuth ---

    @GET
    @Path("/ebay/connect-url")
    fun getEbayConnectUrl(): Response {
        val state = getCurrentUserId().toString()
        val url = ebayService.getOAuthUrl(state)
        return Response.ok(mapOf("url" to url, "state" to state)).build()
    }

    @GET
    @Path("/ebay/callback")
    fun ebayOAuthCallback(
        @QueryParam("code") code: String?,
        @QueryParam("state") state: String?
    ): Response {
        if (code == null) {
            return Response.status(400).entity(mapOf("error" to "Missing authorization code")).build()
        }
        val userId = UUID.fromString(state ?: getCurrentUserId().toString())
        val success = ebayService.exchangeCode(userId, code)
        return if (success) {
            Response.ok(mapOf("status" to "connected", "platform" to "EBAY")).build()
        } else {
            Response.serverError().entity(mapOf("error" to "Token exchange failed")).build()
        }
    }

    // --- Connections ---

    @GET
    @Path("/connections")
    fun getConnections(): List<ConnectionResponse> {
        // For MVP, derive from token table. In production this should query MarketplaceConnectionEntity.
        val connections = mutableListOf<ConnectionResponse>()
        // eBay
        val ebayToken = com.sellercockpit.api.marketplace.EbayTokenEntity.findValidByUser(getCurrentUserId())
        if (ebayToken != null) {
            connections.add(ConnectionResponse(
                platform = MarketplacePlatform.EBAY,
                connected = true,
                accountId = null,
                connectedAt = ebayToken.createdAt.toString(),
                expiresAt = ebayToken.expiresAt.toString()
            ))
        }
        return connections
    }

    // --- Publishing ---

    @POST
    @Path("/ebay/publish/{draftId}")
    fun publishEbay(@PathParam("draftId") draftId: String): Response {
        // For MVP, delegate through ProductCaseService which handles persistence
        // Direct eBay publishing would go through ebayService
        try {
            val result = productCaseService.publishListing(getCurrentUserId(), UUID.fromString(draftId), PublishRequest(MarketplacePlatform.EBAY))
            return Response.ok(result).build()
        } catch (e: Exception) {
            return Response.serverError().entity(mapOf("error" to e.message)).build()
        }
    }

    @POST
    @Path("/kleinanzeigen/assisted-publish/{draftId}")
    fun assistedPublishKleinanzeigen(@PathParam("draftId") draftId: String): Response {
        return Response.ok(mapOf(
            "platform" to "KLEINANZEIGEN",
            "mode" to "assisted",
            "instructions" to listOf(
                "1. Copy the generated title and description",
                "2. Open kleinanzeigen.de in your browser",
                "3. Create a new ad and paste the content",
                "4. Upload the same images",
                "5. Set the price as recommended",
                "6. Save the live URL back in the app"
            ),
            "draftId" to draftId
        )).build()
    }

    // --- Sync ---

    @POST
    @Path("/ebay/sync/{listingId}")
    fun syncEbay(@PathParam("listingId") listingId: String): Response {
        // Would poll eBay API for status
        return Response.ok(mapOf("status" to "ACTIVE", "listingId" to listingId)).build()
    }

    // --- Fees ---

    @POST
    @Path("/ebay/fees")
    fun estimateEbayFees(request: FeeEstimateRequest): FeeEstimate {
        return ebayService.estimateFees(request)
    }

    private fun getCurrentUserId(): UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
}

data class ConnectionResponse(
    val platform: MarketplacePlatform,
    val connected: Boolean,
    val accountId: String?,
    val connectedAt: String?,
    val expiresAt: String?
)
