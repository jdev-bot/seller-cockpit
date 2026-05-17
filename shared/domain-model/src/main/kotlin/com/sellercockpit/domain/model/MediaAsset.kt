package com.sellercockpit.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaAsset(
    val id: MediaAssetId,
    val productCaseId: ProductCaseId,
    val type: MediaAssetType,
    val storageUrl: String,
    val thumbnailUrl: String? = null,
    val selectedForListing: Boolean = false,
    val sortOrder: Int? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: String
)
