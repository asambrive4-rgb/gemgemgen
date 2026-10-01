// 역할: 와일드카드 단어들을 유사 의미별로 그룹화하도록 요청하는 AI 프롬프트를 조립합니다.
package com.example.gemgemgen.wildcard.domain

import com.example.gemgemgen.analysis.domain.AnalysisPromptPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

object WildcardClassifyPromptBuilder {
    private val CLASSIFY_RESPONSE_SCHEMA: JsonObject by lazy {
        Json.parseToJsonElement(
            """
            {
              "type": "object",
              "properties": {
                "groups": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string" },
                      "items": {
                        "type": "array",
                        "items": { "type": "string" }
                      }
                    },
                    "required": ["name", "items"]
                  }
                }
              },
              "required": ["groups"]
            }
            """.trimIndent()
        ).jsonObject
    }

    fun build(
        criteria: String,
        lines: List<String>
    ): AnalysisPromptPayload {
        val numbered = lines.mapIndexed { index, line ->
            "${index + 1}. $line"
        }.joinToString(separator = "\n")

        val systemInstruction = """
You classify existing wildcard candidate lines into groups for a Korean image-prompt wildcard library.

Rules:
1. Use ONLY the user-provided classification criteria to decide groups and membership.
2. Every output item MUST be an exact copy of one input line. Do not rewrite, translate, merge, or invent lines.
3. Prefer assigning each input line to exactly one group.
4. You may omit a line from all named groups if it does not fit; unassigned lines will be treated as dropped lines and not saved.
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

        return AnalysisPromptPayload(
            systemInstruction = systemInstruction,
            userPrompt = userPrompt,
            responseSchema = CLASSIFY_RESPONSE_SCHEMA
        )
    }
}
