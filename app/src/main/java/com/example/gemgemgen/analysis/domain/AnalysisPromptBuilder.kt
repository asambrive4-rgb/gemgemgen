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
You are a high-fidelity image prompt engineering expert specializing in prompt analysis and precision reconstruction.
Analyze a full image prompt for the target variation category: "${category.label}".

Category Rules:
- Required:
${rule.required}
- Avoid:
${rule.avoid}
- Variables: ${rule.variables}
${if (rule.output.isBlank()) "" else "- Output: ${rule.output}"}

Detailed Domain & Category Guidance:
$specialRule

Systematic Analysis Principles:
1. Four Request Categories Diagnosis:
   - Failure Correction: The original prompt had intent/negative conditions that failed due to ambiguity, weak wording, or conflicting syntax. Identify and fix root cause.
   - Missing Conditions: Essential conditions were omitted, causing model hallucination. Specify precise position, direction, distance, scale, or relationship.
   - Intentional Direction Shift: Legitimate request to change theme, setting, lighting, composition, style, or action. Adjust relevant conditions cleanly without claiming the original was "erroneous".
   - Essential Cascading Adjustments: Track mandatory knock-on effects (newly visible or occluded anatomy/clothing/ground, conflicting previous adjectives, redefined margins/crops).
2. Concrete Visual Translation of Abstract Desires:
   - Never keep abstract atmospheric desires in raw form. Translate them directly into concrete, physically observable visual parameters:
     * "More overwhelming" -> Lower camera height, upward low angle, magnified subject frame share, exaggerated lower-body perspective, vertical lines converging upward, reduced top margin or towering background structures.
     * "Reduce flatness / enhance depth" -> Distinct separation of foreground/midground/background, significant scale disparity between near and far objects, explicit overlap hierarchy, strong receding lines on floor/walls, wide-angle perspective or oblique angle, prominent foreground framing.
     * "Closer feel" -> Increased subject frame occupancy, reduced head and lateral margins, deliberate framing crop of peripheral anatomy, reduced background visibility, compressed perceived camera-subject distance.
3. Human Figure Priority (Appearance over Declared Emotion):
   - For all human subjects, express intent through observable physical cues: facial muscle/gaze orientation, spine/shoulder tilt, weight-bearing leg, hand placement/grip, body contact points, and clothing fit/tension.
   - Abstract emotional or narrative declarations must not dominate the prompt. If physical cues sufficiently convey the request, omit raw emotion words entirely; otherwise, use at most one subdued impression note.
4. Essential Cascading Trace (Do Not Stop at Local Patch):
   - When framing expands: specify newly exposed body areas, lower garment length, socks/shoes or bare feet, contact with ground/furniture, and expanded background borders.
   - When environment/lighting changes: eliminate indoor/outdoor props of the previous scene, align light source angles, color temperature, and corresponding shadow behavior.
   - Strictly avoid arbitrary redesign of independent elements (hair, face, accessories, theme) that have no physical link to the requested change.
5. Exact Substring Extraction:
   - Identify the exact substring in the source prompt to be replaced, with exact 0-based startIndex and endIndex.

Goals:
1. Find the exact substring in the original prompt that represents the target category.
2. Return the exact 0-based startIndex and endIndex of that substring.
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

        val systemInstruction = """
You generate Korean wildcard candidate fragments for image prompts.
Generate exactly $count Korean fragments that can replace "${targetSegment.text}" for "${category.label}".

Variation goal (from analysis; follow this as the primary creative objective):
$variationGoal

Critical rules:
1. Output only Korean wildcard fragments in the "text" field.
2. Do not output full prompts, English sentences, numbering, bullets, prefixes, or suffix explanations.
3. Keep the original viewpoint, distance, visual scope, and spatial layout stable unless the target category or variation goal explicitly requires their alteration.
4. High-precision visual rendering: Do not merely output abstract genre/technique names like "로우 앵글", "광각", "화보 스타일", "빈티지", "더 가까운 느낌", "압도적인 분위기". Describe what is physically and visibly happening in the frame (e.g., lower camera looking up with vertical lines converging, enlarged foreground limbs, dense film grain, chiaroscuro lighting contrast, precise frame occupancy).
5. Human appearance priority: For human subjects, prioritize concrete physical posture, gaze direction, hand position, and clothing tension/drape rather than declared abstract emotional adjectives. Do not dictate broad narrative claims like "유혹한다", "지배한다", "강하게 느껴진다".
6. Essential cascading coverage: Include newly necessary elements (e.g., footwear or ground surface if lower body is newly framed; clear contact with surfaces; adjusted shadows for new light directions) so the replaced fragment leaves no contradictory or missing visual gaps.
7. Preserve non-target elements: Do not arbitrarily modify independent clothing designs, hair styles, identities, or scene props that have no physical link to the requested modification.
8. If the viewpoint is rear/side-back, do not describe frontal face details, lip color, eye makeup, or front-facing fringe unless explicitly visible.
9. If lower body or floor is cropped or unclear, do not describe shoes, socks, floor tiles, ground, or pavement details.
10. For location/background, include layout-aware structure, not short lazy place names.
11. For human categories, avoid injecting unrelated location details.
12. Match the level of detail and descriptive length of the user's direction hints. If the hints are highly detailed and long, generate outputs that are correspondingly rich and descriptive, rather than summarizing them into short 1-2 sentences.
13. Respect the style and format of the user's hints naturally (e.g., matching the overall tone or structure), but do not restrict the phrasing too strictly if it harms expression quality.
14. Prefer the Variation goal above when choosing length, lineage, tone, and emphasis; still obey camera/crop and allowed/avoid constraints.
15. Return strict JSON array only.

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
            AnalysisCategory.FREE_EDIT -> """
