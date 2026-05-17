package com.sellercockpit.api.mapper

import com.sellercockpit.api.model.*
import com.sellercockpit.domain.model.*
import java.math.BigDecimal
import java.time.Instant
import java.util.*

object DomainMapper {

    fun toDomain(entity: ProductCaseEntity): ProductCase = ProductCase(
        id = ProductCaseId(entity.id.toString()),
        userId = UserId(entity.userId.toString()),
        sellerMode = entity.sellerMode,
        status = entity.status,
        title = entity.title,
        productFacts = entity.productFacts?.let { pf ->
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
        },
        conditionAssessment = entity.conditionAssessment?.let { ca ->
            ConditionAssessment(
                condition = ca.condition,
                visibleDefects = ca.visibleDefects.split(",").filter { it.isNotBlank() },
                functionalityConfirmed = ca.functionalityConfirmed,
                missingInformation = ca.missingInformation.split(",").filter { it.isNotBlank() },
                confidence = ca.confidence,
                userConfirmed = ca.userConfirmed
            )
        },
        pricingProfile = entity.pricingProfile?.let { toDomain(it) },
        pricingRecommendation = entity.pricingRecommendation?.let { toDomain(it) },
        marketResearchResult = entity.marketResearchResult?.let { toDomain(it) },
        missingQuestions = entity.missingQuestions,
        userAnswers = emptyMap(),
        complianceWarnings = entity.complianceWarnings,
        createdAt = entity.createdAt.toString(),
        updatedAt = entity.updatedAt.toString()
    )

    fun toDomain(pf: PricingProfileEmbeddable): PricingProfile = PricingProfile(
        sellerMode = pf.sellerMode,
        purchasePrice = pf.purchasePriceAmount?.let { Money(it, pf.purchasePriceCurrency) },
        purchasePriceIncludesVat = pf.purchasePriceIncludesVat,
        shippingCost = pf.shippingCostAmount?.let { Money(it) },
        packagingCost = pf.packagingCostAmount?.let { Money(it) },
        otherCosts = pf.otherCostsAmount?.let { Money(it) },
        platformFeeEstimate = pf.platformFeeEstimateAmount?.let { Money(it) },
        taxMode = pf.taxMode,
        vatRatePercent = pf.vatRatePercent,
        targetMarginPercent = pf.targetMarginPercent,
        desiredMinimumPrice = pf.desiredMinimumPriceAmount?.let { Money(it) },
        desiredMinimumProfit = pf.desiredMinimumProfitAmount?.let { Money(it) }
    )

    fun toDomain(pr: PricingRecommendationEmbeddable): PricingRecommendation = PricingRecommendation(
        quickSalePrice = pr.quickSalePriceAmount?.let { Money(it, pr.currency) },
        recommendedPrice = Money(pr.recommendedPriceAmount, pr.currency),
        optimisticPrice = pr.optimisticPriceAmount?.let { Money(it, pr.currency) },
        breakEvenPrice = pr.breakEvenPriceAmount?.let { Money(it, pr.currency) },
        doNotSellBelowPrice = pr.doNotSellBelowPriceAmount?.let { Money(it, pr.currency) },
        expectedPayout = pr.expectedPayoutAmount?.let { Money(it, pr.currency) },
        estimatedProfit = pr.estimatedProfitAmount?.let { Money(it, pr.currency) },
        roiPercent = pr.roiPercent,
        netProfit = pr.netProfitAmount?.let { Money(it, pr.currency) },
        marginPercent = pr.marginPercent,
        feeBreakdown = pr.feeBreakdown?.let { fb ->
            FeeBreakdown(
                platformFee = Money(fb.platformFeeAmount, fb.currency),
                shippingCost = Money(fb.shippingCostAmount, fb.currency),
                packagingCost = Money(fb.packagingCostAmount, fb.currency),
                otherCosts = Money(fb.otherCostsAmount, fb.currency),
                totalCosts = Money(fb.totalCostsAmount, fb.currency)
            )
        },
        taxBreakdown = pr.taxBreakdown?.let { tb ->
            TaxBreakdown(
                vatRatePercent = tb.vatRatePercent,
                vatAmount = Money(tb.vatAmount, tb.currency),
                netRevenue = Money(tb.netRevenueAmount, tb.currency),
                grossRevenue = Money(tb.grossRevenueAmount, tb.currency)
            )
        },
        explanation = pr.explanation,
        confidence = pr.confidence
    )

    fun toDomain(mr: MarketResearchEmbeddable): MarketResearchResult = MarketResearchResult(
        comparables = emptyList(),
        estimatedMarketLow = Money(mr.estimatedMarketLowAmount, mr.currency),
        estimatedMarketMid = Money(mr.estimatedMarketMidAmount, mr.currency),
        estimatedMarketHigh = Money(mr.estimatedMarketHighAmount, mr.currency),
        confidence = mr.confidence,
        summary = mr.summary,
        warnings = mr.warnings.split(",").filter { it.isNotBlank() }
    )

    fun toEntity(dto: CreateProductCaseRequest, userId: UUID): ProductCaseEntity = ProductCaseEntity(
        id = UUID.randomUUID(),
        userId = userId,
        sellerMode = dto.sellerMode,
        status = ProductCaseStatus.CAPTURED,
        title = dto.title
    )

    fun toEntity(draft: ListingDraft): ListingDraftEntity = ListingDraftEntity(
        id = UUID.fromString(draft.id.value),
        productCaseId = UUID.fromString(draft.productCaseId.value),
        platform = draft.platform,
        title = draft.title,
        description = draft.description,
        category = draft.category,
        conditionText = draft.conditionText,
        priceAmount = draft.price.amount,
        currency = draft.price.currency,
        imageIds = draft.imageIds.map { it.value },
        attributes = draft.attributes,
        warnings = draft.warnings,
        readyToPublish = draft.readyToPublish
    )
}
