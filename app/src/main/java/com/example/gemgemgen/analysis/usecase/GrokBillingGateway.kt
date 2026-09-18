// 역할: Grok 서비스의 사용량 한도 및 결제 쿼터 상태를 조회하는 게이트웨이 인터페이스입니다.
package com.example.gemgemgen.analysis.usecase

data class GrokQuotaInfo(
    val remainingPercent: Int,
    val usedVal: Long,
    val limitVal: Long
)

interface GrokBillingGateway {
    suspend fun fetchQuota(accessToken: String): GrokQuotaInfo
}
