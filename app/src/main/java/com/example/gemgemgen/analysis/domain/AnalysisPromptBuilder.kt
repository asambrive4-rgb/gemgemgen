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
        val specialRule = specialAnalyticRule(category)
        val directionHintsText = formatDirectionHints(selectedHints)
        val customHintText = customHint?.trim().orEmpty().ifBlank { "NONE" }
        val systemInstruction = """
You are a high-fidelity image prompt engineering expert.
Analyze a full image prompt for one target variation category.

Category: ${category.label}
- Required: ${rule.required}
- Avoid: ${rule.avoid}
- Variables: ${rule.variables}
${if (rule.output.isBlank()) "" else "- Output: ${rule.output}"}

Detailed camera-aware rules:
$specialRule

Goals:
1. Find the exact substring in the original prompt that represents the target category.
2. Return the exact 0-based startIndex and endIndex of that substring.
3. Analyze viewpoint, distance, visible scope, camera angle, visible elements, hidden or unclear elements.
4. Analyze spatial layout: subject placement, foreground, midground, background, left/center/right, above/below, behind/beside subject, fixed anchors, mutable zones.
5. Return category constraints: allowed and avoid.
6. Infer variationGoal: one clear Korean sentence describing what replacement fragments should achieve for this category.
   Base variationGoal on ALL of:
   (a) the visual/spatial analysis of the source prompt,
   (b) selected direction chips if any (length, detail, lineage shift, erotic edge, outfit-fits-location, etc.),
   (c) the custom user direction if any.
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

        val systemInstruction = """
You generate Korean wildcard candidate fragments for image prompts.
Generate exactly $count Korean fragments that can replace "${targetSegment.text}" for "${category.label}".

Variation goal (from analysis; follow this as the primary creative objective):
$variationGoal

Critical rules:
1. Output only Korean wildcard fragments in the "text" field.
2. Do not output full prompts, English sentences, numbering, bullets, prefixes, or suffix explanations.
3. Keep the original viewpoint, distance, visual scope, and spatial layout stable.
4. If the viewpoint is rear/side-back, do not describe frontal face details, lip color, eye makeup, or front-facing fringe unless explicitly visible.
5. If lower body or floor is cropped or unclear, do not describe shoes, socks, floor tiles, ground, or pavement details.
6. For location/background, include layout-aware structure, not short lazy place names.
7. For human categories, avoid injecting unrelated location details.
8. Match the level of detail and descriptive length of the user's direction hints. If the hints are highly detailed and long, generate outputs that are correspondingly rich and descriptive, rather than summarizing them into short 1-2 sentences.
9. Respect the style and format of the user's hints naturally (e.g., matching the overall tone or structure), but do not restrict the phrasing too strictly if it harms expression quality.
10. Prefer the Variation goal above when choosing length, lineage, tone, and emphasis; still obey camera/crop and allowed/avoid constraints.
11. Return strict JSON array only.

Context:
- Variation goal: $variationGoal
- Viewpoint: ${analysisReport.visualContext.viewpoint}
- Distance: ${analysisReport.visualContext.distance}
- Visible scope: ${analysisReport.visualContext.visibleScope}
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

Generate exactly $count unique Korean wildcard fragments as a JSON array.
        """.trimIndent()

        return AnalysisTxtPromptPayload(
            systemInstruction = systemInstruction,
            userPrompt = userPrompt,
            responseSchema = txtResponseSchema()
        )
    }

    private fun formatDirectionHints(selectedHints: List<String>): String {
        if (selectedHints.isEmpty()) return "NONE"
        return selectedHints
            .mapIndexed { index, hint -> "Option ${index + 1}: \"$hint\"" }
            .joinToString(separator = "\n")
    }

    private fun specialAnalyticRule(category: AnalysisCategory): String {
        return when (category) {
            AnalysisCategory.WOMEN_CLOTHING ->
                "Clothing: only describe clothing details actually visible in the current body crop."
            AnalysisCategory.MEN_CLOTHING ->
                "Clothing: keep realistic silhouette and texture aligned with crop and lighting."
            AnalysisCategory.LOCATION ->
                "Location: generate only elements observable within camera frame and avoid imaginary off-screen details."
            AnalysisCategory.WOMEN_POSE ->
                "Pose: keep subject count, focus, and camera angle; vary safe hand and upper-body posture."
            AnalysisCategory.MEN_POSE ->
                "Pose: align posture with body type and surrounding physical structures."
            AnalysisCategory.WOMEN_EXPRESSION ->
                "Expression: only describe facial expression when the face is visible enough."
            AnalysisCategory.WOMEN_HAIRSTYLE ->
                "Hairstyle: prioritize length, tied state, bangs, wave/straight texture, volume, and silhouette; avoid front facial details in rear or distant views."
            AnalysisCategory.MEN_APPEARANCE,
            AnalysisCategory.WAKA -> ""
        }
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
                        "startIndex" to integerSchema("0-based start index."),
                        "endIndex" to integerSchema("0-based exclusive end index."),
                        "confidence" to numberSchema("0.0 to 1.0 confidence."),
                        "reason" to stringSchema("Korean reason.")
                    ),
                    "required" to arr("exactText", "startIndex", "endIndex", "confidence", "reason")
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
                "warnings" to stringArraySchema()
            ),
            "required" to arr(
                "targetSegment",
                "visualContext",
                "spatialLayout",
                "categoryConstraints",
                "variationGoal",
                "warnings"
            )
        )
    }

    private fun txtResponseSchema(): JsonObject {
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
