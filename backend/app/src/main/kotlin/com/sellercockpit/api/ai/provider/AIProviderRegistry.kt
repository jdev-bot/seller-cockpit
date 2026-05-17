package com.sellercockpit.api.ai.provider

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class AIProviderRegistry @Inject constructor(
    private val providers: Instance<AIProvider>,
    @ConfigProperty(name = "ai.primary-provider", defaultValue = "openai") private val primaryProviderName: String,
    @ConfigProperty(name = "ai.fallback-provider", defaultValue = "anthropic") private val fallbackProviderName: String
) {
    fun getPrimary(): AIProvider? = providers.find { it.name == primaryProviderName }
        ?: providers.find { it.name == "mock" } // fallback to mock if configured provider unavailable

    fun getFallback(): AIProvider? = providers.find { it.name == fallbackProviderName }

    fun getProvider(name: String): AIProvider? = providers.find { it.name == name }

    fun listProviders(): List<AIProvider> = providers.toList()

    fun listAvailable(): List<AIProvider> = providers.filter { it.healthStatus == ProviderHealthStatus.HEALTHY }

    fun getAnyAvailable(): AIProvider? {
        val primary = getPrimary()
        if (primary != null && primary.healthStatus == ProviderHealthStatus.HEALTHY) return primary
        val fallback = getFallback()
        if (fallback != null && fallback.healthStatus == ProviderHealthStatus.HEALTHY) return fallback
        return listAvailable().firstOrNull()
    }
}
