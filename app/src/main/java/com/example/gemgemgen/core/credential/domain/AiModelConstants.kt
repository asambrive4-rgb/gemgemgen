// 역할: 앱 전반에서 사용되는 AI 모델 식별자 상수, 프롬프트 페이로드 및 분석 예외를 정의합니다.
package com.example.gemgemgen.core.credential.domain

import kotlinx.serialization.json.JsonObject

const val MODEL_GEMINI_3_8_FLASH = "gemini-3.8-flash"
const val MODEL_GEMINI_3_7_FLASH = "gemini-3.7-flash"
const val DEFAULT_ANALYSIS_MODEL = MODEL_GEMINI_3_7_FLASH
const val MODEL_GEMINI_3_6_FLASH = "gemini-3.6-flash"
const val MODEL_GEMINI_3_5_FLASH_LITE = "gemini-3.5-flash-lite"
const val MODEL_GROK_4_5 = "grok-4.5"

data class AnalysisPromptPayload(
    val systemInstruction: String,
    val userPrompt: String,
    val responseSchema: JsonObject
)

class AnalysisException(message: String) : RuntimeException(message)
