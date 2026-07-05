package com.example.gemgemgen.analysis.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AnalysisParseException(message: String) : RuntimeException(message)

object AnalysisResponseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseReport(jsonText: String, sourcePrompt: String): AnalysisReport {
        val root = parseObject(jsonText)
        val targetSegment = root["targetSegment"]?.jsonObjectOrNull()?.let {
            parseTargetSegment(it, sourcePrompt)
        }
        val visualContext = root["visualContext"]?.jsonObjectOrNull()?.let(::parseVisualContext)
            ?: AnalysisVisualContext()
        val spatialLayout = root["spatialLayout"]?.jsonObjectOrNull()?.let(::parseSpatialLayout)
            ?: AnalysisSpatialLayout()
        val constraints = root["categoryConstraints"]?.jsonObjectOrNull()?.let {
            AnalysisCategoryConstraints(
                allowed = it["allowed"].strings(),
                avoid = it["avoid"].strings()
            )
        } ?: AnalysisCategoryConstraints()
        val warnings = root["warnings"].strings()

        return AnalysisReport(
            targetSegment = targetSegment,
            visualContext = visualContext,
            spatialLayout = spatialLayout,
            categoryConstraints = constraints,
            warnings = warnings
        )
    }

    fun parseTxtCandidates(jsonText: String): List<String> {
        val root = json.parseToJsonElement(jsonText)
        val items = when (root) {
            is JsonArray -> root
            is JsonObject -> root["items"]?.jsonArrayOrNull() ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }

        return items.mapNotNull { element ->
            element.jsonObjectOrNull()
                ?.get("text")
                ?.stringOrBlank()
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun parseObject(jsonText: String): JsonObject {
        return try {
            json.parseToJsonElement(jsonText).jsonObject
        } catch (error: RuntimeException) {
            throw AnalysisParseException("AI 응답 JSON을 해석하지 못했습니다.")
        }
    }

    private fun parseTargetSegment(
        obj: JsonObject,
        sourcePrompt: String
    ): AnalysisDetectedSegment? {
        val exactText = obj["exactText"].stringOrBlank().trim()
        if (exactText.isBlank()) return null

        val reportedConfidence = obj["confidence"].doubleOrNull() ?: 0.5
        val reason = obj["reason"].stringOrBlank()
        val directIndex = sourcePrompt.indexOf(exactText)
        if (directIndex >= 0) {
            return AnalysisDetectedSegment(
                exactText = exactText,
                startIndex = directIndex,
                endIndex = directIndex + exactText.length,
                confidence = reportedConfidence,
                reason = reason
            )
        }

        val lowerIndex = sourcePrompt.lowercase().indexOf(exactText.lowercase())
        if (lowerIndex >= 0) {
            val fixedText = sourcePrompt.substring(lowerIndex, lowerIndex + exactText.length)
            return AnalysisDetectedSegment(
                exactText = fixedText,
                startIndex = lowerIndex,
                endIndex = lowerIndex + exactText.length,
                confidence = minOf(reportedConfidence, 0.8),
                reason = reason.ifBlank { "대소문자가 조정되었습니다." }
            )
        }

        return AnalysisDetectedSegment(
            exactText = exactText,
            startIndex = -1,
            endIndex = -1,
            confidence = 0.1,
            reason = reason.ifBlank {
                "원본 텍스트와 추출 문구가 일치하지 않습니다. 수동 선택이 필요합니다."
            }
        )
    }

    private fun parseVisualContext(obj: JsonObject): AnalysisVisualContext {
        return AnalysisVisualContext(
            viewpoint = obj["viewpoint"].stringOrDefault("알 수 없음"),
            distance = obj["distance"].stringOrDefault("알 수 없음"),
            visibleScope = obj["visibleScope"].stringOrDefault("알 수 없음"),
            cameraAngle = obj["cameraAngle"].stringOrDefault("알 수 없음"),
            visibleElements = obj["visibleElements"].strings(),
            hiddenOrUnclearElements = obj["hiddenOrUnclearElements"].strings()
        )
    }

    private fun parseSpatialLayout(obj: JsonObject): AnalysisSpatialLayout {
        return AnalysisSpatialLayout(
            subjectPlacement = obj["subjectPlacement"].stringOrBlank(),
            foreground = obj["foreground"].strings(),
            midground = obj["midground"].strings(),
            background = obj["background"].strings(),
            leftSide = obj["leftSide"].strings(),
            center = obj["center"].strings(),
            rightSide = obj["rightSide"].strings(),
            above = obj["above"].strings(),
            below = obj["below"].strings(),
            behindSubject = obj["behindSubject"].strings(),
            besideSubject = obj["besideSubject"].strings(),
            fixedAnchors = obj["fixedAnchors"].strings(),
            mutableZones = obj["mutableZones"].strings()
        )
    }

    private fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement?.jsonArrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonElement?.stringOrBlank(): String {
        return runCatching { this?.jsonPrimitive?.content.orEmpty() }.getOrDefault("")
    }

    private fun JsonElement?.stringOrDefault(default: String): String {
        return stringOrBlank().ifBlank { default }
    }

    private fun JsonElement?.doubleOrNull(): Double? {
        return runCatching { this?.jsonPrimitive?.content?.toDoubleOrNull() }.getOrNull()
    }

    private fun JsonElement?.strings(): List<String> {
        val array = jsonArrayOrNull() ?: return emptyList()
        return array.mapNotNull { it.stringOrBlank().takeIf(String::isNotBlank) }
    }
}
