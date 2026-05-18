package com.sellercockpit.api.resource

import com.sellercockpit.api.auth.AuthenticatedUser
import com.sellercockpit.api.marketplace.EbayService
import com.sellercockpit.api.marketplace.KleinanzeigenService
import com.sellercockpit.api.model.*
import com.sellercockpit.api.service.ProductCaseService
import com.sellercockpit.domain.model.*
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.runBlocking

@Path("/api/marketplaces")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class MarketplaceResource @Inject constructor(
    private val productCaseService: ProductCaseService,
    private val ebayService: EbayService,
    private val kleinanzeigenService: KleinanzeigenService
) {

    private fun getCurrentUserId(ctx: SecurityContext): UUID {
        val principal = ctx.userPrincipal as? AuthenticatedUser
            ?: throw IllegalStateException("Not authenticated")
        return principal.toUserId()
    }

    // --- eBay OAuth ---

    @GET
    @Path("/ebay/connect-url")
    fun getEbayConnectUrl(@Context securityContext: SecurityContext): Response {
        val userId = getCurrentUserId(securityContext)
        val state = userId.toString()
        val url = ebayService.getOAuthUrl(state)
        return Response.ok(mapOf("url" to url, "state" to state)).build()
    }

    @GET
    @Path("/ebay/callback")
    fun ebayOAuthCallback(
        @QueryParam("code") code: String?,
        @QueryParam("state") state: String?,
        @QueryParam("error") error: String?
    ): Response {
        if (error != null) {
            return Response.status(400).entity(mapOf("error" to error, "details" to "User denied eBay authorization")).build()
        }
        if (code == null) {
            return Response.status(400).entity(mapOf("error" to "Missing authorization code")).build()
        }
        val userId = try { UUID.fromString(state ?: "") } catch (e: Exception) {
            return Response.status(400).entity(mapOf("error" to "Invalid state parameter")).build()
        }
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
    fun getConnections(@Context securityContext: SecurityContext): List<ConnectionResponse> {
        val userId = try {
            getCurrentUserId(securityContext)
        } catch (e: IllegalStateException) {
            // Return basic connections list for not-yet-authenticated users
            return listOf(
                ConnectionResponse(platform = MarketplacePlatform.EBAY, connected = false, accountId = null, connectedAt = null, expiresAt = null),
                ConnectionResponse(platform = MarketplacePlatform.KLEINANZEIGEN, connected = false, accountId = null, connectedAt = null, expiresAt = null)
            )
        }
        val connections = mutableListOf<ConnectionResponse>()
        // eBay
        val ebayToken = com.sellercockpit.api.marketplace.EbayTokenEntity.findValidByUser(userId)
        if (ebayToken != null) {
            connections.add(ConnectionResponse(
                platform = MarketplacePlatform.EBAY,
                connected = true,
                accountId = null,
                connectedAt = ebayToken.createdAt.toString(),
                expiresAt = ebayToken.expiresAt.toString()
            ))
        }
        // Kleinanzeigen (always supported via assisted, not connected)
        connections.add(ConnectionResponse(
            platform = MarketplacePlatform.KLEINANZEIGEN,
            connected = false,
            accountId = null,
            connectedAt = null,
            expiresAt = null
        ))
        return connections
    }

    @DELETE
    @Path("/ebay/disconnect")
    fun disconnectEbay(@Context securityContext: SecurityContext): Response {
        val userId = getCurrentUserId(securityContext)
        ebayService.revokeConnection(userId)
        return Response.ok(mapOf("status" to "disconnected", "platform" to "EBAY")).build()
    }

    // --- Publishing ---

    @POST
    @Path("/ebay/publish/{draftId}")
    fun publishEbay(
        @PathParam("draftId") draftId: String,
        @Context securityContext: SecurityContext
    ): Response {
        val userId = getCurrentUserId(securityContext)
        try {
            val result = productCaseService.publishListing(userId, UUID.fromString(draftId), PublishRequest(MarketplacePlatform.EBAY))
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
    fun syncEbay(@PathParam("listingId") listingId: String, @Context securityContext: SecurityContext): Response {
        val userId = getCurrentUserId(securityContext)
        val result = ebayService.syncListingStatus(UUID.fromString(listingId), userId)
        return Response.ok(mapOf("status" to result.name, "listingId" to listingId)).build()
    }

    @POST
    @Path("/ebay/end/{listingId}")
    fun endEbay(@PathParam("listingId") listingId: String, @Context securityContext: SecurityContext): Response {
        val userId = getCurrentUserId(securityContext)
        val result = ebayService.endListing(UUID.fromString(listingId), userId)
        return Response.ok(result).build()
    }

    // --- Fees ---

    @POST
    @Path("/ebay/fees")
    fun estimateEbayFees(request: FeeEstimateRequest, @Context securityContext: SecurityContext): FeeEstimate {
        return runBlocking { ebayService.estimateFees(request) }
    }
}

data class ConnectionResponse(
    val platform: MarketplacePlatform,
    val connected: Boolean,
    val accountId: String?,
    val connectedAt: String?,
    val expiresAt: String?
)
