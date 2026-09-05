package com.example.gemgemgen

import com.example.gemgemgen.analysis.android.AndroidGeminiAnalysisGateway
import com.example.gemgemgen.analysis.usecase.AnalysisException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidGeminiAnalysisGatewayTest {
    private val gateway = AndroidGeminiAnalysisGateway()

    @Test
    fun `일반 응답에서 텍스트를 정상 추출한다`() {
        val json = """
            {
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                { "text": "{\"key\": \"value\"}" }
                            ]
                        }
                    }
                ]
            }
        """.trimIndent()

        val text = gateway.extractCandidateText(json)
        assertEquals("{\"key\": \"value\"}", text)
    }

    @Test
    fun `thought 파트가 포함된 경우 thought 파트를 제외하고 실제 결과만 추출한다`() {
        val json = """
            {
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {
                                    "thought": true,
                                    "text": "Here is my reasoning about the image prompt..."
                                },
                                {
                                    "text": "{\"targetSegment\": {\"exactText\": \"beach\"}}"
                                }
                            ]
                        }
                    }
                ]
            }
        """.trimIndent()

        val text = gateway.extractCandidateText(json)
        assertEquals("{\"targetSegment\": {\"exactText\": \"beach\"}}", text)
    }

    @Test
    fun `후보가 없는 경우 예외를 발생시킨다`() {
        val json = """{ "candidates": [] }"""
        val ex = assertThrows(AnalysisException::class.java) {
            gateway.extractCandidateText(json)
        }
        assertEquals("Gemini 응답에 결과 후보(Candidate)가 없습니다. 서버에서 답변을 생성하지 못했습니다.", ex.message)
    }

    @Test
    fun `프롬프트 단계에서 SAFETY로 차단된 경우 명확한 안내를 제공한다`() {
        val json = """
            {
                "promptFeedback": {
                    "blockReason": "SAFETY"
                }
            }
        """.trimIndent()

        val ex = assertThrows(AnalysisException::class.java) {
            gateway.extractCandidateText(json)
        }
        assertEquals("프롬프트 차단: 안전 정책(Safety)에 의해 프롬프트가 차단되었습니다. 민감하거나 부적절한 표현을 완화해 주세요.", ex.message)
    }

    @Test
    fun `답변 단계에서 SAFETY로 차단되어 비어 있는 경우 명확한 안내를 제공한다`() {
        val json = """
            {
                "candidates": [
                    {
                        "finishReason": "SAFETY",
                        "content": {
                            "parts": []
                        }
                    }
                ]
            }
        """.trimIndent()

        val ex = assertThrows(AnalysisException::class.java) {
            gateway.extractCandidateText(json)
        }
        assertEquals("답변 내용이 Gemini 안전 정책(Safety) 필터에 걸려 차단되었습니다. 프롬프트 내용을 완화해 주세요.", ex.message)
    }

    @Test
    fun `HTTP 400 API 키 오류 시 친절한 한글 안내를 제공한다`() {
        val responseText = """{"error": {"code": 400, "message": "API key not valid. Please pass a valid API key."}}"""
        val msg = gateway.formatHttpError(400, responseText, "gemini-3.7-flash")
        assertEquals("Gemini API 키 오류: 등록된 API 키가 유효하지 않습니다. 올바른 API 키를 등록했는지 확인해 주세요.", msg)
    }

    @Test
    fun `HTTP 404 모델 없음 오류 시 안내를 제공한다`() {
        val msg = gateway.formatHttpError(404, "{}", "gemini-unknown")
        assertEquals("Gemini 모델을 찾을 수 없습니다 (gemini-unknown): 지원되지 않거나 이름이 변경된 모델입니다. 다른 모델을 선택해 주세요.", msg)
    }

    @Test
    fun `HTTP 429 Quota 초과 시 대기 및 Flash-Lite 권장 안내를 제공한다`() {
        val responseText = """{"error": {"code": 429, "message": "Resource has been exhausted (e.g. check quota)."}}"""
        val msg = gateway.formatHttpError(429, responseText, "gemini-3.7-flash")
        assertEquals("Gemini 요청 한도 초과 (429): 분당 요청 수(RPM) 또는 일일 사용량이 소진되었습니다. 잠시 후 다시 시도하거나 Flash-Lite 모델을 사용해 보세요.", msg)
    }

    @Test
    fun `HTTP 503 과부하 시 잠시 후 재시도 안내를 제공한다`() {
        val responseText = """{"error": {"code": 503, "message": "The model is overloaded. Please try again later."}}"""
        val msg = gateway.formatHttpError(503, responseText, "gemini-3.7-flash")
        assertEquals("Gemini 서버 과부하/점검 중 (503): Google 서버가 일시적으로 지연되고 있습니다. 잠시 후 다시 시도하거나 다른 모델을 선택해 주세요.", msg)
    }

    @Test
    fun `SocketTimeoutException 발생 시 120초 초과 및 Flash-Lite 권장 안내를 제공한다`() {
        val msg = gateway.formatNetworkError(java.net.SocketTimeoutException("Read timed out"))
        assertEquals("Gemini 응답 시간 초과(타임아웃): 모델이 제한 시간(120초) 내에 응답을 마치지 못했습니다. 복잡한 추론 모델 대신 빠른 Flash-Lite 모델을 사용하거나 잠시 후 다시 시도해 주세요.", msg)
    }

    @Test
    fun `UnknownHostException 발생 시 네트워크 연결 점검 안내를 제공한다`() {
        val msg = gateway.formatNetworkError(java.net.UnknownHostException("generativelanguage.googleapis.com"))
        assertEquals("네트워크 연결 실패: 인터넷 연결이 끊겼거나 Google 서버 주소를 찾을 수 없습니다. Wi-Fi 또는 모바일 데이터 상태를 확인해 주세요.", msg)
    }
}
