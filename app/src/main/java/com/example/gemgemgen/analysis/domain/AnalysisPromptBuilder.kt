package com.example.gemgemgen.analysis.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class AnalysisPromptPayload(
    val systemInstruction: String,
    val userPrompt: String,
    val responseSchema: JsonObject
)

data class AnalysisTxtPromptPayload(
    val systemInstruction: String,
    val userPrompt: String,
    val responseSchema: JsonObject
)

object AnalysisPromptBuilder {
    fun buildAnalysisPrompt(
        sourcePrompt: String,
        category: AnalysisCategory,
        selectedHints: List<String> = emptyList(),
        customHint: String? = null
    ): AnalysisPromptPayload {
        val rule = AnalysisCategoryRules.ruleFor(category)
        val sharedRules = AnalysisVisualRules.instructions
        val directionHintsText = formatDirectionHints(selectedHints)
        val customHintText = customHint?.trim().orEmpty().ifBlank { "NONE" }
        val systemInstruction = """
You are a high-fidelity image prompt engineering expert specializing in prompt analysis and precision reconstruction.
Analyze a full image prompt for the target variation category: "${category.label}".

Category Rules:
- Required:
${rule.required}
- Avoid:
${rule.avoid}
- Variables: ${rule.variables}
${if (rule.output.isBlank()) "" else "- Output: ${rule.output}"}

$sharedRules

Editing scope:
- targetSegment is the primary direct edit. cascadingTrace.conflictingSegments must locate ALL additional source sentences requiring removal/modification or insertion anchors, including distant negatives and final emphasis. Quote source text in exactText; the app finds character positions. Do not count character indices. For repeated text, include enough context or specify its 1-based occurrence. Never merge unrelated intervening text into a rewrite.
- preservedSegments must include the entire original System Instruction section unless the user explicitly requests editing it, plus any explicitly protected source passages.
- clarificationQuestion is a single concise Korean question ONLY when a materially different unsupported choice prevents editing. Otherwise return an empty string.

Goals:
1. Find the exact substring in the original prompt that represents the target category.
2. Return the source phrase in exactText. Use occurrence only to distinguish repeated phrases; do not calculate character positions.
3. Analyze viewpoint, distance, visible scope, camera angle, visible elements, hidden or unclear elements.
4. Analyze spatial layout: subject placement, foreground, midground, background, left/center/right, above/below, behind/beside subject, fixed anchors, mutable zones.
5. Return category constraints: allowed and avoid.
6. Infer variationGoal: one clear Korean sentence describing what replacement fragments should achieve for this category.
   Base variationGoal on ALL of:
   (a) the visual/spatial analysis of the source prompt,
   (b) selected direction chips if any (length, detail, lineage shift, situational edge, etc.),
   (c) the custom user direction if any (translating abstract requests into concrete visual terms).
   Do NOT default to a fixed short/practical style unless the source and user directions actually imply that.
   If chips or custom text ask for more length or denser detail, variationGoal must reflect that.
   If chips or custom text ask for a lineage/genre shift or situational edge, variationGoal must reflect that.
7. Keep the response strict JSON only.
        """.trimIndent()

        val userPrompt = """
Target prompt:
"$sourcePrompt"

Target category:
"${category.label}"

Selected direction chips:
$directionHintsText

Custom user direction:
$customHintText

Find the target segment, analyze the visual constraints, and set variationGoal from analysis + chips + custom direction. Return strict JSON.
        """.trimIndent()

        return AnalysisPromptPayload(
            systemInstruction = systemInstruction,
            userPrompt = userPrompt,
            responseSchema = analysisResponseSchema()
        )
    }

