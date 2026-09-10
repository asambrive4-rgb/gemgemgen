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
        val variationGoal = root["variationGoal"].stringOrBlank().trim()
        val warnings = root["warnings"].strings()
        val trace = root["cascadingTrace"]?.jsonObjectOrNull()

        return AnalysisReport(
            targetSegment = targetSegment,
            visualContext = visualContext,
            spatialLayout = spatialLayout,
            categoryConstraints = constraints,
            variationGoal = variationGoal,
            warnings = warnings,
            targetVisualContext = root["targetVisualContext"]?.jsonObjectOrNull()?.let(::parseVisualContext)
                ?: AnalysisVisualContext(),
            cascadingTrace = AnalysisCascadingTrace(
                newlyVisible = trace?.get("newlyVisible").strings(),
                disappearing = trace?.get("disappearing").strings(),
                undefinedAreas = trace?.get("undefinedAreas").strings(),
                requiredAdjustments = trace?.get("requiredAdjustments").strings(),
                conflictingSegments = parseRanges(trace?.get("conflictingSegments"), sourcePrompt)
            ),
            preservedSegments = parseRanges(root["preservedSegments"], sourcePrompt),
            clarificationQuestion = root["clarificationQuestion"].stringOrBlank().trim()
        )
    }

    fun parseEditCandidates(jsonText: String): List<List<AnalysisTextEdit>> {
        val items = runCatching { json.parseToJsonElement(jsonText).jsonArray }.getOrElse {
            throw AnalysisParseException("편집 후보 목록이 올바른 JSON 배열이 아닙니다.")
        }
        return items.map { item ->
            val edits = item.jsonObjectOrNull()?.get("edits")?.jsonArrayOrNull()
                ?: throw AnalysisParseException("편집 후보에 edits 목록이 없습니다.")
            edits.map { element ->
                val edit = element.jsonObjectOrNull()
                    ?: throw AnalysisParseException("편집 항목 형식이 올바르지 않습니다.")
                val replacement = edit["replacement"] as? kotlinx.serialization.json.JsonPrimitive
                if (replacement == null || !replacement.isString) {
                    throw AnalysisParseException("편집 항목의 replacement 문자열이 없습니다.")
                }
                AnalysisTextEdit(parseRange(edit), replacement.content)
            }
        }
    }

    private fun parseRanges(element: JsonElement?, source: String): List<AnalysisSourceRange> {
        if (element == null) return emptyList()
        val items = element.jsonArrayOrNull()
            ?: throw AnalysisParseException("원문 편집 범위 목록 형식이 올바르지 않습니다.")
        return items.map {
            val range = parseRange(it.jsonObjectOrNull()
                ?: throw AnalysisParseException("원문 편집 범위 형식이 올바르지 않습니다."))
            AnalysisSourceLocator.resolve(source, range)
        }
    }

    private fun parseRange(obj: JsonObject): AnalysisSourceRange {
        val start = obj["startIndex"].stringOrBlank().toIntOrNull() ?: -1
        val end = obj["endIndex"].stringOrBlank().toIntOrNull() ?: -1
        val text = obj["exactText"] as? kotlinx.serialization.json.JsonPrimitive
        if (text == null || !text.isString) {
            throw AnalysisParseException("편집할 원문 문자열이 없습니다.")
        }
        return AnalysisSourceRange(start, end, text.content,
            obj["occurrence"].stringOrBlank().toIntOrNull())
    }

    fun parseTxtCandidates(jsonText: String): List<String> {
        val root = try {
            json.parseToJsonElement(jsonText)
        } catch (error: RuntimeException) {
            throw AnalysisParseException(
                "AI 응답을 JSON 형식으로 해석하지 못했습니다. (후보 목록 형식이 올바르지 않습니다.)"
            )
        }
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
            throw AnalysisParseException(
                "AI 응답을 JSON 형식으로 해석하지 못했습니다. (모델 응답이 올바른 형식이 아니거나 중간에 끊겼을 수 있습니다.)"
            )
        }
    }

    private fun parseTargetSegment(
        obj: JsonObject,
        sourcePrompt: String
    ): AnalysisDetectedSegment? {
        val exactText = obj["exactText"].stringOrBlank()
        if (exactText.isBlank()) return null
        val confidence = obj["confidence"].doubleOrNull() ?: 0.5
        val reason = obj["reason"].stringOrBlank()
        val resolved = runCatching { AnalysisSourceLocator.resolve(sourcePrompt, parseRange(obj)) }
        return resolved.fold(
            onSuccess = {
                AnalysisDetectedSegment(it.exactText, it.startIndex, it.endIndex, confidence, reason)
            },
            onFailure = {
                AnalysisDetectedSegment(exactText, -1, -1, 0.1,
                    it.message ?: reason.ifBlank { "수정 대상 문구를 원문에서 찾지 못했습니다." })
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
