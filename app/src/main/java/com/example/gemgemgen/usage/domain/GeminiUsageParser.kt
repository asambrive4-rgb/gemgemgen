// 역할: 렌더링된 제미나이 사용량 페이지의 DOM 본문 텍스트에서 5시간 한도와 주간 한도 퍼센트 및 리셋 안내 문구를 정규식으로 추출합니다.
package com.example.gemgemgen.usage.domain

object GeminiUsageParser {
    const val USAGE_URL = "https://gemini.google.com/usage"
    const val LOGIN_URL =
        "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fgemini.google.com%2Fusage"

    private const val DEFAULT_FIVE_HOUR_RESET_TEXT = "5시간 주기 자동 초기화"
    private const val DEFAULT_WEEKLY_RESET_TEXT = "주간 주기 자동 초기화"

    private val PERCENT_REGEX = Regex("""(\d{1,3})\s*%""")

    private val EXPLICIT_RESET_LINE_REGEX = Regex(
        pattern = """(?im)^[^\n\r]*(?:resets?\b|초기화|재설정|리셋|갱신)[^\n\r]*$"""
    )

    private val INLINE_RESET_PHRASE_REGEX = Regex(
        pattern = """(?i)(?:resets?\b[^\n\r]*|[^\n\r]*(?:초기화|재설정|리셋|갱신)[^\n\r]*)"""
    )

    private val TIME_HINT_LINE_REGEX = Regex(
        pattern = """(?im)^[^\n\r]*(?:\d+\s*(?:h|hr|hrs|hour|hours|m|min|mins|minute|minutes|d|day|days|시간|분|일)\b|\d{1,2}:\d{2})[^\n\r]*$"""
    )

    private val LEADING_USAGE_NOISE_REGEX = Regex(
        pattern = """^(?i)(?:used|remaining|사용됨|사용중|사용|남음)\s*[-·•:|]?\s*"""
    )

    private val SECTION_HEADER_NOISE_REGEX = Regex(
        pattern = """(?i)^(?:5\s*[- ]?\s*hour|five\s*[- ]?\s*hour|current|daily|weekly|5\s*시간|단기|주간|일일|한도|사용량|limit|usage|quota)\b.*$"""
    )

    /**
     * 이동된 URL이 구글 로그인 페이지(accounts.google.com)인지 판별합니다.
     */
    fun isAuthRedirectUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val normalized = url.lowercase()
        return normalized.contains("accounts.google.com") ||
            normalized.contains("/servicelogin") ||
            normalized.contains("/v3/signin")
    }

    /**
     * DOM 본문(document.body.innerText)에 퍼센트(%) 수치가 2개 이상 렌더링되었는지 확인합니다.
     */
    fun hasMinimumPercentageTokens(bodyText: String): Boolean {
        if (bodyText.isBlank()) return false
        return PERCENT_REGEX.findAll(bodyText).take(2).count() >= 2
    }

    /**
     * 렌더링된 본문 텍스트에서 첫 번째(5시간 윈도우)와 두 번째(주간 한도) 퍼센트 및 각각의 리셋 문구를 추출합니다.
     */
    fun parse(bodyText: String, updatedAt: Long): GeminiUsageState.Success? {
        if (bodyText.isBlank()) return null
        val matches = PERCENT_REGEX.findAll(bodyText).toList()
        if (matches.size < 2) return null

        val firstMatch = matches[0]
        val secondMatch = matches[1]

        val fiveHourPercent = firstMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 100) ?: return null
        val weeklyPercent = secondMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 100) ?: return null

        val betweenSegment = bodyText.substring(firstMatch.range.last + 1, secondMatch.range.first)
        val beforeFirstSegment = bodyText.substring(0, firstMatch.range.first).takeLast(200)
        val afterSecondEnd = (secondMatch.range.last + 1 + 400).coerceAtMost(bodyText.length)
        val afterSecondSegment = bodyText.substring(secondMatch.range.last + 1, afterSecondEnd)
        val beforeSecondSegment = betweenSegment.takeLast(200)

        val fiveHourResetText = extractResetText(
            primarySegment = betweenSegment,
            fallbackSegment = beforeFirstSegment,
            defaultText = DEFAULT_FIVE_HOUR_RESET_TEXT
        )
        val weeklyResetText = extractResetText(
            primarySegment = afterSecondSegment,
            fallbackSegment = beforeSecondSegment,
            defaultText = DEFAULT_WEEKLY_RESET_TEXT
        )

        return createSuccess(
            fiveHourPercent = fiveHourPercent,
            fiveHourResetText = fiveHourResetText,
            weeklyPercent = weeklyPercent,
            weeklyResetText = weeklyResetText,
            updatedAt = updatedAt
        )
    }

    /**
     * 브릿지에서 전달된 파싱 결과를 정규화하여 [GeminiUsageState.Success] 인스턴스를 생성합니다.
     */
    fun createSuccess(
        fiveHourPercent: Int,
        fiveHourResetText: String,
        weeklyPercent: Int,
        weeklyResetText: String,
        updatedAt: Long
    ): GeminiUsageState.Success {
        return GeminiUsageState.Success(
            fiveHourPercent = fiveHourPercent.coerceIn(0, 100),
            fiveHourResetText = cleanResetText(fiveHourResetText).ifBlank { DEFAULT_FIVE_HOUR_RESET_TEXT },
            weeklyPercent = weeklyPercent.coerceIn(0, 100),
            weeklyResetText = cleanResetText(weeklyResetText).ifBlank { DEFAULT_WEEKLY_RESET_TEXT },
            updatedAt = updatedAt
        )
    }

    private fun extractResetText(
        primarySegment: String,
        fallbackSegment: String,
        defaultText: String
    ): String {
        findResetCandidate(primarySegment)?.let { return it }
        findResetCandidate(fallbackSegment)?.let { return it }
        return defaultText
    }

    private fun findResetCandidate(segment: String): String? {
        if (segment.isBlank()) return null

        val lines = segment
            .lineSequence()
            .map { cleanResetText(it) }
            .filter { it.isNotBlank() }
            .toList()

        // 1순위: 명시적 리셋 키워드(Resets, 초기화, 재설정, 리셋, 갱신)가 포함된 줄
        for (line in lines) {
            if (EXPLICIT_RESET_LINE_REGEX.matches(line)) {
                val inlineMatch = INLINE_RESET_PHRASE_REGEX.find(line)?.value?.let(::cleanResetText)
                return if (!inlineMatch.isNullOrBlank()) inlineMatch else line
            }
        }

        // 2순위: 시간/카운트다운 표현(예: 2 hr 14 min, 3시간 20분, 09:00)이 포함된 줄
        for (line in lines) {
            if (!SECTION_HEADER_NOISE_REGEX.matches(line) && TIME_HINT_LINE_REGEX.matches(line)) {
                return line
            }
        }

        return null
    }

    private fun cleanResetText(raw: String): String {
        return raw
            .trim()
            .replace(LEADING_USAGE_NOISE_REGEX, "")
            .trim()
            .trim('-', '·', '•', ':', '|')
            .trim()
    }
}
