package com.example.gemgemgen.wildcard.domain

import com.example.gemgemgen.analysis.domain.AnalysisTxtPromptPayload
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object WildcardClassifyPromptBuilder {
    fun build(
        criteria: String,
        lines: List<String>
    ): AnalysisTxtPromptPayload {
        val numbered = lines.mapIndexed { index, line ->
            "${index + 1}. $line"
        }.joinToString(separator = "\n")

        val systemInstruction = """
You classify existing wildcard candidate lines into groups for a Korean image-prompt wildcard library.

Rules:
1. Use ONLY the user-provided classification criteria to decide groups and membership.
2. Every output item MUST be an exact copy of one input line. Do not rewrite, translate, merge, or invent lines.
3. Prefer assigning each input line to exactly one group.
4. You may omit a line from all named groups if it does not fit; the app will place leftovers into "미분류".
5. Group names must be short Korean labels suitable as file names (no path characters).
6. Return strict JSON only matching the schema.
        """.trimIndent()

        val userPrompt = """
Classification criteria (follow this):
$criteria

Input lines (one candidate per line; keep text exact when assigning):
$numbered

Classify all lines according to the criteria. Return JSON with groups[].name and groups[].items[].
        """.trimIndent()

        return AnalysisTxtPromptPayload(
            systemInstruction = systemInstruction,
            userPrompt = userPrompt,
            responseSchema = classifyResponseSchema()
        )
    }

    private fun classifyResponseSchema(): JsonObject {
        return buildJsonObject {
            put("type", JsonPrimitive("object"))
            put(
                "properties",
                buildJsonObject {
                    put(
                        "groups",
                        buildJsonObject {
                            put("type", JsonPrimitive("array"))
                            put(
                                "items",
                                buildJsonObject {
                                    put("type", JsonPrimitive("object"))
                                    put(
                                        "properties",
                                        buildJsonObject {
                                            put("name", buildJsonObject {
                                                put("type", JsonPrimitive("string"))
                                            })
                                            put(
                                                "items",
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("array"))
                                                    put(
                                                        "items",
                                                        buildJsonObject {
                                                            put("type", JsonPrimitive("string"))
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    )
                                    put(
                                        "required",
                                        JsonArray(
                                            listOf(
                                                JsonPrimitive("name"),
                                                JsonPrimitive("items")
                                            )
                                        )
                                    )
                                }
                            )
                        }
                    )
                }
            )
            put(
                "required",
                JsonArray(listOf(JsonPrimitive("groups")))
            )
        }
    }
}
