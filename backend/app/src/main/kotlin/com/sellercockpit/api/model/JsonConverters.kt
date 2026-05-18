package com.sellercockpit.api.model

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Converter
class JsonMapConverter : AttributeConverter<Map<String, String>?, String> {
    override fun convertToDatabaseColumn(attribute: Map<String, String>?): String? {
        return attribute?.let { Json.encodeToString(it) }
    }

    override fun convertToEntityAttribute(dbData: String?): Map<String, String>? {
        if (dbData.isNullOrBlank() || dbData == "null") return null
        return Json.decodeFromString(dbData)
    }
}

@Converter
class JsonStringListConverter : AttributeConverter<List<String>?, String> {
    override fun convertToDatabaseColumn(attribute: List<String>?): String? {
        return attribute?.let { Json.encodeToString(it) }
    }

    override fun convertToEntityAttribute(dbData: String?): List<String>? {
        if (dbData.isNullOrBlank() || dbData == "null") return null
        return Json.decodeFromString(dbData)
    }
}