    fun buildTxtPrompt(
        sourcePrompt: String,
        category: AnalysisCategory,
        targetSegment: AnalysisTargetSegment,
        analysisReport: AnalysisReport,
        count: Int,
        selectedHints: List<String>,
        customHint: String? = null
    ): AnalysisTxtPromptPayload {
        val avoidRules = analysisReport.categoryConstraints.avoid
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n") { "* $it" }
            ?: "* NONE"
        val allowedRules = analysisReport.categoryConstraints.allowed
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n") { "* $it" }
            ?: "* NONE"
        val hintText = selectedHints
            .takeIf { it.isNotEmpty() }
            ?.mapIndexed { index, hint -> "Option ${index + 1}: \"$hint\"" }
            ?.joinToString(separator = "\n")
            .orEmpty()
        val layoutText = spatialLayoutText(analysisReport.spatialLayout)
        val variationGoal = analysisReport.variationGoal.trim().ifBlank {
            AnalysisCategoryRules.ruleFor(category).goal
        }

        val rule = AnalysisCategoryRules.ruleFor(category)
        val outputRules = if (category == AnalysisCategory.FREE_EDIT) {
            """
Return each candidate as an edits array and explanation, not a full rewritten text.
Each edit has exactText (source phrase to locate) and replacement. The app locates it; do not calculate start/end character indices. If the same phrase repeats, include more context or give its 1-based occurrence in Original prompt.
Use an empty replacement for deletion. For additions, quote an existing anchor phrase in exactText and return that phrase plus the added text in replacement. Do not use an empty exactText or guessed numeric insertion position. Combine overlapping changes into one edit.
Only edit within [${targetSegment.startIndex}, ${targetSegment.endIndex}). Preserve all intervening unedited text; the app assembles it verbatim.
Suggested edit locations: ${targetSegment.editableRanges}. These guide the task; refine sentence boundaries as needed within the replacement segment. Preserve unrelated text between edits.
Resolve the conflicts identified in conflictingSegments with the necessary edits; do not mechanically replace whole sentences when a smaller change resolves them. Never edit preservedSegments. Use the original language/style for replacements; do not translate untouched text.
            """.trimIndent()
        } else {
            "Output Korean wildcard fragments in the text field, without numbering, labels or full prompts. Use one line per fragment."
        }
        val systemInstruction = """
You generate precise image-prompt edits or Korean wildcard fragments according to the output contract.
Generate exactly $count candidates for "${category.label}".
$outputRules

${AnalysisVisualRules.instructions}

Category Rules:
- Required: ${rule.required}
- Avoid: ${rule.avoid}
- Variables: ${rule.variables}
- Output style (subject to editing output contract): ${rule.output}

Variation goal (from analysis; follow this as the primary creative objective):
$variationGoal

Critical rules:
1. Keep viewpoint, distance, scope and layout stable unless user directions or essential physical dependencies require changes. Resolve targetVisualContext from the requested changes, not a blanket lock to current visualContext.
2. For target rear/side-back views, omit frontal details unless visible in that target view. For target crops excluding lower body/floor, omit shoes/ground details; this restriction does not apply to newly exposed target areas.
3. Match the user's requested detail and length; category brevity defaults do not override explicitly requested necessary detail.
4. Prefer Variation goal for length, lineage, tone and emphasis, subject to shared preservation rules and explicit user directions. Category examples are optional, not mandatory changes.
5. Return strict JSON array only, following the output contract above.

Context:
- Variation goal: $variationGoal
- Current viewpoint: ${analysisReport.visualContext.viewpoint}
- Distance: ${analysisReport.visualContext.distance}
- Current visible scope: ${analysisReport.visualContext.visibleScope}
- Target visual context: ${analysisReport.targetVisualContext}
- Five cascading checks: ${analysisReport.cascadingTrace}
- Protected source ranges: ${analysisReport.preservedSegments}
- Camera angle: ${analysisReport.visualContext.cameraAngle}
- Avoid:
$avoidRules
- Allowed:
$allowedRules
${if (layoutText.isBlank()) "" else "- Spatial layout:\n$layoutText"}
        """.trimIndent()

        val userPrompt = """
Original prompt:
"$sourcePrompt"

Replace this segment:
"${targetSegment.text}"

Category: "${category.label}"
${if (hintText.isBlank()) "" else "Selected dummy direction hints:\n$hintText"}
${if (customHint.isNullOrBlank()) "" else "Custom user direction hint:\n$customHint"}

Generate exactly $count unique candidates as a JSON array following the system output contract.
        """.trimIndent()

        return AnalysisTxtPromptPayload(
            systemInstruction = systemInstruction,
            userPrompt = userPrompt,
            responseSchema = txtResponseSchema(category)
        )
    }

