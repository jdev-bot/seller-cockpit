package com.sellercockpit.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ConditionAssessment(
    val condition: ProductCondition,
    val visibleDefects: List<String> = emptyList(),
    val functionalityConfirmed: Boolean? = null,
    val missingInformation: List<String> = emptyList(),
    val confidence: Double = 0.0,
    val userConfirmed: Boolean = false
)
