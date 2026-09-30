// 역할: Gemini AI 모델과 통신하여 프롬프트 분석 및 추천 요청을 수행합니다.
package com.example.gemgemgen.analysis.android

import com.example.gemgemgen.analysis.domain.AnalysisPromptPayload
import com.example.gemgemgen.analysis.usecase.AnalysisAiGateway
import com.example.gemgemgen.analysis.usecase.AnalysisException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AndroidGeminiAnalysisGateway(
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val initialRetryDelayMillis: Long = DEFAULT_INITIAL_RETRY_DELAY_MILLIS
) : AnalysisAiGateway {

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

    private suspend fun generateContent(
        apiKey: String,
        modelId: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject
    ): String {
        var attempt = 0
        var currentDelay = initialRetryDelayMillis

        while (true) {
            attempt++
            try {
                return executeRequest(
                    apiKey = apiKey,
                    modelId = modelId,
                    systemInstruction = systemInstruction,
                    userPrompt = userPrompt,
                    responseSchema = responseSchema
                )
            } catch (error: RetryableServerException) {
                if (attempt > maxRetries) {
                    throw AnalysisException(formatHttpError(error.responseCode, error.responseText, modelId))
                }
                delay(currentDelay)
                currentDelay *= 2
            } catch (error: AnalysisException) {
                throw error
            } catch (error: Exception) {
                throw AnalysisException(formatNetworkError(error))
            }
        }
    }

    private fun executeRequest(
        apiKey: String,
        modelId: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject
    ): String {
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
        val (responseCode, responseText) = executeHttpRequest(connection, body.toString())

        if (responseCode !in 200..299) {
            val isRetryable = responseCode == 503 ||
                responseCode == 502 ||
                responseCode == 504 ||
                (responseCode in 500..599 && responseText.contains("overloaded", ignoreCase = true))
            if (isRetryable) {
                throw RetryableServerException(responseCode, responseText)
            }
            throw AnalysisException(formatHttpError(responseCode, responseText, modelId))
        }

        return extractCandidateText(responseText)
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

    internal fun extractCandidateText(responseText: String): String {
        val root = analysisJson.parseToJsonElement(responseText).jsonObject

        // 1. 프롬프트 단계 차단(promptFeedback) 확인
        val promptFeedback = root["promptFeedback"] as? JsonObject
        val blockReason = (promptFeedback?.get("blockReason") as? JsonPrimitive)?.contentOrNull
        if (!blockReason.isNullOrBlank()) {
            val detail = when (blockReason) {
                "SAFETY" -> "안전 정책(Safety)에 의해 프롬프트가 차단되었습니다. 민감하거나 부적절한 표현을 완화해 주세요."
                "BLOCKLIST" -> "금지어 목록(Blocklist) 정책에 의해 프롬프트가 차단되었습니다."
                "PROHIBITED_CONTENT" -> "금지된 콘텐츠 정책에 의해 프롬프트가 차단되었습니다."
                else -> "Gemini 정책에 의해 프롬프트가 차단되었습니다 ($blockReason)."
            }
            throw AnalysisException("프롬프트 차단: $detail")
        }

        val candidates = root["candidates"]?.jsonArray.orEmpty()
        val firstCandidate = candidates.firstOrNull() as? JsonObject
            ?: throw AnalysisException("Gemini 응답에 결과 후보(Candidate)가 없습니다. 서버에서 답변을 생성하지 못했습니다.")

        val finishReason = (firstCandidate["finishReason"] as? JsonPrimitive)?.contentOrNull
        val contentObj = firstCandidate["content"] as? JsonObject
        val parts = (contentObj?.get("parts") as? JsonArray).orEmpty()
        val nonThoughtParts = parts.filterNot(::isThoughtPart)
        val targetParts = nonThoughtParts.ifEmpty { parts }
        val resultText = targetParts.joinToString(separator = "") { part ->
            (part as? JsonObject)?.get("text")?.jsonPrimitive?.content.orEmpty()
        }

        if (resultText.isBlank()) {
            val detail = when (finishReason) {
                "SAFETY" -> "답변 내용이 Gemini 안전 정책(Safety) 필터에 걸려 차단되었습니다. 프롬프트 내용을 완화해 주세요."
                "RECITATION" -> "저작권/인용 보호(Recitation) 정책에 의해 생성이 차단되었습니다."
                "MAX_TOKENS" -> "최대 출력 토큰 수를 초과하여 응답이 생성되지 못했습니다."
                "BLOCKLIST" -> "금지어 정책에 의해 생성이 차단되었습니다."
                "OTHER" -> "알 수 없는 이유로 생성이 중단되었습니다 (finishReason: OTHER)."
                else -> "Gemini 응답 텍스트가 비어 있습니다. 잠시 후 다시 시도해 주세요."
            }
            throw AnalysisException(detail)
        }

        return resultText
    }

    internal fun formatHttpError(responseCode: Int, responseText: String, modelId: String): String {
        val rawMessage = extractJsonErrorMessage(responseText)
        val lower = rawMessage.lowercase()
        return when {
            responseCode == 400 && (lower.contains("api key") || lower.contains("api_key")) ->
                "Gemini API 키 오류: 등록된 API 키가 유효하지 않습니다. 올바른 API 키를 등록했는지 확인해 주세요."
            responseCode == 400 ->
                "Gemini 요청 파라미터 오류 (400)${if (rawMessage.isNotBlank()) ": $rawMessage" else "."}"
            responseCode == 401 ->
                "Gemini 인증 실패 (401): API 키가 만료되었거나 올바르지 않습니다."
            responseCode == 403 ->
                "Gemini 접근 권한 오류 (403): API 키 권한이 없거나 지원되지 않는 지역입니다. Google AI Studio 설정을 확인해 주세요."
            responseCode == 404 ->
                "Gemini 모델을 찾을 수 없습니다 ($modelId): 지원되지 않거나 이름이 변경된 모델입니다. 다른 모델을 선택해 주세요."
            responseCode == 429 || lower.contains("quota") || lower.contains("resource_exhausted") ->
                "Gemini 요청 한도 초과 (429): 분당 요청 수(RPM) 또는 일일 사용량이 소진되었습니다. 잠시 후 다시 시도하거나 Flash-Lite 모델을 사용해 보세요."
            responseCode == 503 || lower.contains("overloaded") ->
                "Gemini 서버 과부하/점검 중 (503): Google 서버가 일시적으로 지연되고 있습니다. 잠시 후 다시 시도하거나 다른 모델을 선택해 주세요."
            responseCode in 500..599 ->
                "Gemini 서버 내부 오류 ($responseCode): Google 서비스 장애일 수 있으니 잠시 후 다시 시도해 주세요."
            else ->
                "Gemini 요청에 실패했습니다 (응답 코드 $responseCode)${if (rawMessage.isNotBlank()) ": $rawMessage" else "."}"
        }
    }

    internal fun formatNetworkError(error: Exception): String {
        return formatAnalysisNetworkError(
            error = error,
            serviceName = "Gemini",
            serverLabel = "Google 서버",
            timeoutMillis = READ_TIMEOUT_MILLIS,
            timeoutGuidance = "복잡한 추론 모델 대신 빠른 Flash-Lite 모델을 사용하거나 잠시 후 다시 시도해 주세요."
        )
    }

    private fun isThoughtPart(part: JsonElement): Boolean {
        val partObj = part as? JsonObject ?: return false
        val thoughtElement = partObj["thought"] ?: return false
        return runCatching {
            thoughtElement.jsonPrimitive.content.toBooleanStrictOrNull() == true
        }.getOrDefault(false)
    }

    private fun List<JsonElement>?.orEmpty(): List<JsonElement> {
        return this ?: emptyList()
    }

    private class RetryableServerException(
        val responseCode: Int,
        val responseText: String
    ) : RuntimeException()

    internal companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 120_000
        const val DEFAULT_MAX_RETRIES = 2
        const val DEFAULT_INITIAL_RETRY_DELAY_MILLIS = 1_500L
    }
}
