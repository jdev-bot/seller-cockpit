package com.sellercockpit.api.ai.provider

import com.sellercockpit.api.ai.model.*
import com.sellercockpit.domain.model.*

interface AIProvider {
    val name: String
    val supportsVision: Boolean
    val supportsStructuredOutput: Boolean
    val supportsStreaming: Boolean
    val healthStatus: ProviderHealthStatus

    /** Identify product from images (vision) or description */
    suspend fun identifyProduct(images: List<String>, description: String? = null): ProductRecognitionResult

    /** Assess visible condition from images */
    suspend fun assessCondition(images: List<String>, productFacts: ProductFacts?): ConditionAssessmentResult

    /** Generate platform-specific listing draft */
    suspend fun generateListingDraft(request: ListingDraftRequest): ListingDraftResult

    /** Generate missing questions for user */
    suspend fun generateMissingQuestions(productFacts: ProductFacts?, condition: ConditionAssessment?): List<String>

    /** Research comparable prices */
    suspend fun researchPrice(productFacts: ProductFacts?): PriceResearchResult

    /** Health check */
    suspend fun healthCheck(): ProviderHealthStatus
}

enum class ProviderHealthStatus {
    HEALTHY,
    DEGRADED,
    UNAVAILABLE,
    NOT_CONFIGURED
}

enum class AIProviderType {
    OPENAI,
    ANTHROPIC,
    GEMINI,
    CHATGPT_PROXY,
    MOCK
}
