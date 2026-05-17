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

/** ChatGPT reverse-proxy adapter.
 *
 * ⚠️ WARNING: This adapter uses unofficial/unstable endpoints.
 * It requires a reverse proxy like `pandora` or `go-chatgpt-api`
 * that translates between ChatGPT's web session and an OpenAI-compatible API.
 *
 * This is NOT officially supported, breaks frequently, and may violate OpenAI ToS.
 * It exists only for users who explicitly want to use their ChatGPT Plus subscription.
 *
 * To use:
 * 1. Set up a reverse proxy (e.g., `https://github.com/pengzhile/pandora`)
 * 2. Configure `ai.chatgpt-proxy.base-url` to your proxy
 * 3. Provide your proxy access token as `ai.chatgpt-proxy.api-key`
 */
@ApplicationScoped
class ChatGPTProxyProvider @Inject constructor(
    @ConfigProperty(name = "ai.chatgpt-proxy.api-key", defaultValue = "") private val apiKey: String,
    @ConfigProperty(name = "ai.chatgpt-proxy.base-url", defaultValue = "") private val baseUrl: String,
    @ConfigProperty(name = "ai.chatgpt-proxy.model", defaultValue = "gpt-4") private val model: String
) : AIProvider {

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
    private val json = Json { ignoreUnknownKeys = true }

    override val name = "chatgpt-proxy"
    override val supportsVision = true
    override val supportsStructuredOutput = false
    override val supportsStreaming = false
    override var healthStatus = ProviderHealthStatus.NOT_CONFIGURED

    init {
        if (apiKey.isNotBlank() && apiKey != "placeholder" && baseUrl.isNotBlank()) {
            healthStatus = ProviderHealthStatus.HEALTHY
        }
    }

    override suspend fun identifyProduct(images: List<String>, description: String?): ProductRecognitionResult {
        val prompt = buildVisionPrompt("identify", images)
        val response = chatCompletion(prompt)
        return parseJsonResponse(response) ?: throw IllegalStateException("Failed to parse proxy response")
    }

    override suspend fun assessCondition(images: List<String>, productFacts: ProductFacts?): ConditionAssessmentResult {
        val prompt = buildVisionPrompt("condition", images)
        val response = chatCompletion(prompt)
        return parseJsonResponse(response) ?: throw IllegalStateException("Failed to parse condition response")
    }

    override suspend fun generateListingDraft(request: ListingDraftRequest): ListingDraftResult {
        val response = chatCompletion("Generate ${request.platform} listing JSON for: ${request.productFacts.title}")
        return parseJsonResponse(response) ?: throw IllegalStateException("Failed to parse draft response")
    }

    override suspend fun generateMissingQuestions(productFacts: ProductFacts?, condition: ConditionAssessment?): List<String> {
        val response = chatCompletion("Missing questions for ${productFacts?.title ?: "product"}. Return JSON array.")
        return parseListResponse(response) ?: emptyList()
    }

    override suspend fun researchPrice(productFacts: ProductFacts?): PriceResearchResult {
        val response = chatCompletion("Price research EUR for ${productFacts?.title ?: "product"}. Return JSON.")
        return parseJsonResponse(response) ?: throw IllegalStateException("Failed to parse research response")
    }

    override suspend fun healthCheck(): ProviderHealthStatus {
        try {
            chatCompletion("Say healthy only.")
            healthStatus = ProviderHealthStatus.HEALTHY
        } catch (e: Exception) {
            healthStatus = ProviderHealthStatus.UNAVAILABLE
        }
        return healthStatus
    }

    private fun chatCompletion(prompt: String): String {
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/v1/chat/completions"))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IllegalStateException("ChatGPT proxy error ${response.statusCode()}: ${response.body()}")
        }
        val root = json.parseToJsonElement(response.body()).jsonObject
        return root["choices"]?.jsonArray?.get(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Unexpected proxy response")
    }

    private fun buildVisionPrompt(task: String, images: List<String>): String {
        val imageUrls = images.joinToString("\n") { "Image: $it" }
        return when (task) {
            "identify" -> "Identify product from images.\n$imageUrls\nReturn ONLY JSON with fields: title, brand, model, category, variant, color, sizeOrCapacity, identifierType, identifierValue, accessories[], confidence, reasoning"
            "condition" -> "Assess condition from images.\n$imageUrls\nReturn ONLY JSON with: condition, visibleDefects[], functionalityConfirmed (null if unknown), missingInformation[], confidence, reasoning"
            else -> "Analyze images. Respond in JSON.\n$imageUrls"
        }
    }

    private inline fun <reified T> parseJsonResponse(jsonStr: String): T? {
        return try { json.decodeFromString(jsonStr) } catch (e: Exception) { null }
    }
    private fun parseListResponse(jsonStr: String): List<String>? {
        return try { json.decodeFromString<List<String>>(jsonStr) } catch (e: Exception) { null }
    }
}
