package com.sellercockpit.api.marketplace

import com.sellercockpit.domain.model.*
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class EbayAdapter : MarketplaceAdapter {
    override val platform = MarketplacePlatform.EBAY

    override suspend fun connectAccount(userId: String): MarketplaceConnection {
        // TODO: Implement eBay OAuth flow
        return MarketplaceConnection(
            userId = userId,
            platform = platform,
            accountId = "mock-ebay-$userId",
            connectedAt = java.time.Instant.now().toString()
        )
    }

    override suspend fun createDraftListing(productCase: ProductCase, draft: ListingDraft): MarketplaceListing {
        // TODO: Implement eBay Inventory API call
        return MarketplaceListing(
            id = ListingId(java.util.UUID.randomUUID().toString()),
            productCaseId = productCase.id,
            platform = platform,
            status = MarketplaceListingStatus.DRAFT,
            title = draft.title,
            description = draft.description,
            price = draft.price,
            createdAt = java.time.Instant.now().toString(),
            updatedAt = java.time.Instant.now().toString()
        )
    }

    override suspend fun publishListing(listingId: String): MarketplaceListing {
        // TODO: Publish via eBay Offer API
        throw NotImplementedError("eBay publish not yet implemented")
    }

    override suspend fun updateListing(listingId: String, update: ListingUpdate): MarketplaceListing {
        throw NotImplementedError("eBay update not yet implemented")
    }

    override suspend fun endListing(listingId: String, reason: EndListingReason): MarketplaceListing {
        throw NotImplementedError("eBay end listing not yet implemented")
    }

    override suspend fun syncListingStatus(listingId: String): MarketplaceListingStatus {
        return MarketplaceListingStatus.ACTIVE
    }

    override suspend fun estimateFees(request: FeeEstimateRequest): FeeEstimate {
        val fee = request.price * java.math.BigDecimal("0.11")
        val insertion = Money(fee.amount * java.math.BigDecimal("0.05"), fee.currency)
        val finalValue = Money(fee.amount * java.math.BigDecimal("0.06"), fee.currency)
        return FeeEstimate(
            insertionFee = insertion,
            finalValueFee = finalValue,
            totalEstimatedFee = insertion + finalValue,
            currency = fee.currency
        )
    }
}
