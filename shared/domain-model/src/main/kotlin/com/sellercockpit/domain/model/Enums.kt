package com.sellercockpit.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class SellerMode {
    PRIVATE_DECLUTTERING,
    PRIVATE_RESELLING,
    PROFESSIONAL
}

@Serializable
enum class ProductCaseStatus {
    CAPTURED,
    PROCESSING_MEDIA,
    NEEDS_USER_INFO,
    READY_FOR_RESEARCH,
    RESEARCHING,
    PRICED,
    LISTING_READY,
    PARTIALLY_PUBLISHED,
    PUBLISHED,
    SOLD,
    ARCHIVED,
    FAILED
}

@Serializable
enum class MarketplacePlatform {
    EBAY,
    KLEINANZEIGEN
}

@Serializable
enum class MarketplaceListingStatus {
    DRAFT,
    READY_TO_PUBLISH,
    PUBLISHING,
    PUBLISHED,
    ACTIVE,
    NEEDS_ATTENTION,
    RESERVED,
    SOLD,
    EXPIRED,
    REMOVED,
    FAILED
}

@Serializable
enum class MediaAssetType {
    ORIGINAL_VIDEO,
    ORIGINAL_PHOTO,
    EXTRACTED_FRAME,
    CROPPED_IMAGE,
    ENHANCED_IMAGE
}

@Serializable
enum class ProductCondition {
    NEW,
    LIKE_NEW,
    USED_VERY_GOOD,
    USED_GOOD,
    USED_ACCEPTABLE,
    DEFECTIVE,
    UNKNOWN
}

@Serializable
enum class TaxMode {
    NONE,
    SMALL_BUSINESS,
    REGULAR_VAT,
    MARGIN_SCHEME_LATER
}

@Serializable
enum class ConfidenceLevel {
    LOW,
    MEDIUM,
    HIGH
}
