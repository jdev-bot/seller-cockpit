package com.sellercockpit.api.service

import com.sellercockpit.domain.model.*
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ListingGenerator {

    fun generateDrafts(
        productCaseId: ProductCaseId,
        productFacts: ProductFacts?,
        condition: ConditionAssessment?,
        pricing: PricingRecommendation?,
        imageIds: List<MediaAssetId>
    ): List<ListingDraft> {
        val pf = productFacts ?: return emptyList()
        val cond = condition ?: return emptyList()
        val price = pricing?.recommendedPrice ?: Money.zero()

        val ebayDraft = generateEbayDraft(productCaseId, pf, cond, price, imageIds)
        val kleinanzeigenDraft = generateKleinanzeigenDraft(productCaseId, pf, cond, price, imageIds)

        return listOf(ebayDraft, kleinanzeigenDraft)
    }

    private fun generateEbayDraft(
        productCaseId: ProductCaseId,
        pf: ProductFacts,
        cond: ConditionAssessment,
        price: Money,
        imageIds: List<MediaAssetId>
    ): ListingDraft {
        val title = buildEbayTitle(pf)
        val description = buildEbayDescription(pf, cond)
        return ListingDraft(
            id = ListingDraftId(java.util.UUID.randomUUID().toString()),
            productCaseId = productCaseId,
            platform = MarketplacePlatform.EBAY,
            title = title,
            description = description,
            category = pf.category,
            conditionText = cond.condition.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
            price = price,
            imageIds = imageIds,
            attributes = mapOf(
                "Brand" to (pf.brand ?: ""),
                "Model" to (pf.model ?: ""),
                "Color" to (pf.color ?: ""),
                "Condition" to cond.condition.name
            ),
            warnings = buildWarnings(pf, cond),
            readyToPublish = cond.userConfirmed && pf.userConfirmed
        )
    }

    private fun generateKleinanzeigenDraft(
        productCaseId: ProductCaseId,
        pf: ProductFacts,
        cond: ConditionAssessment,
        price: Money,
        imageIds: List<MediaAssetId>
    ): ListingDraft {
        val title = buildKleinanzeigenTitle(pf)
        val description = buildKleinanzeigenDescription(pf, cond)
        return ListingDraft(
            id = ListingDraftId(java.util.UUID.randomUUID().toString()),
            productCaseId = productCaseId,
            platform = MarketplacePlatform.KLEINANZEIGEN,
            title = title,
            description = description,
            category = pf.category,
            conditionText = cond.condition.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
            price = price,
            imageIds = imageIds,
            attributes = emptyMap(),
            warnings = buildWarnings(pf, cond),
            readyToPublish = cond.userConfirmed && pf.userConfirmed
        )
    }

    private fun buildEbayTitle(pf: ProductFacts): String {
        val parts = listOfNotNull(pf.brand, pf.model, pf.variant, pf.color, pf.sizeOrCapacity)
        return if (parts.isNotEmpty()) parts.joinToString(" ") else pf.title
    }

    private fun buildKleinanzeigenTitle(pf: ProductFacts): String {
        val parts = listOfNotNull(pf.brand, pf.model, pf.variant)
        return if (parts.isNotEmpty()) parts.joinToString(" ") + " – guter Zustand" else pf.title
    }

    private fun buildEbayDescription(pf: ProductFacts, cond: ConditionAssessment): String {
        val sb = StringBuilder()
        sb.appendLine("${pf.title}")
        sb.appendLine()
        sb.appendLine("Condition: ${cond.condition.name.replace("_", " ")}")
        if (cond.visibleDefects.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Visible issues:")
            cond.visibleDefects.forEach { sb.appendLine("- $it") }
        }
        if (pf.accessories.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Includes:")
            pf.accessories.forEach { sb.appendLine("- $it") }
        }
        if (cond.functionalityConfirmed == true) {
            sb.appendLine()
            sb.appendLine("Item has been tested and works as expected.")
        } else if (cond.functionalityConfirmed == false) {
            sb.appendLine()
            sb.appendLine("Item functionality has not been confirmed.")
        }
        sb.appendLine()
        sb.appendLine("Shipping within Germany. Private seller, no returns.")
        return sb.toString()
    }

    private fun buildKleinanzeigenDescription(pf: ProductFacts, cond: ConditionAssessment): String {
        val sb = StringBuilder()
        sb.appendLine("Verkaufe mein ${pf.title}.")
        sb.appendLine()
        sb.appendLine("Zustand: ${cond.condition.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}")
        if (cond.visibleDefects.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Hinweise:")
            cond.visibleDefects.forEach { sb.appendLine("- $it") }
        }
        if (pf.accessories.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Enthalten:")
            pf.accessories.forEach { sb.appendLine("- $it") }
        }
        if (cond.functionalityConfirmed == true) {
            sb.appendLine()
            sb.appendLine("Funktioniert einwandfrei.")
        } else if (cond.functionalityConfirmed == false) {
            sb.appendLine()
            sb.appendLine("Funktionsfähigkeit nicht bestätigt.")
        }
        sb.appendLine()
        sb.appendLine("Abholung bevorzugt, Versand bei Kostenübernahme möglich.")
        sb.appendLine("Privatverkauf, keine Garantie oder Rücknahme.")
        return sb.toString()
    }

    private fun buildWarnings(pf: ProductFacts, cond: ConditionAssessment): List<String> {
        val warnings = mutableListOf<String>()
        if (!pf.userConfirmed) warnings.add("Product facts not user-confirmed. Verify before publishing.")
        if (!cond.userConfirmed) warnings.add("Condition assessment not user-confirmed. Verify before publishing.")
        if (cond.functionalityConfirmed == null) warnings.add("Functionality not confirmed. Consider stating this clearly.")
        if (pf.confidence < 0.6) warnings.add("Low AI confidence in product identification. Double-check all details.")
        return warnings
    }
}