    private fun formatDirectionHints(selectedHints: List<String>): String {
        if (selectedHints.isEmpty()) return "NONE"
        return selectedHints
            .mapIndexed { index, hint -> "Option ${index + 1}: \"$hint\"" }
            .joinToString(separator = "\n")
    }

    private fun spatialLayoutText(layout: AnalysisSpatialLayout): String {
        return listOfNotNull(
            layout.subjectPlacement.takeIf { it.isNotBlank() }?.let { "* Subject placement: $it" },
            layout.foreground.line("Foreground"),
            layout.midground.line("Midground"),
            layout.background.line("Background"),
            layout.leftSide.line("Left side"),
            layout.center.line("Center"),
            layout.rightSide.line("Right side"),
            layout.above.line("Above"),
            layout.below.line("Below"),
            layout.behindSubject.line("Behind subject"),
            layout.besideSubject.line("Beside subject"),
            layout.fixedAnchors.line("Fixed anchors"),
            layout.mutableZones.line("Mutable zones")
        ).joinToString(separator = "\n")
    }

    private fun List<String>.line(label: String): String? {
        return takeIf { it.isNotEmpty() }?.joinToString(prefix = "* $label: ")
    }

    private fun analysisResponseSchema(): JsonObject {
        return obj(
            "type" to "object",
            "properties" to obj(
                "targetSegment" to obj(
                    "type" to "object",
                    "properties" to obj(
                        "exactText" to stringSchema("Exact substring from the original prompt."),
                        "occurrence" to integerSchema("1-based occurrence when exactText repeats; otherwise omit."),
                        "confidence" to numberSchema("0.0 to 1.0 confidence."),
                        "reason" to stringSchema("Korean reason.")
                    ),
                    "required" to arr("exactText", "confidence", "reason")
                ),
                "visualContext" to obj(
                    "type" to "object",
                    "properties" to obj(
                        "viewpoint" to stringSchema("Subject viewing angle in Korean."),
                        "distance" to stringSchema("Camera distance in Korean."),
                        "visibleScope" to stringSchema("Visible crop/scope in Korean."),
                        "cameraAngle" to stringSchema("Camera angle in Korean."),
                        "visibleElements" to stringArraySchema(),
                        "hiddenOrUnclearElements" to stringArraySchema()
                    ),
                    "required" to arr(
                        "viewpoint",
                        "distance",
                        "visibleScope",
                        "cameraAngle",
                        "visibleElements",
                        "hiddenOrUnclearElements"
                    )
                ),
                "spatialLayout" to obj(
                    "type" to "object",
                    "properties" to obj(
                        "subjectPlacement" to stringSchema("Subject location."),
                        "foreground" to stringArraySchema(),
                        "midground" to stringArraySchema(),
                        "background" to stringArraySchema(),
                        "leftSide" to stringArraySchema(),
                        "center" to stringArraySchema(),
                        "rightSide" to stringArraySchema(),
                        "above" to stringArraySchema(),
                        "below" to stringArraySchema(),
                        "behindSubject" to stringArraySchema(),
                        "besideSubject" to stringArraySchema(),
                        "fixedAnchors" to stringArraySchema(),
                        "mutableZones" to stringArraySchema()
                    )
                ),
                "categoryConstraints" to obj(
                    "type" to "object",
                    "properties" to obj(
                        "allowed" to stringArraySchema(),
                        "avoid" to stringArraySchema()
                    ),
                    "required" to arr("allowed", "avoid")
                ),
                "variationGoal" to stringSchema(
                    "One clear Korean sentence: the variation goal for replacement fragments, " +
                        "based on visual analysis, selected direction chips, and custom user direction."
                ),
                "targetVisualContext" to obj(
                    "type" to "object",
                    "properties" to obj(
                        "viewpoint" to stringSchema("Target view after requested changes."),
                        "distance" to stringSchema("Target camera distance."),
                        "visibleScope" to stringSchema("Target crop after expansion or tightening."),
                        "cameraAngle" to stringSchema("Target angle."),
                        "visibleElements" to stringArraySchema(),
                        "hiddenOrUnclearElements" to stringArraySchema()
                    ),
                    "required" to arr("viewpoint", "distance", "visibleScope", "cameraAngle", "visibleElements", "hiddenOrUnclearElements")
                ),
                "cascadingTrace" to obj(
                    "type" to "object",
                    "properties" to obj(
                        "newlyVisible" to stringArraySchema(),
                        "disappearing" to stringArraySchema(),
                        "undefinedAreas" to stringArraySchema(),
                        "requiredAdjustments" to stringArraySchema(),
                        "conflictingSegments" to obj("type" to "array", "items" to sourceRangeSchema())
                    ),
                    "required" to arr("newlyVisible", "disappearing", "undefinedAreas", "requiredAdjustments", "conflictingSegments")
                ),
                "preservedSegments" to obj("type" to "array", "items" to sourceRangeSchema()),
                "clarificationQuestion" to stringSchema("Empty unless an essential unsupported choice needs clarification; never ask for images."),
                "warnings" to stringArraySchema()
            ),
            "required" to arr(
                "targetSegment",
                "visualContext",
                "spatialLayout",
                "categoryConstraints",
                "variationGoal",
                "targetVisualContext",
                "cascadingTrace",
                "preservedSegments",
                "clarificationQuestion",
                "warnings"
            )
        )
    }

