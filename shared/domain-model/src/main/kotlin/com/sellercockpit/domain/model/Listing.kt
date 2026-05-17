package com.sellercockpit.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ListingDraft(
    val id: ListingDraftId,
    val productCaseId: ProductCaseId,
    val platform: MarketplacePlatform,
    val title: String,
    val description: String,
    val category: String? = null,
    val conditionText: String,
    val price: Money,
    val imageIds: List<MediaAssetId> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
    val warnings: List<String> = emptyList(),
    val readyToPublish: Boolean = false
)

@Serializable
data class MarketplaceListing(
    val id: ListingId,
    val productCaseId: ProductCaseId,
    val platform: MarketplacePlatform,
    val status: MarketplaceListingStatus,
    val externalListingId: String? = null,
    val externalUrl: String? = null,
    val title: String,
    val description: String,
    val price: Money,
    val currency: String = "EUR",
    val publishedAt: String? = null,
    val lastSyncedAt: String? = null,
    val createdAt: String,
    val updatedAt: String
)
