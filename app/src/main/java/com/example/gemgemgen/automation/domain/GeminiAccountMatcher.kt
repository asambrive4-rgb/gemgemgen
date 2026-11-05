// 역할: Gemini 계정 노드 후보 정보에서 이메일을 추출하고, 목표 계정 식별자(이메일/별칭)와의 유사도 점수를 계산하여 일치 여부를 판별하는 도메인 매칭 규칙입니다.
package com.example.gemgemgen.automation.domain

internal data class GeminiAccountNodeCandidate(
    val primaryText: String,
    val secondaryText: String,
    val contentDescription: String = ""
)

internal object GeminiAccountMatcher {
    const val EXACT_MATCH_THRESHOLD = 900
    private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")

    fun extractEmail(candidate: GeminiAccountNodeCandidate): String {
        val secondary = candidate.secondaryText.trim()
        if (secondary.contains("@")) return secondary
        EMAIL_REGEX.find(secondary)?.value?.let { return it }
        EMAIL_REGEX.find(candidate.contentDescription)?.value?.let { return it }
        EMAIL_REGEX.find(candidate.primaryText)?.value?.let { return it }
        return secondary
    }

    /**
     * 후보 계정이 사용자가 지정한 [targetEmail] 또는 [targetAlias]와 얼마나 일치하는지 점수를 계산합니다.
     * - 900점 이상: 이메일 전체 일치, 이메일 로컬 파트(@ 앞부분) 정확 일치, 또는 이메일 미지정 시 표시 이름 정확 일치
     * - 1~899점: 이메일 미지정 시 별칭 부분 일치 (유사 계정 중 가장 짧은 이메일 로컬 파트를 우선 선택)
     * - 0점 이하: 불일치
     */
    fun scoreCandidate(
        candidate: GeminiAccountNodeCandidate,
        targetEmail: String,
        targetAlias: String
    ): Int {
        val rawEmail = extractEmail(candidate).trim().lowercase()
        val localPart = rawEmail.substringBefore("@")
        val displayName = candidate.primaryText.trim().lowercase()
        val description = candidate.contentDescription.trim().lowercase()

        val normTargetEmail = targetEmail.trim().lowercase()
        val normTargetLocal = normTargetEmail.substringBefore("@")
        val normTargetAlias = targetAlias.trim().lowercase()

        if (normTargetEmail.isNotBlank()) {
            if (rawEmail.isNotBlank() && rawEmail == normTargetEmail) {
                return 1000
            }
            if (normTargetLocal.isNotBlank() && localPart.isNotBlank() && localPart == normTargetLocal) {
                return 950
            }
            if (!normTargetEmail.contains("@") && displayName.isNotBlank() && displayName == normTargetEmail) {
                return 900
            }
            return 0
        }

        if (normTargetAlias.isBlank()) return 0

        if (rawEmail.isNotBlank() && rawEmail == normTargetAlias) {
            return 1000
        }
        val aliasLocalQuery = normTargetAlias.substringBefore("@")
        if (aliasLocalQuery.isNotBlank() && localPart.isNotBlank() && localPart == aliasLocalQuery) {
            return 950
        }
        if (displayName.isNotBlank() && displayName == normTargetAlias) {
            return 900
        }
        if (aliasLocalQuery.isNotBlank() && localPart.isNotBlank() && localPart.contains(aliasLocalQuery)) {
            return (700 - localPart.length).coerceAtLeast(1)
        }
        if (displayName.isNotBlank() && displayName.contains(normTargetAlias)) {
            return (500 - displayName.length).coerceAtLeast(1)
        }
        if (description.isNotBlank() && description.contains(normTargetAlias)) {
            return 100
        }
        return 0
    }

    fun isExactMatch(
        candidate: GeminiAccountNodeCandidate,
        targetEmail: String,
        targetAlias: String
    ): Boolean = scoreCandidate(candidate, targetEmail, targetAlias) >= EXACT_MATCH_THRESHOLD
}