    private fun sourceRangeSchema(): JsonObject = obj(
        "type" to "object",
        "properties" to obj(
            "occurrence" to integerSchema("1-based occurrence in the original prompt if this phrase repeats; otherwise omit."),
            "exactText" to stringSchema("Exact untrimmed source substring, including whitespace.")
        ),
        "required" to arr("exactText")
    )

    private fun txtResponseSchema(category: AnalysisCategory): JsonObject {
        if (category == AnalysisCategory.FREE_EDIT) {
            val editProperties = sourceRangeSchema()["properties"] as JsonObject
            val editSchema = obj(
                "type" to "object",
                "properties" to JsonObject(editProperties + ("replacement" to stringSchema("Replacement only; empty for deletion."))),
                "required" to arr("exactText", "replacement")
            )
            return obj(
                "type" to "array",
                "items" to obj(
                    "type" to "object",
                    "properties" to obj(
                        "edits" to obj("type" to "array", "items" to editSchema),
                        "explanation" to stringSchema("Short Korean explanation.")
                    ),
                    "required" to arr("edits", "explanation")
                )
            )
        }
        return obj(
            "type" to "array",
            "items" to obj(
                "type" to "object",
                "properties" to obj(
                    "text" to stringSchema("Korean wildcard candidate fragment only."),
                    "explanation" to stringSchema("Short Korean explanation.")
                ),
                "required" to arr("text", "explanation")
            )
        )
    }

    private fun stringSchema(description: String): JsonObject {
        return obj("type" to "string", "description" to description)
    }

    private fun integerSchema(description: String): JsonObject {
        return obj("type" to "integer", "description" to description)
    }

    private fun numberSchema(description: String): JsonObject {
        return obj("type" to "number", "description" to description)
    }

    private fun stringArraySchema(): JsonObject {
        return obj("type" to "array", "items" to obj("type" to "string"))
    }

    private fun obj(vararg entries: Pair<String, Any>): JsonObject {
        return buildJsonObject {
            entries.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, JsonPrimitive(value))
                    is JsonObject -> put(key, value)
                    is JsonArray -> put(key, value)
                    else -> error("Unsupported JSON value for $key")
                }
            }
        }
    }

    private fun arr(vararg values: String): JsonArray {
        return buildJsonArray {
            values.forEach { add(JsonPrimitive(it)) }
        }
    }
}
