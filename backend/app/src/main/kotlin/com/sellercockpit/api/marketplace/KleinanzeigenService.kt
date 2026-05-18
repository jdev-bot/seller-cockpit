package com.sellercockpit.api.marketplace

import com.sellercockpit.domain.model.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class KleinanzeigenService : MarketplaceAdapter {
    override val platform = MarketplacePlatform.KLEINANZEIGEN

    override suspend fun connectAccount(userId: String): MarketplaceConnection {
        return MarketplaceConnection(
            userId = userId,
            platform = platform,
            accountId = "manual-$userId",
            connectedAt = Instant.now().toString()
        )
    }

    override suspend fun createDraftListing(productCase: ProductCase, draft: ListingDraft): MarketplaceListing {
        return MarketplaceListing(
            id = ListingId(UUID.randomUUID().toString()),
            productCaseId = productCase.id,
            platform = platform,
            status = MarketplaceListingStatus.READY_TO_PUBLISH,
            title = draft.title,
            description = draft.description,
            price = draft.price,
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString()
        )
    }

    override suspend fun publishListing(listingId: String): MarketplaceListing {
        throw IllegalStateException("Kleinanzeigen does not support direct API publishing. Use assisted publishing.")
    }

    override suspend fun updateListing(listingId: String, update: ListingUpdate): MarketplaceListing {
        throw IllegalStateException("Kleinanzeigen updates must be done manually.")
    }

    override suspend fun endListing(listingId: String, reason: EndListingReason): MarketplaceListing {
        throw IllegalStateException("Kleinanzeigen listings must be ended manually.")
    }

    override suspend fun syncListingStatus(listingId: String): MarketplaceListingStatus {
        return MarketplaceListingStatus.ACTIVE
    }

    override suspend fun estimateFees(request: FeeEstimateRequest): FeeEstimate {
        val zero = Money.zero()
        return FeeEstimate(
            insertionFee = zero,
            finalValueFee = zero,
            totalEstimatedFee = zero,
            currency = "EUR"
        )
    }
}
