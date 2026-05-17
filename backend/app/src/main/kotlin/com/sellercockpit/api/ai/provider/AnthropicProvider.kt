package com.sellercockpit.api.ai.provider

import com.sellercockpit.api.ai.model.*
import com.sellercockpit.domain.model.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.*
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Anthropic Claude adapter.
 *  Claude 3 Opus/Sonnet/Haiku support vision and long context. */
@ApplicationScoped
class AnthropicProvider @Inject constructor(
    @ConfigProperty(name = "ai.anthropic.api-key", defaultValue = "") private val apiKey: String,
    @ConfigProperty(name = "ai.anthropic.base-url", defaultValue = "https://api.anthropic.com") private val baseUrl: String,
    @ConfigProperty(name = "ai.anthropic.model", defaultValue = "claude-3-opus-20240229") private val model: String,
    @ConfigProperty(name = "ai.anthropic.max-tokens", defaultValue = "4096") private val maxTokens: Int
) : AIProvider {

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
    private val json = Json { ignoreUnknownKeys = true }

    override val name = "anthropic"
    override val supportsVision = true
    override val supportsStructuredOutput = false
    override val supportsStreaming = false
    override var healthStatus = ProviderHealthStatus.NOT_CONFIGURED

    init {
        if (apiKey.isNotBlank() && apiKey != "placeholder") healthStatus = ProviderHealthStatus.HEALTHY
    }

    override suspend fun identifyProduct(images: List<String>, description: String?): ProductRecognitionResult {
        val prompt = buildVisionPrompt("identify", images, description)
        val response = messages(prompt)
        return parseJsonResponse(response) ?: throw IllegalStateException("Failed to parse Anthropic response")
    }

    override suspend fun assessCondition(images: List<String>, productFacts: ProductFacts?): ConditionAssessmentResult {
        val prompt = buildVisionPrompt("condition", images)
        val response = messages(prompt)
        return parseJsonResponse(response) ?: throw IllegalStateException("Failed to parse condition response")
    }

    override suspend fun generateListingDraft(request: ListingDraftRequest): ListingDraftResult {
        val prompt = buildListingPrompt(request)
        val response = messages(prompt)
        return parseJsonResponse(response) ?: throw IllegalStateException("Failed to parse draft response")
    }

    override suspend fun generateMissingQuestions(productFacts: ProductFacts?, condition: ConditionAssessment?): List<String> {
        val prompt = buildMissingQuestionsPrompt(productFacts, condition)
        val response = messages(prompt)
        return parseListResponse(response) ?: emptyList()
    }

    override suspend fun researchPrice(productFacts: ProductFacts?): PriceResearchResult {
        val prompt = buildResearchPrompt(productFacts)
        val response = messages(prompt)
        return parseJsonResponse(response) ?: throw IllegalStateException("Failed to parse research response")
    }

    override suspend fun healthCheck(): ProviderHealthStatus {
        try {
            messages("Say 'healthy'. Respond ONLY with that word.")
            healthStatus = ProviderHealthStatus.HEALTHY
        } catch (e: Exception) {
            healthStatus = ProviderHealthStatus.UNAVAILABLE
        }
        return healthStatus
    }

    private fun messages(prompt: String): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/v1/messages"))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IllegalStateException("Anthropic API error ${response.statusCode()}: ${response.body()}")
        }
        val root = json.parseToJsonElement(response.body()).jsonObject
        return root["content"]?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Unexpected Anthropic response")
    }

    private fun buildVisionPrompt(task: String, images: List<String>, description: String? = null): String {
        return when (task) {
            "identify" -> buildString {
                appendLine("Analyze these product images and identify the item. Return ONLY JSON matching OpenAI Provider schema.")
                if (description != null) appendLine("User description: $description")
            }
            "condition" -> buildString {
                appendLine("Assess the visible condition of this product from images. Return ONLY JSON matching condition schema.")
            }
            else -> "Analyze images and respond in JSON."
        }
    }

    private fun buildListingPrompt(request: ListingDraftRequest): String {
        return "Generate a ${request.platform} listing for ${request.productFacts.title}. Return ONLY JSON."
    }

    private fun buildMissingQuestionsPrompt(productFacts: ProductFacts?, condition: ConditionAssessment?): String {
        return "What questions should the seller answer about ${productFacts?.title ?: "this product"}? Return ONLY JSON array."
    }

    private fun buildResearchPrompt(productFacts: ProductFacts?): String {
        return "Estimate used price for ${productFacts?.title ?: "unknown product"} in EUR. Return ONLY JSON."
    }

    private inline fun <reified T> parseJsonResponse(jsonStr: String): T? {
        return try { json.decodeFromString(jsonStr) } catch (e: Exception) { null }
    }
    private fun parseListResponse(jsonStr: String): List<String>? {
        return try { json.decodeFromString(jsonStr) } catch (e: Exception) { null }
    }
}
