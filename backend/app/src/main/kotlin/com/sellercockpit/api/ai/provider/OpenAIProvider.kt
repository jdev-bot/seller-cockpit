package com.sellercockpit.api.ai.provider

import com.sellercockpit.api.ai.model.*
import com.sellercockpit.domain.model.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@ApplicationScoped
class OpenAIProvider @Inject constructor(
    @ConfigProperty(name = "ai.openai.api-key") private val apiKey: String,
    @ConfigProperty(name = "ai.openai.base-url", defaultValue = "https://api.openai.com/v1") private val baseUrl: String,
    @ConfigProperty(name = "ai.openai.model", defaultValue = "gpt-4o") private val model: String,
    @ConfigProperty(name = "ai.openai.max-tokens", defaultValue = "4096") private val maxTokens: Int,
    @ConfigProperty(name = "ai.openai.temperature", defaultValue = "0.2") private val temperature: Float
) : AIProvider {

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override val name = "openai"
    override val supportsVision = true
    override val supportsStructuredOutput = true
    override val supportsStreaming = false
    override var healthStatus = ProviderHealthStatus.NOT_CONFIGURED

    init {
        if (apiKey.isNotBlank() && apiKey != "placeholder") {
            healthStatus = ProviderHealthStatus.HEALTHY
        }
    }

    override suspend fun identifyProduct(images: List<String>, description: String?): ProductRecognitionResult {
        val prompt = buildString {
            appendLine("You are a product identification expert. Analyze the provided images and identify the physical product.")
            appendLine()
            appendLine("Return ONLY a JSON object with this exact structure:")
            appendLine("{")
            appendLine("  \"title\": \"human-readable product title\",")
            appendLine("  \"brand\": \"brand name or null\",")
            appendLine("  \"model\": \"model number/name or null\",")
            appendLine("  \"category\": \"product category or null\",")
            appendLine("  \"variant\": \"variant/color/capacity or null\",")
            appendLine("  \"color\": \"color or null\",")
            appendLine("  \"sizeOrCapacity\": \"size/capacity or null\",")
            appendLine("  \"identifierType\": \"SKU/ISBN/MPN or null\",")
            appendLine("  \"identifierValue\": \"identifier value or null\",")
            appendLine("  \"accessories\": [\"accessory 1\", \"accessory 2\"],")
            appendLine("  \"confidence\": 0.95,")
            appendLine("  \"reasoning\": \"brief reasoning for your identification\"")
            appendLine("}")
            appendLine()
            appendLine("Be honest about confidence. If uncertain, state that in reasoning and keep confidence < 0.7.")
            appendLine("Do NOT invent details. If you cannot see it, return null.")
        }

        val messages = buildVisionMessages(prompt, images, description)
        val response = chatCompletion(messages, true)
        return parseJsonResponse(response) ?: throw IllegalStateException("Failed to parse OpenAI response")
    }

    override suspend fun assessCondition(images: List<String>, productFacts: ProductFacts?): ConditionAssessmentResult {
        val prompt = buildString {
            appendLine("You are a condition assessment expert. Examine the product images carefully.")
            appendLine()
            appendLine("Determine the overall condition from these options: NEW, LIKE_NEW, USED_VERY_GOOD, USED_GOOD, USED_ACCEPTABLE, DEFECTIVE, UNKNOWN")
            appendLine()
            appendLine("Return ONLY a JSON object:")
            appendLine("{")
            appendLine("  \"condition\": \"USED_GOOD\",")
            appendLine("  \"visibleDefects\": [\"minor scratch on corner\", \"slight discoloration\"],")
            appendLine("  \"functionalityConfirmed\": null,")
            appendLine("  \"missingInformation\": [\"Does it power on?\", \"Are all buttons responsive?\"],")
            appendLine("  \"confidence\": 0.85,")
            appendLine("  \"reasoning\": \"visible wear consistent with moderate use\"")
            appendLine("}")
            appendLine()
            appendLine("functionalityConfirmed: null if you cannot determine from images (most common).")
            appendLine("missingInformation: list questions the seller should answer to complete the listing.")
            appendLine("Do NOT overstate condition. Be conservative.")
        }

        val messages = buildVisionMessages(prompt, images)
        val response = chatCompletion(messages, true)
        return parseJsonResponse(response) ?: throw IllegalStateException("Failed to parse condition response")
    }

    override suspend fun generateListingDraft(request: ListingDraftRequest): ListingDraftResult {
        val prompt = buildString {
            appendLine("You are an expert marketplace listing writer. Create an optimized listing for ${request.platform}.")
            appendLine()
            appendLine("Product: ${request.productFacts.title}")
            appendLine("Brand: ${request.productFacts.brand ?: "N/A"}")
            appendLine("Condition: ${request.conditionAssessment.condition}")
            if (request.conditionAssessment.visibleDefects.isNotEmpty()) {
                appendLine("Visible defects: ${request.conditionAssessment.visibleDefects.joinToString(", ")}")
            }
            if (request.pricing != null) {
                appendLine("Price: ${request.pricing.recommendedPrice.amount} ${request.pricing.recommendedPrice.currency}")
            }
            appendLine()
            appendLine("Return ONLY a JSON object:")
            appendLine("{")
            appendLine("  \"title\": \"optimized search-friendly title, max 80 chars\",")
            appendLine("  \"description\": \"detailed description with condition, defects, shipping info\",")
            appendLine("  \"conditionText\": \"human-readable condition description\",")
            appendLine("  \"attributes\": {\"Brand\": \"...\", \"Model\": \"...\"},")
            appendLine("  \"warnings\": [\"list any unsupported claims to avoid\"]")
            appendLine("}")
            appendLine()
            if (request.platform == MarketplacePlatform.KLEINANZEIGEN) {
                appendLine("Write in German. Keep it personal and trustworthy. Include 'Privatverkauf, keine Garantie oder Rücknahme'.")
            } else {
                appendLine("Write in English. Use structured format with clear sections. Be search-optimized.")
            }
        }

        val messages = listOf(
            mapOf("role" to "system", "content" to "You are a marketplace listing optimization expert."),
            mapOf("role" to "user", "content" to prompt)
        )

        val response = chatCompletion(messages, true)
        return parseJsonResponse(response) ?: throw IllegalStateException("Failed to parse listing response")
    }

    override suspend fun generateMissingQuestions(productFacts: ProductFacts?, condition: ConditionAssessment?): List<String> {
        val prompt = buildString {
            appendLine("Based on this product information, what critical information is still missing for a complete marketplace listing?")
            appendLine()
            if (productFacts != null) {
                appendLine("Product: ${productFacts.title}")
                appendLine("Brand: ${productFacts.brand ?: "unknown"}")
            }
            if (condition != null) {
                appendLine("Condition: ${condition.condition}")
                appendLine("Defects visible: ${condition.visibleDefects.joinToString(", ")}")
                appendLine("Functionality confirmed: ${condition.functionalityConfirmed ?: "unknown"}")
            }
            appendLine()
            appendLine("Return ONLY a JSON array of concise questions the seller should answer, e.g.:")
            appendLine("[\"Does it power on?\", \"Are original accessories included?\"]")
            appendLine("If all critical info is present, return [].")
        }

        val messages = listOf(
            mapOf("role" to "system", "content" to "You help identify missing information for marketplace listings."),
            mapOf("role" to "user", "content" to prompt)
        )

        val response = chatCompletion(messages, true)
        return parseListResponse(response) ?: emptyList()
    }

    override suspend fun researchPrice(productFacts: ProductFacts?): PriceResearchResult {
        val prompt = buildString {
            appendLine("You are a market research analyst. Estimate a realistic price range for this used product.")
            appendLine()
            if (productFacts != null) {
                appendLine("Product: ${productFacts.title}")
                appendLine("Brand: ${productFacts.brand ?: "N/A"}")
                appendLine("Model: ${productFacts.model ?: "N/A"}")
                appendLine("Condition: ${productFacts.category ?: "N/A"}")
            }
            appendLine()
            appendLine("Return ONLY a JSON object:")
            appendLine("{")
            appendLine("  \"estimatedMarketLow\": {\"amount\": 25.00, \"currency\": \"EUR\"},")
            appendLine("  \"estimatedMarketMid\": {\"amount\": 45.00, \"currency\": \"EUR\"},")
            appendLine("  \"estimatedMarketHigh\": {\"amount\": 65.00, \"currency\": \"EUR\"},")
            appendLine("  \"comparables\": [")
            appendLine("    {")
            appendLine("      \"id\": \"comp-1\",")
            appendLine("      \"platform\": \"EBAY\",")
            appendLine("      \"title\": \"similar listing\",")
            appendLine("      \"price\": {\"amount\": 50.00, \"currency\": \"EUR\"},")
            appendLine("      \"relevanceScore\": 0.85,")
            appendLine("      \"sold\": true")
            appendLine("    }")
            appendLine("  ],")
            appendLine("  \"confidence\": \"MEDIUM\",")
            appendLine("  \"summary\": \"brief market analysis\",")
            appendLine("  \"warnings\": [\"caveats about estimate\"]")
            appendLine("}")
            appendLine()
            appendLine("Use real-world knowledge of typical used prices in EUR for German/EU marketplaces.")
        }

        val messages = listOf(
            mapOf("role" to "system", "content" to "You are a market research analyst with knowledge of used item prices on eBay Kleinanzeigen."),
            mapOf("role" to "user", "content" to prompt)
        )

        val response = chatCompletion(messages, true)
        return parseJsonResponse(response) ?: throw IllegalStateException("Failed to parse research response")
    }

    override suspend fun healthCheck(): ProviderHealthStatus {
        try {
            val messages = listOf(mapOf("role" to "user", "content" to "Say 'healthy' and nothing else."))
            val response = chatCompletion(messages, false)
            healthStatus = if (response.contains("healthy", ignoreCase = true)) ProviderHealthStatus.HEALTHY else ProviderHealthStatus.DEGRADED
        } catch (e: Exception) {
            healthStatus = ProviderHealthStatus.UNAVAILABLE
        }
        return healthStatus
    }

    private fun buildVisionMessages(prompt: String, images: List<String>, description: String? = null): List<Map<String, Any?>> {
        val content = mutableListOf<Map<String, Any?>>()
        content.add(mapOf("type" to "text", "text" to prompt))

        images.forEach { imageUrl ->
            content.add(mapOf(
                "type" to "image_url",
                "image_url" to mapOf(
                    "url" to imageUrl,
                    "detail" to "high"
                )
            ))
        }

        description?.let {
            content.add(mapOf("type" to "text", "text" to "Additional description: $it"))
        }

        return listOf(
            mapOf("role" to "system", "content" to "You are a product analysis expert that responds only in JSON."),
            mapOf("role" to "user", "content" to content)
        )
    }

    private fun chatCompletion(messages: List<Map<String, Any?>>, jsonMode: Boolean): String {
        val requestBody = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                messages.forEach { msg ->
                    addJsonObject {
                        msg.forEach { (k, v) ->
                            when (v) {
                                is String -> put(k, v)
                                is List<*> -> put(k, json.encodeToJsonElement(v))
                                else -> put(k, json.encodeToJsonElement(v))
                            }
                        }
                    }
                }
            }
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            if (jsonMode) {
                put("response_format", buildJsonObject { put("type", "json_object") })
            }
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/chat/completions"))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw IllegalStateException("OpenAI API error ${response.statusCode()}: ${response.body()}")
        }

        val jsonResponse = json.parseToJsonElement(response.body()).jsonObject
        return jsonResponse["choices"]?.jsonArray?.get(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Unexpected OpenAI response format")
    }

    private inline fun <reified T> parseJsonResponse(jsonStr: String): T? {
        return try {
            json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseListResponse(jsonStr: String): List<String>? {
        return try {
            json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            null
        }
    }
}
