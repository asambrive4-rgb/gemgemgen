package com.example.gemgemgen.analysis.android

import com.example.gemgemgen.analysis.domain.AnalysisPromptPayload
import com.example.gemgemgen.analysis.domain.AnalysisTxtPromptPayload
import com.example.gemgemgen.analysis.usecase.AnalysisAiGateway
import com.example.gemgemgen.analysis.usecase.AnalysisException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Grok chat completions 경로. OAuth access token을 apiKey 자리에 받는다.
 * 구조화 출력은 기존 파서가 기대하는 JSON 텍스트를 시스템 지시로 강제한다.
 */
class AndroidGrokAnalysisGateway : AnalysisAiGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyze(
        apiKey: String,
        modelId: String,
        payload: AnalysisPromptPayload
    ): String {
        return complete(
            accessToken = apiKey,
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
        return complete(
            accessToken = apiKey,
            modelId = modelId,
            systemInstruction = payload.systemInstruction,
            userPrompt = payload.userPrompt,
            responseSchema = payload.responseSchema
        )
    }

    private fun complete(
        accessToken: String,
        modelId: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject
    ): String {
        return try {
            val url = URL("$API_BASE/chat/completions")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Accept", "application/json")
            }

            val systemWithJson = buildString {
                append(systemInstruction.trim())
                append("\n\n")
                append("Respond with ONLY valid JSON. No markdown fences, no commentary.")
                append(" The JSON must match this schema shape:\n")
                append(responseSchema.toString())
            }
            val body = buildJsonObject {
                put("model", JsonPrimitive(modelId))
                put(
                    "messages",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", JsonPrimitive("system"))
                                put("content", JsonPrimitive(systemWithJson))
                            }
                        )
                        add(
                            buildJsonObject {
                                put("role", JsonPrimitive("user"))
                                put("content", JsonPrimitive(userPrompt))
                            }
                        )
                    }
                )
                put("temperature", JsonPrimitive(0.2))
            }

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

                if (responseCode == 401 || responseCode == 403) {
                    throw AnalysisException(
                        errorMessage(responseText).ifBlank {
                            "Grok 인증/권한 오류입니다. ($responseCode) 로그인 상태를 확인하거나 Gemini로 전환해 주세요."
                        }
                    )
                }
                if (responseCode == 429) {
                    throw AnalysisException(
                        errorMessage(responseText).ifBlank {
                            "Grok 요청이 한도에 도달했습니다. 잠시 후 다시 시도해 주세요."
                        }
                    )
                }
                if (responseCode !in 200..299) {
                    throw AnalysisException(
                        errorMessage(responseText).ifBlank {
                            "Grok 요청에 실패했습니다. 응답 코드: $responseCode"
                        }
                    )
                }

                stripCodeFence(extractMessageText(responseText))
            } finally {
                connection.disconnect()
            }
        } catch (error: AnalysisException) {
            throw error
        } catch (error: Exception) {
            throw AnalysisException(
                "Grok 네트워크 요청에 실패했습니다: ${error.message ?: error.javaClass.simpleName}"
            )
        }
    }

    private fun extractMessageText(responseText: String): String {
        val root = json.parseToJsonElement(responseText).jsonObject
        val choices = root["choices"]?.jsonArray.orEmpty()
        val first = choices.firstOrNull()?.jsonObject
            ?: throw AnalysisException("Grok 응답에 후보가 없습니다.")
        val contentElement = first["message"]?.jsonObject?.get("content")
        val content = when {
            contentElement == null -> ""
            contentElement is JsonPrimitive -> contentElement.content
            else -> runCatching {
                contentElement.jsonArray.joinToString("") { part ->
                    part.jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
                }
            }.getOrDefault("")
        }
        return content.ifBlank {
            throw AnalysisException("Grok 응답 텍스트가 비어 있습니다.")
        }
    }

    private fun stripCodeFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val withoutOpen = trimmed.removePrefix("```").removePrefix("json").removePrefix("JSON")
            .trimStart()
        val end = withoutOpen.lastIndexOf("```")
        return if (end >= 0) withoutOpen.substring(0, end).trim() else withoutOpen.trim()
    }

    private fun errorMessage(responseText: String): String {
        return runCatching {
            val root = json.parseToJsonElement(responseText).jsonObject
            val error = root["error"] ?: return@runCatching ""
            error.jsonPrimitive.contentOrNull
                ?: error.jsonObject["message"]?.jsonPrimitive?.content
                ?: error.jsonObject["error"]?.jsonPrimitive?.content
                ?: ""
        }.getOrDefault("")
    }

    private companion object {
        const val API_BASE = "https://api.x.ai/v1"
        const val CONNECT_TIMEOUT_MILLIS = 20_000
        // 긴 분석/TXT 생성 대기. 타임아웃 시에도 앱은 종료되지 않고 오류 메시지로 복구.
        const val READ_TIMEOUT_MILLIS = 180_000
    }
}
