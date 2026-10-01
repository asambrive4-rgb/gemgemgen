// 역할: Grok AI 모델과 통신하여 프롬프트 분석 및 개선 요청을 수행합니다.
package com.example.gemgemgen.analysis.android

import com.example.gemgemgen.analysis.domain.AnalysisPromptPayload
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.usecase.AnalysisAiGateway
import com.example.gemgemgen.analysis.usecase.AnalysisException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Grok chat completions 경로. OAuth access token을 apiKey 자리에 받는다.
 * 구조화 출력은 기존 파서가 기대하는 JSON 텍스트를 시스템 지시로 강제한다.
 */
class AndroidGrokAnalysisGateway : AnalysisAiGateway {

    override suspend fun analyze(
        apiKey: String,
        modelId: String,
        payload: AnalysisPromptPayload,
        provider: AnalysisProvider
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

            val (responseCode, responseText) = executeHttpRequest(connection, body.toString())

            if (responseCode == 401 || responseCode == 403) {
                val raw = extractJsonErrorMessage(responseText)
                throw AnalysisException(
                    "Grok 인증/권한 오류입니다 ($responseCode). 로그인 상태를 확인하거나 Gemini로 전환해 주세요.${if (raw.isNotBlank()) " ($raw)" else ""}"
                )
            }
            if (responseCode == 429) {
                throw AnalysisException(
                    "Grok 요청 한도(Rate Limit)에 도달했습니다 (429). 잠시 후 다시 시도해 주세요."
                )
            }
            if (responseCode == 503) {
                throw AnalysisException(
                    "Grok 서버가 현재 과부하 상태이거나 점검 중입니다 (503). 잠시 후 다시 시도해 주세요."
                )
            }
            if (responseCode !in 200..299) {
                val raw = extractJsonErrorMessage(responseText)
                throw AnalysisException(
                    "Grok 요청에 실패했습니다 (응답 코드 $responseCode)${if (raw.isNotBlank()) ": $raw" else "."}"
                )
            }

            stripCodeFence(extractMessageText(responseText))
        } catch (error: AnalysisException) {
            throw error
        } catch (error: Exception) {
            throw AnalysisException(
                formatAnalysisNetworkError(
                    error = error,
                    serviceName = "Grok",
                    serverLabel = "xAI 서버",
                    timeoutMillis = READ_TIMEOUT_MILLIS
                )
            )
        }
    }

    private fun extractMessageText(responseText: String): String {
        val root = analysisJson.parseToJsonElement(responseText).jsonObject
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

    private companion object {
        const val API_BASE = "https://api.x.ai/v1"
        const val CONNECT_TIMEOUT_MILLIS = 20_000
        // 긴 분석/TXT 생성 대기. 타임아웃 시에도 앱은 종료되지 않고 오류 메시지로 복구.
        const val READ_TIMEOUT_MILLIS = 180_000
    }
}
