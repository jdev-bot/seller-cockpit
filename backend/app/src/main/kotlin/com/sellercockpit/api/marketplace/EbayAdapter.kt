package com.sellercockpit.api.marketplace

import com.sellercockpit.domain.model.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * eBay marketplace adapter — thin delegation layer over [EbayService].
 *
 * Implements the generic [MarketplaceAdapter] contract so the rest of the
 * application can work with marketplace-agnostic interfaces. All heavy
 * lifting (OAuth, inventory+offer API, encryption, token refresh) lives in
 * [EbayService].
 */
@ApplicationScoped
class EbayAdapter @Inject constructor(
    private val ebayService: EbayService
) : MarketplaceAdapter {

    override val platform = MarketplacePlatform.EBAY

    override suspend fun connectAccount(userId: String): MarketplaceConnection {
        // EbayService.connectAccount checks the DB for an existing active
        // connection and throws if none is found.
        return ebayService.connectAccount(userId)
    }

    override suspend fun createDraftListing(
        productCase: ProductCase,
        draft: ListingDraft
    ): MarketplaceListing {
        return ebayService.createDraftListing(productCase, draft)
    }

    override suspend fun publishListing(listingId: String): MarketplaceListing {
        return ebayService.publishListing(listingId)
    }

    override suspend fun updateListing(
        listingId: String,
        update: ListingUpdate
    ): MarketplaceListing {
        // eBay Offer API does not support partial field updates.
        // A full revision requires: withdraw → delete offer → recreate inventory
        // item + offer → publish. This is intentionally not implemented until we
        // have a concrete use-case that justifies the complexity.
        throw NotImplementedError(
            "eBay listing update is not supported via the adapter. " +
            "Use EbayService.endListing + createDraftListing + publishListing instead."
        )
    }

    override suspend fun endListing(
        listingId: String,
        reason: EndListingReason
    ): MarketplaceListing {
        val uuid = UUID.fromString(listingId)
        // EbayService.endListing(UUID, UUID?) handles token lookup via the
        // listing's product case when userId is null.
        return ebayService.endListing(uuid, null)
    }

    override suspend fun syncListingStatus(listingId: String): MarketplaceListingStatus {
        return ebayService.syncListingStatus(listingId)
    }

    override suspend fun estimateFees(request: FeeEstimateRequest): FeeEstimate {
        return ebayService.estimateFees(request)
    }
}
