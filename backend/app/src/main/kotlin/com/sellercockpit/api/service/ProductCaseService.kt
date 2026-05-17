package com.sellercockpit.api.service

import com.sellercockpit.api.mapper.DomainMapper
import com.sellercockpit.api.model.*
import com.sellercockpit.api.repository.ListingDraftRepository
import com.sellercockpit.api.repository.MarketplaceListingRepository
import com.sellercockpit.api.repository.MediaAssetRepository
import com.sellercockpit.api.repository.ProductCaseRepository
import com.sellercockpit.domain.model.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.NotFoundException
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@ApplicationScoped
class ProductCaseService @Inject constructor(
    private val productCaseRepository: ProductCaseRepository,
    private val mediaAssetRepository: MediaAssetRepository,
    private val listingDraftRepository: ListingDraftRepository,
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val pricingEngine: PricingEngine,
    private val mockAIOrchestrator: MockAIOrchestrator,
    private val listingGenerator: ListingGenerator,
    private val storageService: StorageService
) {

    @Transactional
    fun createProductCase(userId: UUID, request: CreateProductCaseRequest): ProductCaseResponse {
        val entity = ProductCaseEntity(
            id = UUID.randomUUID(),
            userId = userId,
            sellerMode = request.sellerMode,
            status = ProductCaseStatus.CAPTURED,
            title = request.title
        )
        productCaseRepository.persist(entity)
        return toResponse(DomainMapper.toDomain(entity))
    }

    fun listProductCases(userId: UUID): ProductCaseListResponse {
        val entities = ProductCaseRepository.findByUserId(userId)
        val domains = entities.map { DomainMapper.toDomain(it) }
        return ProductCaseListResponse(
            items = domains.map { toResponse(it) },
            total = domains.size
        )
    }

    fun getProductCase(userId: UUID, id: UUID): ProductCaseResponse {
        val entity = productCaseRepository.findById(id) ?: throw NotFoundException("Product case not found")
        if (entity.userId != userId) throw NotFoundException()
        return toResponse(DomainMapper.toDomain(entity))
    }

    @Transactional
    fun updateProductCase(userId: UUID, id: UUID, request: UpdateProductCaseRequest): ProductCaseResponse {
        val entity = productCaseRepository.findById(id) ?: throw NotFoundException()
        if (entity.userId != userId) throw NotFoundException()

        request.title?.let { entity.title = it }
        request.productFacts?.let { pf ->
            entity.productFacts = ProductFactsEmbeddable(
                title = pf.title,
                brand = pf.brand,
                model = pf.model,
                category = pf.category,
                variant = pf.variant,
                color = pf.color,
                sizeOrCapacity = pf.sizeOrCapacity,
                accessories = pf.accessories.joinToString(","),
                userConfirmed = pf.userConfirmed,
                confidence = pf.confidence
            )
        }
        request.conditionAssessment?.let { ca ->
            entity.conditionAssessment = ConditionAssessmentEmbeddable(
                condition = ca.condition,
                visibleDefects = ca.visibleDefects.joinToString(","),
                functionalityConfirmed = ca.functionalityConfirmed,
                missingInformation = ca.missingInformation.joinToString(","),
                confidence = ca.confidence,
                userConfirmed = ca.userConfirmed
            )
        }
        request.pricingProfile?.let { pp ->
            entity.pricingProfile = PricingProfileEmbeddable(
                sellerMode = pp.sellerMode,
                purchasePriceAmount = pp.purchasePrice?.amount,
                purchasePriceIncludesVat = pp.purchasePriceIncludesVat,
                shippingCostAmount = pp.shippingCost?.amount,
                packagingCostAmount = pp.packagingCost?.amount,
                otherCostsAmount = pp.otherCosts?.amount,
                platformFeeEstimateAmount = pp.platformFeeEstimate?.amount,
                taxMode = pp.taxMode,
                vatRatePercent = pp.vatRatePercent,
                targetMarginPercent = pp.targetMarginPercent,
                desiredMinimumPriceAmount = pp.desiredMinimumPrice?.amount,
                desiredMinimumProfitAmount = pp.desiredMinimumProfit?.amount
            )
        }
        entity.updatedAt = Instant.now()
        return toResponse(DomainMapper.toDomain(entity))
    }

    @Transactional
    fun answerQuestions(userId: UUID, id: UUID, request: AnswerQuestionsRequest): ProductCaseResponse {
        val entity = productCaseRepository.findById(id) ?: throw NotFoundException()
        if (entity.userId != userId) throw NotFoundException()
        // Remove answered questions from missing list (simple heuristic)
        request.answers.keys.forEach { key ->
            entity.missingQuestions.removeAll { it.startsWith(key) || it.contains(key, ignoreCase = true) }
        }
        if (entity.missingQuestions.isEmpty() && entity.status == ProductCaseStatus.NEEDS_USER_INFO) {
            entity.status = ProductCaseStatus.READY_FOR_RESEARCH
        }
        entity.updatedAt = Instant.now()
        return toResponse(DomainMapper.toDomain(entity))
    }

    @Transactional
    fun deleteProductCase(userId: UUID, id: UUID) {
        val entity = productCaseRepository.findById(id) ?: throw NotFoundException()
        if (entity.userId != userId) throw NotFoundException()
        // Delete associated media
        mediaAssetRepository.findByProductCaseId(id).forEach { mediaAssetRepository.delete(it) }
        listingDraftRepository.findByProductCaseId(id).forEach { listingDraftRepository.delete(it) }
        marketplaceListingRepository.findByProductCaseId(id).forEach { marketplaceListingRepository.delete(it) }
        productCaseRepository.delete(entity)
    }

    @Transactional
    fun processMedia(userId: UUID, id: UUID): ProcessMediaResponse {
        val entity = productCaseRepository.findById(id) ?: throw NotFoundException()
        if (entity.userId != userId) throw NotFoundException()

        entity.status = ProductCaseStatus.PROCESSING_MEDIA
        val mediaAssets = mediaAssetRepository.findByProductCaseId(id)

        // Run mock AI pipeline
        val pipelineResult = mockAIOrchestrator.runPipeline(entity, mediaAssets)

        entity.productFacts = pipelineResult.productFacts?.let { pf ->
            ProductFactsEmbeddable(
                title = pf.title,
                brand = pf.brand,
                model = pf.model,
                category = pf.category,
                variant = pf.variant,
                color = pf.color,
                sizeOrCapacity = pf.sizeOrCapacity,
                accessories = pf.accessories.joinToString(","),
                userConfirmed = false,
                confidence = pf.confidence
            )
        }
        entity.conditionAssessment = pipelineResult.conditionAssessment?.let { ca ->
            ConditionAssessmentEmbeddable(
                condition = ca.condition,
                visibleDefects = ca.visibleDefects.joinToString(","),
                functionalityConfirmed = ca.functionalityConfirmed,
                missingInformation = ca.missingInformation.joinToString(","),
                confidence = ca.confidence,
                userConfirmed = false
            )
        }
        entity.missingQuestions = pipelineResult.missingQuestions.toMutableList()
        entity.complianceWarnings = pipelineResult.complianceWarnings.toMutableList()
        entity.status = if (entity.missingQuestions.isNotEmpty()) ProductCaseStatus.NEEDS_USER_INFO else ProductCaseStatus.READY_FOR_RESEARCH
        entity.updatedAt = Instant.now()

        return ProcessMediaResponse(
            jobId = id.toString(),
            status = entity.status.name
        )
    }

    @Transactional
    fun runResearch(userId: UUID, id: UUID): ResearchResponse {
        val entity = productCaseRepository.findById(id) ?: throw NotFoundException()
        if (entity.userId != userId) throw NotFoundException()

        entity.status = ProductCaseStatus.RESEARCHING
        val researchResult = mockAIOrchestrator.runResearch(entity)

        entity.marketResearchResult = MarketResearchEmbeddable(
            estimatedMarketLowAmount = researchResult.estimatedMarketLow.amount,
            estimatedMarketMidAmount = researchResult.estimatedMarketMid.amount,
            estimatedMarketHighAmount = researchResult.estimatedMarketHigh.amount,
            confidence = researchResult.confidence,
            summary = researchResult.summary,
            warnings = researchResult.warnings.joinToString(","),
            currency = researchResult.estimatedMarketMid.currency
        )
        entity.status = ProductCaseStatus.PRICED
        entity.updatedAt = Instant.now()

        return ResearchResponse(
            marketResearch = researchResult,
            status = entity.status.name
        )
    }

    @Transactional
    fun recalculatePricing(userId: UUID, id: UUID, request: PricingRecalculateRequest?): PricingRecommendation {
        val entity = productCaseRepository.findById(id) ?: throw NotFoundException()
        if (entity.userId != userId) throw NotFoundException()

        val pricingProfile = request?.pricingProfile ?: entity.pricingProfile?.let { DomainMapper.toDomain(it) }
        val marketResearch = entity.marketResearchResult?.let { DomainMapper.toDomain(it) }
            ?: throw IllegalStateException("Market research not available")

        val input = PricingInput(
            sellerMode = entity.sellerMode,
            marketResearch = marketResearch,
            purchasePrice = pricingProfile?.purchasePrice,
            shippingCost = pricingProfile?.shippingCost,
            packagingCost = pricingProfile?.packagingCost,
            platformFeeEstimate = pricingProfile?.platformFeeEstimate,
            desiredMinimumPrice = pricingProfile?.desiredMinimumPrice,
            desiredMinimumProfit = pricingProfile?.desiredMinimumProfit,
            targetMarginPercent = pricingProfile?.targetMarginPercent,
            taxProfile = pricingProfile
        )

        val recommendation = pricingEngine.calculate(input)
        entity.pricingRecommendation = PricingRecommendationEmbeddable(
            quickSalePriceAmount = recommendation.quickSalePrice?.amount,
            recommendedPriceAmount = recommendation.recommendedPrice.amount,
            optimisticPriceAmount = recommendation.optimisticPrice?.amount,
            breakEvenPriceAmount = recommendation.breakEvenPrice?.amount,
            doNotSellBelowPriceAmount = recommendation.doNotSellBelowPrice?.amount,
            expectedPayoutAmount = recommendation.expectedPayout?.amount,
            estimatedProfitAmount = recommendation.estimatedProfit?.amount,
            roiPercent = recommendation.roiPercent,
            netProfitAmount = recommendation.netProfit?.amount,
            marginPercent = recommendation.marginPercent,
            explanation = recommendation.explanation,
            confidence = recommendation.confidence,
            currency = recommendation.recommendedPrice.currency
        )
        entity.status = ProductCaseStatus.LISTING_READY
        entity.updatedAt = Instant.now()
        return recommendation
    }

    @Transactional
    fun generateListings(userId: UUID, id: UUID): List<ListingDraftResponse> {
        val entity = productCaseRepository.findById(id) ?: throw NotFoundException()
        if (entity.userId != userId) throw NotFoundException()

        val productFacts = entity.productFacts?.let { pf ->
            ProductFacts(
                title = pf.title,
                brand = pf.brand,
                model = pf.model,
                category = pf.category,
                variant = pf.variant,
                color = pf.color,
                sizeOrCapacity = pf.sizeOrCapacity,
                identifiers = emptyList(),
                accessories = pf.accessories.split(",").filter { it.isNotBlank() },
                userConfirmed = pf.userConfirmed,
                confidence = pf.confidence
            )
        }
        val condition = entity.conditionAssessment?.let { ca ->
            ConditionAssessment(
                condition = ca.condition,
                visibleDefects = ca.visibleDefects.split(",").filter { it.isNotBlank() },
                functionalityConfirmed = ca.functionalityConfirmed,
                missingInformation = ca.missingInformation.split(",").filter { it.isNotBlank() },
                confidence = ca.confidence,
                userConfirmed = ca.userConfirmed
            )
        }
        val pricing = entity.pricingRecommendation?.let { DomainMapper.toDomain(it) }
        val mediaAssets = mediaAssetRepository.findByProductCaseId(id)
            .filter { it.selectedForListing }
            .sortedBy { it.sortOrder ?: Int.MAX_VALUE }
            .map { it.id.toString() }

        val drafts = listingGenerator.generateDrafts(
            productCaseId = ProductCaseId(id.toString()),
            productFacts = productFacts,
            condition = condition,
            pricing = pricing,
            imageIds = mediaAssets.map { MediaAssetId(it) }
        )

        drafts.forEach { draft ->
            listingDraftRepository.persist(DomainMapper.toEntity(draft))
        }

        return drafts.map { toResponse(it) }
    }

    @Transactional
    fun getListingDrafts(userId: UUID, id: UUID): List<ListingDraftResponse> {
        val entity = productCaseRepository.findById(id) ?: throw NotFoundException()
        if (entity.userId != userId) throw NotFoundException()
        val drafts = listingDraftRepository.findByProductCaseId(id)
        return drafts.map { toResponse(it) }
    }

    @Transactional
    fun updateListingDraft(draftId: UUID, update: ListingDraftResponse): ListingDraftResponse {
        val draft = listingDraftRepository.findById(draftId) ?: throw NotFoundException()
        draft.title = update.title
        draft.description = update.description
        draft.conditionText = update.conditionText
        draft.priceAmount = update.price.amount
        draft.readyToPublish = update.readyToPublish
        draft.updatedAt = Instant.now()
        return toResponse(draft)
    }

    @Transactional
    fun publishListing(userId: UUID, draftId: UUID, request: PublishRequest): MarketplaceListingResponse {
        val draft = listingDraftRepository.findById(draftId) ?: throw NotFoundException()
        val entity = productCaseRepository.findById(draft.productCaseId) ?: throw NotFoundException()
        if (entity.userId != userId) throw NotFoundException()

        // For MVP, direct publish only for eBay if connected, otherwise assisted/manual
        val listing = MarketplaceListingEntity().apply {
            id = UUID.randomUUID()
            productCaseId = draft.productCaseId
            platform = request.platform
            status = MarketplaceListingStatus.READY_TO_PUBLISH
            title = draft.title
            description = draft.description
            priceAmount = draft.priceAmount
            currency = draft.currency
            createdAt = Instant.now()
            updatedAt = Instant.now()
        }
        marketplaceListingRepository.persist(listing)

        // Update product case status
        val allListings = marketplaceListingRepository.findByProductCaseId(draft.productCaseId)
        val hasPublished = allListings.any { it.status == MarketplaceListingStatus.PUBLISHED }
        val allPublished = allListings.isNotEmpty() && allListings.all { it.status == MarketplaceListingStatus.PUBLISHED }
        entity.status = when {
            allPublished -> ProductCaseStatus.PUBLISHED
            hasPublished -> ProductCaseStatus.PARTIALLY_PUBLISHED
            else -> entity.status
        }
        entity.updatedAt = Instant.now()

        return toResponse(listing)
    }

    fun getMarketplaceListings(userId: UUID, productCaseId: UUID): List<MarketplaceListingResponse> {
        val entity = productCaseRepository.findById(productCaseId) ?: throw NotFoundException()
        if (entity.userId != userId) throw NotFoundException()
        return marketplaceListingRepository.findByProductCaseId(productCaseId).map { toResponse(it) }
    }

    @Transactional
    fun setExternalUrl(userId: UUID, listingId: UUID, request: SetExternalUrlRequest): MarketplaceListingResponse {
        val listing = marketplaceListingRepository.findById(listingId) ?: throw NotFoundException()
        val entity = productCaseRepository.findById(listing.productCaseId) ?: throw NotFoundException()
        if (entity.userId != userId) throw NotFoundException()
        listing.externalUrl = request.externalUrl
        if (listing.status == MarketplaceListingStatus.READY_TO_PUBLISH || listing.status == MarketplaceListingStatus.PUBLISHING) {
            listing.status = MarketplaceListingStatus.PUBLISHED
            listing.publishedAt = Instant.now()
        }
        listing.updatedAt = Instant.now()
        return toResponse(listing)
    }

    @Transactional
    fun updateListingStatus(userId: UUID, listingId: UUID, request: UpdateListingStatusRequest): MarketplaceListingResponse {
        val listing = marketplaceListingRepository.findById(listingId) ?: throw NotFoundException()
        val entity = productCaseRepository.findById(listing.productCaseId) ?: throw NotFoundException()
        if (entity.userId != userId) throw NotFoundException()
        listing.status = request.status
        listing.updatedAt = Instant.now()
        return toResponse(listing)
    }

    @Transactional
    fun createPresignedUploadUrl(userId: UUID, productCaseId: UUID, request: UploadUrlRequest): UploadUrlResponse {
        val entity = productCaseRepository.findById(productCaseId) ?: throw NotFoundException()
        if (entity.userId != userId) throw NotFoundException()

        val assetId = UUID.randomUUID()
        val storageUrl = "media/$productCaseId/$assetId-${request.filename}"
        val uploadUrl = storageService.generatePresignedUrl(storageUrl, request.contentType, 600)

        val mediaAsset = MediaAssetEntity().apply {
            id = assetId
            this.productCaseId = productCaseId
            type = if (request.contentType.startsWith("video")) MediaAssetType.ORIGINAL_VIDEO else MediaAssetType.ORIGINAL_PHOTO
            this.storageUrl = storageUrl
            selectedForListing = true
            createdAt = Instant.now()
        }
        mediaAssetRepository.persist(mediaAsset)

        return UploadUrlResponse(
            uploadUrl = uploadUrl,
            storageUrl = storageUrl,
            mediaAssetId = assetId.toString()
        )
    }

    fun getDashboard(userId: UUID): DashboardResponse {
        val entities = ProductCaseRepository.findByUserId(userId)
        val items = entities.map { entity ->
            val listings = marketplaceListingRepository.findByProductCaseId(entity.id)
            val ebay = listings.find { it.platform == MarketplacePlatform.EBAY }
            val kleinanzeigen = listings.find { it.platform == MarketplacePlatform.KLEINANZEIGEN }
            ProductCaseDashboardItem(
                id = entity.id.toString(),
                title = entity.title ?: entity.productFacts?.title ?: "Unnamed product",
                mode = entity.sellerMode,
                status = entity.status,
                ebayStatus = ebay?.status?.name,
                kleinanzeigenStatus = kleinanzeigen?.status?.name,
                nextAction = computeNextAction(entity, ebay, kleinanzeigen)
            )
        }
        return DashboardResponse(items)
    }

    private fun computeNextAction(
        entity: ProductCaseEntity,
        ebay: MarketplaceListingEntity?,
        kleinanzeigen: MarketplaceListingEntity?
    ): String = when (entity.status) {
        ProductCaseStatus.CAPTURED -> "Capture media"
        ProductCaseStatus.PROCESSING_MEDIA -> "Processing..."
        ProductCaseStatus.NEEDS_USER_INFO -> "Answer questions"
        ProductCaseStatus.READY_FOR_RESEARCH -> "Run market research"
        ProductCaseStatus.RESEARCHING -> "Researching..."
        ProductCaseStatus.PRICED -> "Review pricing"
        ProductCaseStatus.LISTING_READY -> "Generate listings"
        ProductCaseStatus.PARTIALLY_PUBLISHED -> {
            if (ebay == null || ebay.status != MarketplaceListingStatus.PUBLISHED) "Publish to eBay"
            else if (kleinanzeigen == null || kleinanzeigen.status != MarketplaceListingStatus.PUBLISHED) "Publish to Kleinanzeigen"
            else "All published"
        }
        ProductCaseStatus.PUBLISHED -> {
            if (listOfNotNull(ebay, kleinanzeigen).any { it.status == MarketplaceListingStatus.ACTIVE }) "Track listings"
            else "All done"
        }
        else -> "Review"
    }

    private fun toResponse(domain: ProductCase): ProductCaseResponse = ProductCaseResponse(
        id = domain.id.value,
        userId = domain.userId.value,
        sellerMode = domain.sellerMode,
        status = domain.status,
        title = domain.title,
        productFacts = domain.productFacts,
        conditionAssessment = domain.conditionAssessment,
        pricingProfile = domain.pricingProfile,
        pricingRecommendation = domain.pricingRecommendation,
        marketResearchResult = domain.marketResearchResult,
        missingQuestions = domain.missingQuestions,
        complianceWarnings = domain.complianceWarnings,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt
    )

    private fun toResponse(entity: ListingDraftEntity): ListingDraftResponse = ListingDraftResponse(
        id = entity.id.toString(),
        productCaseId = entity.productCaseId.toString(),
        platform = entity.platform,
        title = entity.title,
        description = entity.description,
        category = entity.category,
        conditionText = entity.conditionText,
        price = Money(entity.priceAmount, entity.currency),
        imageIds = entity.imageIds ?: emptyList(),
        attributes = entity.attributes ?: emptyMap(),
        warnings = entity.warnings ?: emptyList(),
        readyToPublish = entity.readyToPublish
    )

    private fun toResponse(draft: ListingDraft): ListingDraftResponse = ListingDraftResponse(
        id = draft.id.value,
        productCaseId = draft.productCaseId.value,
        platform = draft.platform,
        title = draft.title,
        description = draft.description,
        category = draft.category,
        conditionText = draft.conditionText,
        price = draft.price,
        imageIds = draft.imageIds.map { it.value },
        attributes = draft.attributes,
        warnings = draft.warnings,
        readyToPublish = draft.readyToPublish
    )

    private fun toResponse(entity: MarketplaceListingEntity): MarketplaceListingResponse = MarketplaceListingResponse(
        id = entity.id.toString(),
        productCaseId = entity.productCaseId.toString(),
        platform = entity.platform,
        status = entity.status,
        externalListingId = entity.externalListingId,
        externalUrl = entity.externalUrl,
        title = entity.title,
        description = entity.description,
        price = Money(entity.priceAmount, entity.currency),
        currency = entity.currency,
        publishedAt = entity.publishedAt?.toString(),
        lastSyncedAt = entity.lastSyncedAt?.toString(),
        createdAt = entity.createdAt.toString(),
        updatedAt = entity.updatedAt.toString()
    )
}
