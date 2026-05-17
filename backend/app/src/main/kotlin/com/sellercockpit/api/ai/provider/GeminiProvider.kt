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

/** Google Gemini adapter (via Vertex AI or direct Gemini API). */
@ApplicationScoped
class GeminiProvider @Inject constructor(
    @ConfigProperty(name = "ai.gemini.api-key", defaultValue = "") private val apiKey: String,
    @ConfigProperty(name = "ai.gemini.base-url", defaultValue = "https://generativelanguage.googleapis.com/v1beta") private val baseUrl: String,
    @ConfigProperty(name = "ai.gemini.model", defaultValue = "gemini-1.5-pro-latest") private val model: String
) : AIProvider {

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
    private val json = Json { ignoreUnknownKeys = true }

    override val name = "gemini"
    override val supportsVision = true
    override val supportsStructuredOutput = false
    override val supportsStreaming = false
    override var healthStatus = ProviderHealthStatus.NOT_CONFIGURED

    init {
        if (apiKey.isNotBlank() && apiKey != "placeholder") healthStatus = ProviderHealthStatus.HEALTHY
    }

    override suspend fun identifyProduct(images: List<String>, description: String?): ProductRecognitionResult {
        val text = generateContent(buildVisionPrompt("identify"), images)
        return parseJsonResponse(text) ?: throw IllegalStateException("Failed to parse Gemini response")
    }

    override suspend fun assessCondition(images: List<String>, productFacts: ProductFacts?): ConditionAssessmentResult {
        val text = generateContent(buildVisionPrompt("condition"), images)
        return parseJsonResponse(text) ?: throw IllegalStateException("Failed to parse Gemini condition response")
    }

    override suspend fun generateListingDraft(request: ListingDraftRequest): ListingDraftResult {
        val text = generateContent("Generate ${request.platform} listing JSON for: ${request.productFacts.title}")
        return parseJsonResponse(text) ?: throw IllegalStateException("Failed to parse draft response")
    }

    override suspend fun generateMissingQuestions(productFacts: ProductFacts?, condition: ConditionAssessment?): List<String> {
        val text = generateContent("Missing questions for ${productFacts?.title ?: "product"}. Return JSON array.")
        return parseListResponse(text) ?: emptyList()
    }

    override suspend fun researchPrice(productFacts: ProductFacts?): PriceResearchResult {
        val text = generateContent("Price research EUR for ${productFacts?.title ?: "product"}. Return JSON.")
        return parseJsonResponse(text) ?: throw IllegalStateException("Failed to parse research response")
    }

    override suspend fun healthCheck(): ProviderHealthStatus {
        try {
            generateContent("Say healthy only.")
            healthStatus = ProviderHealthStatus.HEALTHY
        } catch (e: Exception) {
            healthStatus = ProviderHealthStatus.UNAVAILABLE
        }
        return healthStatus
    }

    private fun generateContent(prompt: String, images: List<String> = emptyList()): String {
        val parts = mutableListOf<JsonObject>()
        parts.add(buildJsonObject { put("text", prompt) })

        images.forEach { url ->
            // For base64 inline images, we'd decode. For URLs, Gemini can fetch if publicly accessible
            parts.add(buildJsonObject {
                putJsonObject("file_data") {
                    put("mime_type", "image/jpeg")
                    put("uri", url)
                }
            })
        }

        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        parts.forEach { add(it) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
            }
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/models/$model:generateContent?key=$apiKey"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IllegalStateException("Gemini API error ${response.statusCode()}: ${response.body()}")
        }

        val root = json.parseToJsonElement(response.body()).jsonObject
        return root["candidates"]?.jsonArray?.get(0)?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Unexpected Gemini response")
    }

    private fun buildVisionPrompt(task: String): String = when (task) {
        "identify" -> "Identify this product from images. Return ONLY JSON with: title, brand, model, category, variant, color, sizeOrCapacity, identifierType, identifierValue, accessories[], confidence, reasoning"
        "condition" -> "Assess condition from images. Return ONLY JSON with: condition (NEW/LIKE_NEW/USED_VERY_GOOD/USED_GOOD/USED_ACCEPTABLE/DEFECTIVE/UNKNOWN), visibleDefects[], functionalityConfirmed (or null if unknown), missingInformation[], confidence, reasoning"
        else -> "Analyze images. Respond in JSON."
    }

    private inline fun <reified T> parseJsonResponse(jsonStr: String): T? {
        return try { json.decodeFromString(jsonStr) } catch (e: Exception) { null }
    }
    private fun parseListResponse(jsonStr: String): List<String>? {
        return try { json.decodeFromString<List<String>>(jsonStr) } catch (e: Exception) { null }
    }
}
