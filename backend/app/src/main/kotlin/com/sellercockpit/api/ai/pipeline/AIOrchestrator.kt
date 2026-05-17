package com.sellercockpit.api.ai.pipeline

import com.sellercockpit.api.ai.model.*
import com.sellercockpit.api.ai.provider.AIProvider
import com.sellercockpit.api.ai.provider.AIProviderRegistry
import com.sellercockpit.api.research.MarketResearchService
import com.sellercockpit.api.model.MediaAssetEntity
import com.sellercockpit.api.model.ProductCaseEntity
import com.sellercockpit.domain.model.*
import com.sellercockpit.api.service.StorageService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jboss.logging.Logger

/** Real AI orchestrator that chains the structured AI pipeline.
 *  Uses configured provider with fallback support.
 */
@ApplicationScoped
class AIOrchestrator @Inject constructor(
    private val registry: AIProviderRegistry,
    private val storageService: StorageService
) {
    private val log = Logger.getLogger(javaClass)

    data class PipelineResult(
        val productFacts: ProductFacts?,
        val conditionAssessment: ConditionAssessment?,
        val missingQuestions: List<String>,
        val complianceWarnings: List<String>
    )

    suspend fun runPipeline(entity: ProductCaseEntity, mediaAssets: List<MediaAssetEntity>): PipelineResult {
        val provider = registry.getAnyAvailable()
            ?: throw IllegalStateException("No AI provider available. Configure at least one in application.properties (ai.openai.api-key or ai.anthropic.api-key)")

        log.info("Running AI pipeline for product case ${entity.id} using provider ${provider.name}")

        val imageUrls = mediaAssets.filter { it.selectedForListing || it.type == MediaAssetType.ORIGINAL_PHOTO || it.type == MediaAssetType.ORIGINAL_VIDEO }
            .sortedBy { it.sortOrder ?: Int.MAX_VALUE }
            .take(5) // Limit to 5 images for cost control
            .map { storageService.getPublicUrl(it.storageUrl) }

        val mediaUrls = if (imageUrls.isNotEmpty()) imageUrls else {
            log.warn("No images found for product case ${entity.id}, using text-only mode")
            emptyList()
        }

        val productTitle = entity.title ?: "Unknown product"

        // Step 1: Product recognition
        val productResult = try {
            provider.identifyProduct(mediaUrls, "Product case ${entity.id}: $productTitle")
        } catch (e: Exception) {
            log.error("Product identification failed, trying fallback", e)
            tryFallback { it.identifyProduct(mediaUrls, productTitle) }
        }

        val productFacts = ProductFacts(
            title = productResult?.title ?: productTitle,
            brand = productResult?.brand,
            model = productResult?.model,
            category = productResult?.category,
            variant = productResult?.variant,
            color = productResult?.color,
            sizeOrCapacity = productResult?.sizeOrCapacity,
            identifiers = listOfNotNull(
                productResult?.identifierType?.let { ProductIdentifier(productResult.identifierType, productResult.identifierValue ?: "") }
            ),
            accessories = productResult?.accessories ?: emptyList(),
            userConfirmed = false,
            confidence = productResult?.confidence ?: 0.0
        )

        // Step 2: Condition assessment
        val conditionResult = try {
            provider.assessCondition(mediaUrls, productFacts)
        } catch (e: Exception) {
            log.error("Condition assessment failed, trying fallback", e)
            tryFallback { it.assessCondition(mediaUrls, productFacts) }
        }

        val conditionAssessment = conditionResult?.let {
            ConditionAssessment(
                condition = it.condition,
                visibleDefects = it.visibleDefects,
                functionalityConfirmed = it.functionalityConfirmed,
                missingInformation = it.missingInformation,
                confidence = it.confidence,
                userConfirmed = false
            )
        }

        // Step 3: Generate missing questions
        val missingQuestions = try {
            provider.generateMissingQuestions(productFacts, conditionAssessment)
        } catch (e: Exception) {
            log.error("Missing questions generation failed", e)
            listOf("Does the item work normally?", "Is original packaging included?", "Are all accessories present?")
        }

        // Step 4: Compliance check
        val complianceWarnings = mutableListOf<String>()
        if (productFacts.confidence < 0.6) {
            complianceWarnings.add("Low AI confidence (${(productFacts.confidence * 100).toInt()}%). Verify product details.")
        }
        if (conditionAssessment?.functionalityConfirmed == null) {
            complianceWarnings.add("Functionality not confirmed from images. Ask seller or test before publishing.")
        }
        if (conditionAssessment?.visibleDefects?.isNotEmpty() == true) {
            complianceWarnings.add("Visible defects detected. Ensure they are disclosed in listing.")
        }

        return PipelineResult(
            productFacts = productFacts,
            conditionAssessment = conditionAssessment,
            missingQuestions = missingQuestions,
            complianceWarnings = complianceWarnings
        )
    }

    suspend fun runResearch(entity: ProductCaseEntity): MarketResearchResult {
        val provider = registry.getAnyAvailable()
            ?: return fallbackResearch(entity)

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

        return try {
            val result = provider.researchPrice(productFacts)
            MarketResearchResult(
                comparables = result.comparables,
                estimatedMarketLow = result.estimatedMarketLow,
                estimatedMarketMid = result.estimatedMarketMid,
                estimatedMarketHigh = result.estimatedMarketHigh,
                confidence = result.confidence,
                summary = result.summary,
                warnings = result.warnings
            )
        } catch (e: Exception) {
            log.error("Research failed, returning fallback", e)
            fallbackResearch(entity)
        }
    }

    suspend fun generateListingDrafts(
        productCaseId: ProductCaseId,
        productFacts: ProductFacts,
        condition: ConditionAssessment,
        pricing: PricingRecommendation?,
        platform: MarketplacePlatform
    ): ListingDraft? {
        val provider = registry.getAnyAvailable() ?: return null

        return try {
            val request = ListingDraftRequest(productFacts, condition, platform, pricing)
            val result = provider.generateListingDraft(request)
            ListingDraft(
                id = ListingDraftId(java.util.UUID.randomUUID().toString()),
                productCaseId = productCaseId,
                platform = platform,
                title = result.title,
                description = result.description,
                conditionText = result.conditionText,
                price = pricing?.recommendedPrice ?: Money(java.math.BigDecimal.ZERO),
                attributes = result.attributes,
                warnings = result.warnings,
                readyToPublish = condition.userConfirmed && productFacts.userConfirmed
            )
        } catch (e: Exception) {
            log.error("Listing draft generation failed for $platform", e)
            null
        }
    }

    private inline fun <reified T> tryFallback(block: (AIProvider) -> T): T? {
        val fallback = registry.getFallback()
        return try {
            fallback?.let { block(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun fallbackResearch(entity: ProductCaseEntity): MarketResearchResult {
        val basePrice = when {
            entity.title?.contains("phone", ignoreCase = true) == true -> java.math.BigDecimal("250.00")
            entity.title?.contains("keyboard", ignoreCase = true) == true -> java.math.BigDecimal("45.00")
            entity.title?.contains("monitor", ignoreCase = true) == true -> java.math.BigDecimal("120.00")
            else -> java.math.BigDecimal("55.00")
        }
        val low = basePrice * java.math.BigDecimal("0.80")
        val high = basePrice * java.math.BigDecimal("1.25")
        return MarketResearchResult(
            comparables = emptyList(),
            estimatedMarketLow = Money(low.setScale(2, java.math.RoundingMode.HALF_UP)),
            estimatedMarketMid = Money(basePrice.setScale(2, java.math.RoundingMode.HALF_UP)),
            estimatedMarketHigh = Money(high.setScale(2, java.math.RoundingMode.HALF_UP)),
            confidence = ConfidenceLevel.LOW,
            summary = "No AI provider available. Using fallback estimate. Please configure a provider.",
            warnings = listOf("AI not configured — prices are rough estimates only.")
        )
    }
}
