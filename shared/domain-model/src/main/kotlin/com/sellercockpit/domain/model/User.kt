package com.sellercockpit.domain.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class UserId(val value: String)

@Serializable
@JvmInline
value class ProductCaseId(val value: String)

@Serializable
@JvmInline
value class MediaAssetId(val value: String)

@Serializable
@JvmInline
value class ListingId(val value: String)

@Serializable
@JvmInline
value class ListingDraftId(val value: String)

@Serializable
data class User(
    val id: UserId,
    val email: String,
    val displayName: String? = null,
    val createdAt: String
)
