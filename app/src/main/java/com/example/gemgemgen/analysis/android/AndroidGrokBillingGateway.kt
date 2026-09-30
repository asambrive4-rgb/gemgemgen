// 역할: Grok 서비스의 사용량 한도 및 결제 쿼터 상태를 조회합니다.
package com.example.gemgemgen.analysis.android

import com.example.gemgemgen.analysis.domain.GrokQuotaPolicy
import com.example.gemgemgen.analysis.usecase.AnalysisException
import com.example.gemgemgen.analysis.usecase.GrokBillingGateway
import com.example.gemgemgen.analysis.usecase.GrokQuotaInfo
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * SuperGrok 구독 사용량. progrok `billing` 과 동일 엔드포인트.
 * monthlyLimit/used.val 은 cent 단위로 보이며, 비율 계산만 사용한다.
 */
class AndroidGrokBillingGateway : GrokBillingGateway {

    override suspend fun fetchQuota(accessToken: String): GrokQuotaInfo {
        return try {
            val connection = (URL(BILLING_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Accept", "application/json")
            }
            val (code, body) = executeHttpRequest(connection)

            if (code !in 200..299) {
                val detail = extractJsonErrorMessage(body)
                throw AnalysisException(
                    "Grok 크레딧 정보를 가져오지 못했습니다. ($code)${if (detail.isNotBlank()) ": $detail" else ""}"
                )
            }

            val root = analysisJson.parseToJsonElement(body).jsonObject
            val config = root["config"]?.jsonObject
                ?: throw AnalysisException("Grok 크레딧 응답 형식이 올바르지 않습니다.")
            val limit = config["monthlyLimit"]?.jsonObject?.valNumber()
                ?: throw AnalysisException("Grok 크레딧 한도 정보가 없습니다.")
            val used = config["used"]?.jsonObject?.valNumber()
                ?: throw AnalysisException("Grok 크레딧 사용량 정보가 없습니다.")

            GrokQuotaInfo(
                remainingPercent = GrokQuotaPolicy.remainingPercent(used = used, limit = limit)
            )
        } catch (error: AnalysisException) {
            throw error
        } catch (error: Exception) {
            throw AnalysisException(
                formatAnalysisNetworkError(
                    error = error,
                    serviceName = "Grok 크레딧 조회",
                    serverLabel = "Grok 서버",
                    timeoutMillis = READ_TIMEOUT_MS
                )
            )
        }
    }

    private fun kotlinx.serialization.json.JsonObject.valNumber(): Long? {
        val raw = this["val"]?.jsonPrimitive?.content ?: return null
        return raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong()
    }

    private companion object {
        const val BILLING_URL = "https://cli-chat-proxy.grok.com/v1/billing"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 10_000
    }
}
