package com.sellercockpit.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ProductFacts(
    val title: String,
    val brand: String? = null,
    val model: String? = null,
    val category: String? = null,
    val variant: String? = null,
    val color: String? = null,
    val sizeOrCapacity: String? = null,
    val identifiers: List<ProductIdentifier> = emptyList(),
    val accessories: List<String> = emptyList(),
    val userConfirmed: Boolean = false,
    val confidence: Double = 0.0
)

@Serializable
data class ProductIdentifier(
    val type: String,
    val value: String
)