Analysis & Precision Modification Guidance:
1. Request Diagnosis & Scope:
   - Failure Correction: Identify ambiguous, weak, or conflicting conditions in the source prompt that caused generation failure; clarify physical boundaries.
   - Missing Conditions: Provide specific location, direction, distance, scale, or relationship that was previously omitted.
   - Intentional Direction Shift: When user requests theme, setting, action, or style change, adjust relevant conditions cleanly without claiming the original was erroneous.
2. Abstract-to-Visual Translation Mapping:
   - "More overwhelming": Lower camera height, upward tilted low angle, expanded subject frame share, magnified lower body in foreground, vertical lines converging upward, reduced top margin or towering background structures.
   - "Reduce flatness / enhance depth": 3-tier depth separation (foreground, midground, background), sharp scale disparity between near and far objects, explicit overlap hierarchy, strong receding lines on floor/walls, oblique angle, large foreground framing elements.
   - "Closer feel": Increased subject frame occupancy, reduced head and lateral margins, deliberate framing crop of peripheral anatomy, reduced background visibility, compressed perceived camera distance.
3. Essential Cascading Tracking (Critical):
   - When framing expands: specify newly exposed body areas, lower garment length, socks/shoes or bare feet, contact with ground/furniture, and expanded background borders.
   - When environment/lighting changes: purge indoor props from outdoor scenes, harmonize key light angle, color temperature, and cast shadows.
   - Remove or resolve any existing negative or contradictory prompt sentences that clash with the new direction.
   - Strictly avoid arbitrary modification of independent elements (hair, facial identity, independent props, unrelated narrative) not physically tied to the requested change.
4. Human Figure Priority:
   - Always lead with concrete observable appearance (facial features, eyebrow/gaze direction, spine/shoulder posture, weight distribution on legs, precise hand/finger placement, clothing fit/tension/wrinkles).
   - Omit abstract emotional declarations if appearance conveys the intent; if needed for nuance, append at most one gentle, subdued impression phrase.
            """.trimIndent()

            AnalysisCategory.COMPOSITION -> """
Composition Guidance:
1. Frame & Crop Specifications:
   - Check vertical/horizontal/square framing, exact crop level (full body, knee-up, waist-up, bust-up, tight face).
   - Explicitly define edge cuts, subject frame occupancy, and left/right/top/bottom negative space.
2. Camera Height, Tilt & Optical Perspective:
   - High Angle: Camera positioned above subject eye level and tilted downward; head/upper torso relatively larger than lower body; lower limbs foreshortened and compressed downward; broad floor/ground exposure; top surfaces of furniture and objects visible. Never just write "high angle".
   - Low Angle: Camera positioned below subject eye level and tilted upward; legs/feet prominently enlarged; torso/head diminishing upward; exaggerated vertical height; ceiling or towering structures exposed; vertical lines of walls/pillars converging upward. Never just write "low angle" or "epic feel".
   - Wide-angle / Ultra Wide: Camera close to subject; dramatic disparity between large foreground and rapidly receding background; prominent projection of nearest limbs; exaggerated spatial depth; subtle barrel distortion or outward flare of peripheral lines.
   - Fisheye: Center bulging forward; peripheral straight lines bowed like an arch; central elements magnified; edge elements curved along hemispherical distortion.
   - Telephoto / Compression: Minimal scale disparity between foreground and background; distant elements visually pressed against subject; depth flattened; minimal distortion.
3. Cascading Framing Requirements:
   - When widening: define newly visible footwear, lower clothing, ground texture, and contact points.
   - When tightening: ensure cropped-out elements are not contradictory described.
            """.trimIndent()

            AnalysisCategory.CAMERA_TEXTURE -> """
Camera & Optical Texture Guidance:
1. Illumination & Contrast:
   - Explicit key light direction, luminance contrast ratio between highlights and deep shadows, sharpness/softness of shadow penumbra, highlight falloff and rolloff.
2. Optical Depth of Field (DoF):
   - Precise aperture effect: shallow DoF with smooth creamy background bokeh versus sharp pan-focus rendering across all depth planes; subject-background separation.
3. Sensor & Film Emulsion Texture:
   - Subtle tactile film grain structure, analog grain density, sensor noise characteristics, optical sharpness versus gentle diffusion/soft glow.
4. Optical Aberrations & Color Cast:
   - Peripheral vignetting (lens falloff), chromatic aberration, anamorphic lens flare, color temperature (warm golden hour bias, cool blue dusk, muted vintage tones).
5. Style Realization without Generic Buzzwords:
   - Fashion Editorial: Clean, controlled lighting sculpting fabric silhouettes, refined texture, deliberate asymmetric negative space, restrained tonal palette.
   - Cinematic Scene: Distinct directional key light, dramatic chiaroscuro contrast, volumetric atmosphere, layered depth grading.
   - Vintage / Analog: Gentle contrast, organic grain, soft highlight bloom, subtle color drift. Avoid brand-name camera fabrication.
            """.trimIndent()
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
