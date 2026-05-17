package com.sellercockpit.api.marketplace

import com.sellercockpit.domain.model.*
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class KleinanzeigenAdapter : MarketplaceAdapter {
    override val platform = MarketplacePlatform.KLEINANZEIGEN

    override suspend fun connectAccount(userId: String): MarketplaceConnection {
        // Kleinanzeigen: no official API for publishing; connection is for tracking only
        return MarketplaceConnection(
            userId = userId,
            platform = platform,
            accountId = "manual-$userId",
            connectedAt = java.time.Instant.now().toString()
        )
    }

    override suspend fun createDraftListing(productCase: ProductCase, draft: ListingDraft): MarketplaceListing {
        // Assisted publishing: create local record only
        return MarketplaceListing(
            id = ListingId(java.util.UUID.randomUUID().toString()),
            productCaseId = productCase.id,
            platform = platform,
            status = MarketplaceListingStatus.READY_TO_PUBLISH,
            title = draft.title,
            description = draft.description,
            price = draft.price,
            createdAt = java.time.Instant.now().toString(),
            updatedAt = java.time.Instant.now().toString()
        )
    }

    override suspend fun publishListing(listingId: String): MarketplaceListing {
        // Cannot directly publish; user must post manually
        throw IllegalStateException("Kleinanzeigen does not support direct API publishing. Use assisted publishing flow.")
    }

    override suspend fun updateListing(listingId: String, update: ListingUpdate): MarketplaceListing {
        throw IllegalStateException("Kleinanzeigen updates must be done manually on the platform.")
    }

    override suspend fun endListing(listingId: String, reason: EndListingReason): MarketplaceListing {
        throw IllegalStateException("Kleinanzeigen listings must be ended manually on the platform.")
    }

    override suspend fun syncListingStatus(listingId: String): MarketplaceListingStatus {
        // For assisted listings, status is manually tracked
        return MarketplaceListingStatus.ACTIVE
    }

    override suspend fun estimateFees(request: FeeEstimateRequest): FeeEstimate {
        // Kleinanzeigen private sales typically have no fees
        return FeeEstimate(
            platformFee = Money.zero(),
            totalFee = Money.zero()
        )
    }
}
