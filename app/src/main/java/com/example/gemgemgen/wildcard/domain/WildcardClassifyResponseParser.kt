package com.example.gemgemgen.wildcard.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WildcardClassifyParseException(message: String) : RuntimeException(message)

object WildcardClassifyResponseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseGroups(jsonText: String): List<WildcardClassifyGroup> {
        val root = try {
            json.parseToJsonElement(jsonText)
        } catch (_: RuntimeException) {
            throw WildcardClassifyParseException("AI 응답 JSON을 해석하지 못했습니다.")
        }

        val groupsArray: JsonArray = when (root) {
            is JsonObject -> {
                val groups = root["groups"]
                    ?: throw WildcardClassifyParseException("AI 응답에 groups가 없습니다.")
                asJsonArray(groups)
            }
            is JsonArray -> root
            else -> throw WildcardClassifyParseException("AI 응답 형식이 올바르지 않습니다.")
        }

        return groupsArray.mapNotNull { element ->
            val obj = asJsonObjectOrNull(element) ?: return@mapNotNull null
            val name = asStringOrNull(obj["name"]).orEmpty().trim()
            if (name.isEmpty()) return@mapNotNull null
            val itemsElement = obj["items"]
            val items = if (itemsElement == null) {
                emptyList()
            } else {
                asJsonArray(itemsElement).mapNotNull { item ->
                    asStringOrNull(item)?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
            WildcardClassifyGroup(name = name, items = items)
        }
    }

    private fun asJsonObjectOrNull(element: JsonElement): JsonObject? =
        runCatching { element.jsonObject }.getOrNull()

    private fun asJsonArray(element: JsonElement): JsonArray =
        runCatching { element.jsonArray }.getOrElse {
            throw WildcardClassifyParseException("AI 응답 배열을 해석하지 못했습니다.")
        }

    private fun asStringOrNull(element: JsonElement?): String? =
        element?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
}
