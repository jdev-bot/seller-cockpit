package com.sellercockpit.api.model

import com.sellercockpit.domain.model.*
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "product_cases")
open class ProductCaseEntity(
    @Id
    @Column(name = "id")
    open val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    open var userId: UUID = UUID.randomUUID(),

    @Column(name = "seller_mode", nullable = false)
    @Enumerated(EnumType.STRING)
    open var sellerMode: SellerMode = SellerMode.PRIVATE_DECLUTTERING,

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    open var status: ProductCaseStatus = ProductCaseStatus.CAPTURED,

    @Column(name = "title")
    open var title: String? = null,

    @Embedded
    open var productFacts: ProductFactsEmbeddable? = null,

    @Embedded
    open var conditionAssessment: ConditionAssessmentEmbeddable? = null,

    @Embedded
    open var pricingProfile: PricingProfileEmbeddable? = null,

    @Embedded
    open var pricingRecommendation: PricingRecommendationEmbeddable? = null,

    @Embedded
    open var marketResearchResult: MarketResearchEmbeddable? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_case_missing_questions", joinColumns = [JoinColumn(name = "product_case_id")])
    @Column(name = "question")
    open var missingQuestions: MutableList<String> = mutableListOf(),

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_case_compliance_warnings", joinColumns = [JoinColumn(name = "product_case_id")])
    @Column(name = "warning")
    open var complianceWarnings: MutableList<String> = mutableListOf(),

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now()
)

@Embeddable
open class ProductFactsEmbeddable(
    open var title: String = "",
    open var brand: String? = null,
    open var model: String? = null,
    open var category: String? = null,
    open var variant: String? = null,
    open var color: String? = null,
    open var sizeOrCapacity: String? = null,
    open var accessories: String = "",
    open var userConfirmed: Boolean = false,
    open var confidence: Double = 0.0
)

@Embeddable
open class ConditionAssessmentEmbeddable(
    @Enumerated(EnumType.STRING)
    open var condition: ProductCondition = ProductCondition.UNKNOWN,
    open var visibleDefects: String = "",
    open var functionalityConfirmed: Boolean? = null,
    open var missingInformation: String = "",
    open var confidence: Double = 0.0,
    open var userConfirmed: Boolean = false
)

@Embeddable
open class PricingProfileEmbeddable(
    @Enumerated(EnumType.STRING)
    open var sellerMode: SellerMode = SellerMode.PRIVATE_DECLUTTERING,
    open var purchasePriceAmount: BigDecimal? = null,
    open var purchasePriceCurrency: String = "EUR",
    open var purchasePriceIncludesVat: Boolean? = null,
    open var shippingCostAmount: BigDecimal? = null,
    open var packagingCostAmount: BigDecimal? = null,
    open var otherCostsAmount: BigDecimal? = null,
    open var platformFeeEstimateAmount: BigDecimal? = null,
    @Enumerated(EnumType.STRING)
    open var taxMode: TaxMode? = null,
    open var vatRatePercent: BigDecimal? = null,
    open var targetMarginPercent: BigDecimal? = null,
    open var desiredMinimumPriceAmount: BigDecimal? = null,
    open var desiredMinimumProfitAmount: BigDecimal? = null
)

@Embeddable
open class MoneyEmbeddable(
    open var amount: BigDecimal = BigDecimal.ZERO,
    open var currency: String = "EUR"
)

@Embeddable
open class FeeBreakdownEmbeddable(
    open var platformFeeAmount: BigDecimal = BigDecimal.ZERO,
    open var shippingCostAmount: BigDecimal = BigDecimal.ZERO,
    open var packagingCostAmount: BigDecimal = BigDecimal.ZERO,
    open var otherCostsAmount: BigDecimal = BigDecimal.ZERO,
    open var totalCostsAmount: BigDecimal = BigDecimal.ZERO,
    open var currency: String = "EUR"
)

@Embeddable
open class TaxBreakdownEmbeddable(
    open var vatRatePercent: BigDecimal = BigDecimal.ZERO,
    open var vatAmount: BigDecimal = BigDecimal.ZERO,
    open var netRevenueAmount: BigDecimal = BigDecimal.ZERO,
    open var grossRevenueAmount: BigDecimal = BigDecimal.ZERO,
    open var currency: String = "EUR"
)

@Embeddable
open class PricingRecommendationEmbeddable(
    open var quickSalePriceAmount: BigDecimal? = null,
    open var recommendedPriceAmount: BigDecimal = BigDecimal.ZERO,
    open var optimisticPriceAmount: BigDecimal? = null,
    open var breakEvenPriceAmount: BigDecimal? = null,
    open var doNotSellBelowPriceAmount: BigDecimal? = null,
    open var expectedPayoutAmount: BigDecimal? = null,
    open var estimatedProfitAmount: BigDecimal? = null,
    open var roiPercent: BigDecimal? = null,
    open var netProfitAmount: BigDecimal? = null,
    open var marginPercent: BigDecimal? = null,
    @Embedded
    open var feeBreakdown: FeeBreakdownEmbeddable? = null,
    @Embedded
    open var taxBreakdown: TaxBreakdownEmbeddable? = null,
    open var explanation: String = "",
    @Enumerated(EnumType.STRING)
    open var confidence: ConfidenceLevel = ConfidenceLevel.MEDIUM,
    open var currency: String = "EUR"
)

@Embeddable
open class MarketResearchEmbeddable(
    open var estimatedMarketLowAmount: BigDecimal = BigDecimal.ZERO,
    open var estimatedMarketMidAmount: BigDecimal = BigDecimal.ZERO,
    open var estimatedMarketHighAmount: BigDecimal = BigDecimal.ZERO,
    @Enumerated(EnumType.STRING)
    open var confidence: ConfidenceLevel = ConfidenceLevel.MEDIUM,
    open var summary: String = "",
    open var warnings: String = "",
    open var currency: String = "EUR"
)
