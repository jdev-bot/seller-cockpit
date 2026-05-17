package com.sellercockpit.api.marketplace

import com.sellercockpit.domain.model.*

interface MarketplaceAdapter {
    val platform: MarketplacePlatform

    suspend fun connectAccount(userId: String): MarketplaceConnection
    suspend fun createDraftListing(productCase: ProductCase, draft: ListingDraft): MarketplaceListing
    suspend fun publishListing(listingId: String): MarketplaceListing
    suspend fun updateListing(listingId: String, update: ListingUpdate): MarketplaceListing
    suspend fun endListing(listingId: String, reason: EndListingReason): MarketplaceListing
    suspend fun syncListingStatus(listingId: String): MarketplaceListingStatus
    suspend fun estimateFees(request: FeeEstimateRequest): FeeEstimate
}

data class MarketplaceConnection(
    val userId: String,
    val platform: MarketplacePlatform,
    val accountId: String,
    val connectedAt: String
)

data class ListingUpdate(
    val title: String? = null,
    val description: String? = null,
    val price: Money? = null
)

data class EndListingReason(
    val reason: String
)

data class FeeEstimateRequest(
    val price: Money,
    val category: String? = null
)

data class FeeEstimate(
    val platformFee: Money,
    val insertionFee: Money? = null,
    val finalValueFee: Money? = null,
    val totalFee: Money
)
