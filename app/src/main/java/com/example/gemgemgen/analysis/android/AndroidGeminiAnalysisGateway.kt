package com.example.gemgemgen.analysis.android

import com.example.gemgemgen.analysis.domain.AnalysisPromptPayload
import com.example.gemgemgen.analysis.domain.AnalysisTxtPromptPayload
import com.example.gemgemgen.analysis.usecase.AnalysisAiGateway
import com.example.gemgemgen.analysis.usecase.AnalysisException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AndroidGeminiAnalysisGateway : AnalysisAiGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyze(
        apiKey: String,
        modelId: String,
        payload: AnalysisPromptPayload
    ): String {
        return generateContent(
            apiKey = apiKey,
            modelId = modelId,
            systemInstruction = payload.systemInstruction,
            userPrompt = payload.userPrompt,
            responseSchema = payload.responseSchema
        )
    }

    override suspend fun generateTxt(
        apiKey: String,
        modelId: String,
        payload: AnalysisTxtPromptPayload
    ): String {
        return generateContent(
            apiKey = apiKey,
            modelId = modelId,
            systemInstruction = payload.systemInstruction,
            userPrompt = payload.userPrompt,
            responseSchema = payload.responseSchema
        )
    }

    private fun generateContent(
        apiKey: String,
        modelId: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject
    ): String {
        return try {
            val encodedKey = URLEncoder.encode(apiKey, Charsets.UTF_8.name())
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$encodedKey"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            val body = buildRequestBody(
                systemInstruction = systemInstruction,
                userPrompt = userPrompt,
                responseSchema = responseSchema
            )
            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body.toString())
                }

                val responseCode = connection.responseCode
                val responseText = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        .orEmpty()
                }

                if (responseCode !in 200..299) {
                    throw AnalysisException(errorMessage(responseText).ifBlank {
                        "Gemini 요청에 실패했습니다. 응답 코드: $responseCode"
                    })
                }

                extractCandidateText(responseText)
            } finally {
                connection.disconnect()
            }
        } catch (error: AnalysisException) {
            throw error
        } catch (error: Exception) {
            throw AnalysisException(
                "Gemini 네트워크 요청에 실패했습니다: ${error.message ?: error.javaClass.simpleName}"
            )
        }
    }

    private fun buildRequestBody(
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject
    ): JsonObject {
        return buildJsonObject {
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put(
                                "parts",
                                buildJsonArray {
                                    add(buildJsonObject { put("text", JsonPrimitive(userPrompt)) })
                                }
                            )
                        }
                    )
                }
            )
            put(
                "systemInstruction",
                buildJsonObject {
                    put(
                        "parts",
                        buildJsonArray {
                            add(buildJsonObject { put("text", JsonPrimitive(systemInstruction)) })
                        }
                    )
                }
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("responseMimeType", JsonPrimitive("application/json"))
                    put("responseSchema", responseSchema)
                }
            )
        }
    }

    private fun extractCandidateText(responseText: String): String {
        val root = json.parseToJsonElement(responseText).jsonObject
        val candidates = root["candidates"]?.jsonArray.orEmpty()
        val firstCandidate = candidates.firstOrNull()?.jsonObject
            ?: throw AnalysisException("Gemini 응답에 후보가 없습니다.")
        val parts = firstCandidate["content"]
            ?.jsonObject
            ?.get("parts")
            ?.jsonArray
            .orEmpty()
        return parts.joinToString(separator = "") { part ->
            part.jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
        }.ifBlank {
            throw AnalysisException("Gemini 응답 텍스트가 비어 있습니다.")
        }
    }

    private fun errorMessage(responseText: String): String {
        return runCatching {
            json.parseToJsonElement(responseText)
                .jsonObject["error"]
                ?.jsonObject
                ?.get("message")
                ?.jsonPrimitive
                ?.content
                .orEmpty()
        }.getOrDefault("")
    }

    private fun List<JsonElement>?.orEmpty(): List<JsonElement> {
        return this ?: emptyList()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 60_000
    }
}
