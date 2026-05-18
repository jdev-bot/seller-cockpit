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
    @Column(name = "product_facts_title")
    open var title: String = "",
    @Column(name = "product_facts_brand")
    open var brand: String? = null,
    @Column(name = "product_facts_model")
    open var model: String? = null,
    @Column(name = "product_facts_category")
    open var category: String? = null,
    @Column(name = "product_facts_variant")
    open var variant: String? = null,
    @Column(name = "product_facts_color")
    open var color: String? = null,
    @Column(name = "product_facts_size_or_capacity")
    open var sizeOrCapacity: String? = null,
    @Column(name = "product_facts_accessories")
    open var accessories: String = "",
    @Column(name = "product_facts_user_confirmed")
    open var userConfirmed: Boolean = false,
    @Column(name = "product_facts_confidence")
    open var confidence: Double = 0.0
)

@Embeddable
open class ConditionAssessmentEmbeddable(
    @Enumerated(EnumType.STRING)
    @Column(name = "condition_assessment_condition")
    open var condition: ProductCondition = ProductCondition.UNKNOWN,
    @Column(name = "condition_assessment_visible_defects")
    open var visibleDefects: String = "",
    @Column(name = "condition_assessment_functionality_confirmed")
    open var functionalityConfirmed: Boolean? = null,
    @Column(name = "condition_assessment_missing_information")
    open var missingInformation: String = "",
    @Column(name = "condition_assessment_confidence")
    open var confidence: Double = 0.0,
    @Column(name = "condition_assessment_user_confirmed")
    open var userConfirmed: Boolean = false
)

@Embeddable
open class PricingProfileEmbeddable(
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_profile_seller_mode")
    open var sellerMode: SellerMode = SellerMode.PRIVATE_DECLUTTERING,
    @Column(name = "pricing_profile_purchase_price_amount")
    open var purchasePriceAmount: BigDecimal? = null,
    @Column(name = "pricing_profile_purchase_price_currency")
    open var purchasePriceCurrency: String = "EUR",
    @Column(name = "pricing_profile_purchase_price_includes_vat")
    open var purchasePriceIncludesVat: Boolean? = null,
    @Column(name = "pricing_profile_shipping_cost_amount")
    open var shippingCostAmount: BigDecimal? = null,
    @Column(name = "pricing_profile_packaging_cost_amount")
    open var packagingCostAmount: BigDecimal? = null,
    @Column(name = "pricing_profile_other_costs_amount")
    open var otherCostsAmount: BigDecimal? = null,
    @Column(name = "pricing_profile_platform_fee_estimate_amount")
    open var platformFeeEstimateAmount: BigDecimal? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_profile_tax_mode")
    open var taxMode: TaxMode? = null,
    @Column(name = "pricing_profile_vat_rate_percent")
    open var vatRatePercent: BigDecimal? = null,
    @Column(name = "pricing_profile_target_margin_percent")
    open var targetMarginPercent: BigDecimal? = null,
    @Column(name = "pricing_profile_desired_minimum_price_amount")
    open var desiredMinimumPriceAmount: BigDecimal? = null,
    @Column(name = "pricing_profile_desired_minimum_profit_amount")
    open var desiredMinimumProfitAmount: BigDecimal? = null
)

@Embeddable
open class MoneyEmbeddable(
    @Column(name = "money_amount")
    open var amount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "money_currency")
    open var currency: String = "EUR"
)

@Embeddable
open class FeeBreakdownEmbeddable(
    @Column(name = "fee_breakdown_platform_fee_amount")
    open var platformFeeAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "fee_breakdown_shipping_cost_amount")
    open var shippingCostAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "fee_breakdown_packaging_cost_amount")
    open var packagingCostAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "fee_breakdown_other_costs_amount")
    open var otherCostsAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "fee_breakdown_total_costs_amount")
    open var totalCostsAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "fee_breakdown_currency")
    open var currency: String = "EUR"
)

@Embeddable
open class TaxBreakdownEmbeddable(
    @Column(name = "tax_breakdown_vat_rate_percent")
    open var vatRatePercent: BigDecimal = BigDecimal.ZERO,
    @Column(name = "tax_breakdown_vat_amount")
    open var vatAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "tax_breakdown_net_revenue_amount")
    open var netRevenueAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "tax_breakdown_gross_revenue_amount")
    open var grossRevenueAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "tax_breakdown_currency")
    open var currency: String = "EUR"
)

@Embeddable
open class PricingRecommendationEmbeddable(
    @Column(name = "pricing_recommendation_quick_sale_price_amount")
    open var quickSalePriceAmount: BigDecimal? = null,
    @Column(name = "pricing_recommendation_recommended_price_amount")
    open var recommendedPriceAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "pricing_recommendation_optimistic_price_amount")
    open var optimisticPriceAmount: BigDecimal? = null,
    @Column(name = "pricing_recommendation_break_even_price_amount")
    open var breakEvenPriceAmount: BigDecimal? = null,
    @Column(name = "pricing_recommendation_do_not_sell_below_price_amount")
    open var doNotSellBelowPriceAmount: BigDecimal? = null,
    @Column(name = "pricing_recommendation_expected_payout_amount")
    open var expectedPayoutAmount: BigDecimal? = null,
    @Column(name = "pricing_recommendation_estimated_profit_amount")
    open var estimatedProfitAmount: BigDecimal? = null,
    @Column(name = "pricing_recommendation_roi_percent")
    open var roiPercent: BigDecimal? = null,
    @Column(name = "pricing_recommendation_net_profit_amount")
    open var netProfitAmount: BigDecimal? = null,
    @Column(name = "pricing_recommendation_margin_percent")
    open var marginPercent: BigDecimal? = null,
    @Embedded
    open var feeBreakdown: FeeBreakdownEmbeddable? = null,
    @Embedded
    open var taxBreakdown: TaxBreakdownEmbeddable? = null,
    @Column(name = "pricing_recommendation_explanation")
    open var explanation: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_recommendation_confidence")
    open var confidence: ConfidenceLevel = ConfidenceLevel.MEDIUM,
    @Column(name = "pricing_recommendation_currency")
    open var currency: String = "EUR"
)

@Embeddable
open class MarketResearchEmbeddable(
    @Column(name = "market_research_estimated_market_low_amount")
    open var estimatedMarketLowAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "market_research_estimated_market_mid_amount")
    open var estimatedMarketMidAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "market_research_estimated_market_high_amount")
    open var estimatedMarketHighAmount: BigDecimal = BigDecimal.ZERO,
    @Enumerated(EnumType.STRING)
    @Column(name = "market_research_confidence")
    open var confidence: ConfidenceLevel = ConfidenceLevel.MEDIUM,
    @Column(name = "market_research_summary")
    open var summary: String = "",
    @Column(name = "market_research_warnings")
    open var warnings: String = "",
    @Column(name = "market_research_currency")
    open var currency: String = "EUR"
)
