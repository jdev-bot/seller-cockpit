package com.sellercockpit.api.model

import com.sellercockpit.domain.model.*
import java.math.BigDecimal

// Request DTOs

data class CreateProductCaseRequest(
    val sellerMode: SellerMode,
    val title: String? = null
)

data class UpdateProductCaseRequest(
    val title: String? = null,
    val productFacts: ProductFacts? = null,
    val conditionAssessment: ConditionAssessment? = null,
    val pricingProfile: PricingProfile? = null
)

data class AnswerQuestionsRequest(
    val answers: Map<String, String>
)

data class UploadUrlRequest(
    val filename: String,
    val contentType: String
)

data class CompleteUploadRequest(
    val storageUrl: String
)

data class PublishRequest(
    val platform: MarketplacePlatform
)

data class SetExternalUrlRequest(
    val externalUrl: String
)

data class UpdateListingStatusRequest(
    val status: MarketplaceListingStatus
)

// Response DTOs

data class ProductCaseResponse(
    val id: String,
    val userId: String,
    val sellerMode: SellerMode,
    val status: ProductCaseStatus,
    val title: String?,
    val productFacts: ProductFacts?,
    val conditionAssessment: ConditionAssessment?,
    val pricingProfile: PricingProfile?,
    val pricingRecommendation: PricingRecommendation?,
    val marketResearchResult: MarketResearchResult?,
    val missingQuestions: List<String>,
    val complianceWarnings: List<String>,
    val createdAt: String,
    val updatedAt: String
)

data class ProductCaseListResponse(
    val items: List<ProductCaseResponse>,
    val total: Int
)

data class UploadUrlResponse(
    val uploadUrl: String,
    val storageUrl: String,
    val mediaAssetId: String
)

data class MediaAssetResponse(
    val id: String,
    val productCaseId: String,
    val type: MediaAssetType,
    val storageUrl: String,
    val thumbnailUrl: String?,
    val selectedForListing: Boolean,
    val sortOrder: Int?,
    val metadata: Map<String, String>,
    val createdAt: String
)

data class ListingDraftResponse(
    val id: String,
    val productCaseId: String,
    val platform: MarketplacePlatform,
    val title: String,
    val description: String,
    val category: String?,
    val conditionText: String,
    val price: Money,
    val imageIds: List<String>,
    val attributes: Map<String, String>,
    val warnings: List<String>,
    val readyToPublish: Boolean
)

data class MarketplaceListingResponse(
    val id: String,
    val productCaseId: String,
    val platform: MarketplacePlatform,
    val status: MarketplaceListingStatus,
    val externalListingId: String?,
    val externalUrl: String?,
    val title: String,
    val description: String,
    val price: Money,
    val currency: String,
    val publishedAt: String?,
    val lastSyncedAt: String?,
    val createdAt: String,
    val updatedAt: String
)

data class DashboardResponse(
    val productCases: List<ProductCaseDashboardItem>
)

data class ProductCaseDashboardItem(
    val id: String,
    val title: String?,
    val mode: SellerMode,
    val status: ProductCaseStatus,
    val ebayStatus: String?,
    val kleinanzeigenStatus: String?,
    val nextAction: String
)

data class ProcessMediaResponse(
    val jobId: String,
    val status: String
)

data class JobStatusResponse(
    val jobId: String,
    val status: String,
    val step: String?,
    val progress: Int?,
    val result: Any?,
    val error: String?
)

data class PricingRecalculateRequest(
    val pricingProfile: PricingProfile? = null
)

data class ResearchResponse(
    val marketResearch: MarketResearchResult?,
    val status: String
)

data class ConnectMarketplaceRequest(
    val platform: MarketplacePlatform,
    val authCode: String? = null
)
