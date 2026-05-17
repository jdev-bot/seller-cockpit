package com.sellercockpit.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ProductCase(
    val id: ProductCaseId,
    val userId: UserId,
    val sellerMode: SellerMode,
    val status: ProductCaseStatus,
    val title: String? = null,
    val productFacts: ProductFacts? = null,
    val conditionAssessment: ConditionAssessment? = null,
    val pricingProfile: PricingProfile? = null,
    val pricingRecommendation: PricingRecommendation? = null,
    val marketResearchResult: MarketResearchResult? = null,
    val missingQuestions: List<String> = emptyList(),
    val userAnswers: Map<String, String> = emptyMap(),
    val complianceWarnings: List<String> = emptyList(),
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class ProductCaseDossier(
    val productCaseId: ProductCaseId,
    val sellerMode: SellerMode,
    val media: DossierMedia,
    val productFacts: ProductFacts? = null,
    val conditionAssessment: ConditionAssessment? = null,
    val missingQuestions: List<String> = emptyList(),
    val marketResearch: MarketResearchResult? = null,
    val pricing: PricingRecommendation? = null,
    val listingDrafts: Map<MarketplacePlatform, ListingDraft> = emptyMap(),
    val compliance: DossierCompliance
)

@Serializable
data class DossierMedia(
    val video: MediaAsset? = null,
    val selectedImages: List<MediaAsset> = emptyList()
)

@Serializable
data class DossierCompliance(
    val warnings: List<String> = emptyList(),
    val blocked: Boolean = false,
    val userApproved: Boolean = false
)
