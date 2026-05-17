package com.sellercockpit.api.resource

import com.sellercockpit.api.model.*
import com.sellercockpit.api.service.ProductCaseService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import java.util.UUID

@Path("/api/dashboard")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class DashboardResource @Inject constructor(
    private val productCaseService: ProductCaseService
) {

    @GET
    fun getDashboard(): DashboardResponse {
        return productCaseService.getDashboard(getCurrentUserId())
    }

    @GET
    @Path("/product-cases/{id}/listings")
    fun getListings(@PathParam("id") productCaseId: UUID): List<MarketplaceListingResponse> {
        return productCaseService.getMarketplaceListings(getCurrentUserId(), productCaseId)
    }

    @PATCH
    @Path("/listings/{listingId}/external-url")
    fun setExternalUrl(@PathParam("listingId") listingId: UUID, request: SetExternalUrlRequest): MarketplaceListingResponse {
        return productCaseService.setExternalUrl(getCurrentUserId(), listingId, request)
    }

    @PATCH
    @Path("/listings/{listingId}/status")
    fun updateStatus(@PathParam("listingId") listingId: UUID, request: UpdateListingStatusRequest): MarketplaceListingResponse {
        return productCaseService.updateListingStatus(getCurrentUserId(), listingId, request)
    }

    private fun getCurrentUserId(): UUID {
        return UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
