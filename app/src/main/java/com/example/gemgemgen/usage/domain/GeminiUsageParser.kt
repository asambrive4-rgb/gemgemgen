// 역할: 렌더링된 제미나이 사용량 DOM 본문에서 한도 퍼센트·리셋 문구·이메일을 추출하고, 초기화까지 남은 시간 계산 및 5시간·주간 만료 보정 로직을 제공합니다.
package com.example.gemgemgen.usage.domain

import java.util.Calendar

object GeminiUsageParser {
    const val USAGE_URL = "https://gemini.google.com/usage"
    const val LOGIN_URL =
        "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fgemini.google.com%2Fusage"

    const val FIVE_HOUR_WINDOW_MILLIS = 5L * 3600_000L
    const val WEEKLY_WINDOW_MILLIS = 7L * 24L * 3600_000L

    private const val DEFAULT_FIVE_HOUR_RESET_TEXT = "5시간 주기 자동 초기화"
    private const val DEFAULT_WEEKLY_RESET_TEXT = "주간 주기 자동 초기화"

    private val PERCENT_REGEX = Regex("""(\d{1,3})\s*%""")

    private val EMAIL_REGEX = Regex(
        pattern = """\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"""
    )

    private val EXPLICIT_RESET_LINE_REGEX = Regex(
        pattern = """(?im)^[^\n\r]*(?:resets?\b|초기화|재설정|리셋|갱신)[^\n\r]*$"""
    )

    private val INLINE_RESET_PHRASE_REGEX = Regex(
        pattern = """(?i)(?:resets?\b[^\n\r]*|[^\n\r]*(?:초기화|재설정|리셋|갱신)[^\n\r]*)"""
    )

    private val TIME_HINT_LINE_REGEX = Regex(
        pattern = """(?im)^[^\n\r]*(?:\d+\s*(?:(?:h|hr|hrs|hour|hours|m|min|mins|minute|minutes|d|day|days)\b|(?:개월|주|일|시간|시|분|초))|\d{1,2}\s*월\s*\d{1,2}\s*일|\d{1,2}:\d{2}|\d{1,2}\s*/\s*\d{1,2})[^\n\r]*$"""
    )

    private val LEADING_USAGE_NOISE_REGEX = Regex(
        pattern = """^(?i)(?:used|remaining|사용됨|사용중|사용|남음)\s*[-·•:|]?\s*"""
    )

    private val SECTION_HEADER_NOISE_REGEX = Regex(
        pattern = """(?i)^(?:5\s*[- ]?\s*hour|five\s*[- ]?\s*hour|current|daily|weekly|5\s*시간|단기|주간|일일|한도|사용량|limit|usage|quota)\b.*$"""
    )

    private val RESET_SUFFIX_NOISE_REGEX = Regex(
        pattern = """(?i)(?:\s*에\s*(?:초기화됩니다|초기화|재설정됩니다|재설정|리셋됩니다|리셋|갱신됩니다|갱신)|\s*(?:자동\s*)?(?:초기화됩니다|초기화|재설정됩니다|재설정|리셋됩니다|리셋))$"""
    )

    private val RESET_PREFIX_NOISE_REGEX = Regex(
        pattern = """^(?i)(?:resets?\s+at\s+|resets?\s+in\s+|resets?\s+)"""
    )

    private val KOREAN_TARGET_TIME_REGEX = Regex(
        pattern = """(?:(\d{1,2})\s*(?:월|/)\s*(\d{1,2})\s*일?\s*)?(?:(오전|오후)\s*)?(\d{1,2})(?::(\d{2})|\s*시(?!간)(?:\s*(\d{1,2})\s*분)?)"""
    )

    private val RELATIVE_DAYS_REGEX = Regex("""(\d+)\s*(?:일|(?:d|day|days)\b)""", RegexOption.IGNORE_CASE)
    private val RELATIVE_HOURS_REGEX = Regex("""(\d+)\s*(?:시간|(?:h|hr|hrs|hour|hours)\b)""", RegexOption.IGNORE_CASE)
    private val RELATIVE_MINUTES_REGEX = Regex("""(\d+)\s*(?:분|(?:m|min|mins|minute|minutes)\b)""", RegexOption.IGNORE_CASE)

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
     * 이동된 URL이 구글 쿠키 충돌/오류 페이지(CookieMismatch)인지 판별합니다.
     */
    fun isCookieMismatchUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val normalized = url.lowercase()
        return normalized.contains("cookiemismatch") ||
            normalized.contains("nocookies")
    }

    /**
     * DOM 본문(document.body.innerText)에 퍼센트(%) 수치가 2개 이상 렌더링되었는지 확인합니다.
     */
    fun hasMinimumPercentageTokens(bodyText: String): Boolean {
        if (bodyText.isBlank()) return false
        return PERCENT_REGEX.findAll(bodyText).take(2).count() >= 2
    }

    /**
     * DOM 텍스트 또는 속성 문자열에서 Google 계정 이메일 주소를 추출합니다.
     */
    fun extractAccountEmail(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        return EMAIL_REGEX.find(rawText)?.value?.trim()?.lowercase()
    }

    /**
     * 반복되는 `"에 초기화"` 등의 군더더기 문구를 제거하여 핵심 시각/주기를 24시간제 및 축약 포맷(예: 10/20/14:00, 14:00)으로 짧게 반환합니다.
     * 예: `"오후 2:00에 초기화"` -> `"14:00"`, `"10월 20일 오후 2:00에 초기화"` -> `"10/20/14:00"`
     */
    fun formatShortResetTime(rawText: String): String {
        val cleaned = cleanResetText(rawText)
            .replace(RESET_PREFIX_NOISE_REGEX, "")
            .replace(RESET_SUFFIX_NOISE_REGEX, "")
            .trim()
        if (cleaned.isBlank()) return ""

        if (RELATIVE_TIME_REGEX.containsMatchIn(cleaned)) {
            return cleaned
        }

        val parsed = parseTargetTime(cleaned)
        if (parsed != null) {
            return parsed.formatFormattedTime()
        }
        return cleaned.ifBlank { cleanResetText(rawText) }
    }

    private val RELATIVE_TIME_REGEX = Regex(
        pattern = """(?:\d+\s*(?:개월|주|일|시간|시|분|초)\s*(?:후|뒤)|남음)"""
    )

    /**
     * 리셋 안내 문구(상대 시간/절대 시각)에서 현재 시각([nowMillis]) 기준 초기화까지 남은 시간(밀리초)을 계산합니다.
     * - 구체적인 시간/시각 단서가 없는 기본 문구("5시간 주기 자동 초기화" 등)는 null을 반환합니다.
     * - [isFiveHourWindow]가 true일 때 오늘 이미 지난 시각이면 +1일로 밀리지 않고 음수(<= 0L, 초기화 만료)를 반환합니다.
     */
    fun computeRemainingResetMillis(
        rawText: String,
        nowMillis: Long = System.currentTimeMillis(),
        updatedAt: Long = 0L,
        isFiveHourWindow: Boolean = false
    ): Long? {
        val initialCleaned = cleanResetText(rawText)
        if (initialCleaned.isBlank() ||
            initialCleaned.contains("주기") ||
            initialCleaned == DEFAULT_FIVE_HOUR_RESET_TEXT ||
            initialCleaned == DEFAULT_WEEKLY_RESET_TEXT
        ) {
            return null
        }

        val cleaned = initialCleaned
            .replace(RESET_PREFIX_NOISE_REGEX, "")
            .replace(RESET_SUFFIX_NOISE_REGEX, "")
            .trim()
        if (cleaned.isBlank()) return null

        val hasColonTimeOrAmPm = cleaned.contains(":") || cleaned.contains("오전") || cleaned.contains("오후") ||
            Regex("""\d{1,2}\s*(?:월|/)\s*\d{1,2}""").containsMatchIn(cleaned)

        if (!hasColonTimeOrAmPm || RELATIVE_TIME_REGEX.containsMatchIn(cleaned)) {
            val daysMatch = RELATIVE_DAYS_REGEX.find(cleaned)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val hoursMatch = RELATIVE_HOURS_REGEX.find(cleaned)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val minutesMatch = RELATIVE_MINUTES_REGEX.find(cleaned)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val durationMillis = daysMatch * 24 * 3600_000L + hoursMatch * 3600_000L + minutesMatch * 60_000L
            if (durationMillis > 0L) {
                return if (updatedAt > 0L && updatedAt <= nowMillis) {
                    (updatedAt + durationMillis) - nowMillis
                } else {
                    durationMillis
                }
            }
        }

        val parsedTime = parseTargetTime(cleaned)
        if (parsedTime != null) {
            val anchorMillis = if (updatedAt > 0L && updatedAt <= nowMillis) updatedAt else nowMillis
            val targetCal = Calendar.getInstance().apply {
                timeInMillis = anchorMillis
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, parsedTime.hour24)
                set(Calendar.MINUTE, parsedTime.minute)
                if (parsedTime.month != null && parsedTime.day != null) {
                    set(Calendar.MONTH, parsedTime.month - 1)
                    set(Calendar.DAY_OF_MONTH, parsedTime.day)
                    if (timeInMillis < anchorMillis - 30L * 24L * 3600_000L) {
                        add(Calendar.YEAR, 1)
                    }
                } else {
                    if (timeInMillis <= anchorMillis) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                    if (isFiveHourWindow && timeInMillis - anchorMillis > 6 * 3600_000L) {
                        add(Calendar.DAY_OF_YEAR, -1)
                    }
                }
            }
            return targetCal.timeInMillis - nowMillis
        }

        return null
    }

    /**
     * 다음 리셋 시점의 절대 밀리초(타겟 시각)를 계산합니다.
     * - 리셋 시각이 이미 지난 경우 주기(5시간 / 7일)를 가산하여 현재([nowMillis])보다 미래인 다음 타겟 밀리초를 반환합니다.
     * - 구체적인 시간 단서가 없는 기본 문구("5시간 주기 자동 초기화" 등)는 null을 반환합니다.
     */
    fun computeNextResetTargetMillis(
        rawText: String,
        nowMillis: Long = System.currentTimeMillis(),
        updatedAt: Long = 0L,
        isFiveHourWindow: Boolean = false
    ): Long? {
        val initialCleaned = cleanResetText(rawText)
        if (initialCleaned.isBlank() ||
            initialCleaned.contains("주기") ||
            initialCleaned == DEFAULT_FIVE_HOUR_RESET_TEXT ||
            initialCleaned == DEFAULT_WEEKLY_RESET_TEXT
        ) {
            return null
        }

        val cleaned = initialCleaned
            .replace(RESET_PREFIX_NOISE_REGEX, "")
            .replace(RESET_SUFFIX_NOISE_REGEX, "")
            .trim()
        if (cleaned.isBlank()) return null

        val period = if (isFiveHourWindow) FIVE_HOUR_WINDOW_MILLIS else WEEKLY_WINDOW_MILLIS

        if (RELATIVE_TIME_REGEX.containsMatchIn(cleaned)) {
            val daysMatch = RELATIVE_DAYS_REGEX.find(cleaned)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val hoursMatch = RELATIVE_HOURS_REGEX.find(cleaned)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val minutesMatch = RELATIVE_MINUTES_REGEX.find(cleaned)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val durationMillis = daysMatch * 24 * 3600_000L + hoursMatch * 3600_000L + minutesMatch * 60_000L
            if (durationMillis > 0L) {
                val anchor = if (updatedAt > 0L && updatedAt <= nowMillis) updatedAt else nowMillis
                var targetMillis = anchor + durationMillis
                while (targetMillis <= nowMillis) {
                    targetMillis += period
                }
                return targetMillis
            }
            return null
        }

        val parsedTime = parseTargetTime(cleaned)
        if (parsedTime != null) {
            val anchorMillis = if (updatedAt > 0L && updatedAt <= nowMillis) updatedAt else nowMillis
            val targetCal = Calendar.getInstance().apply {
                timeInMillis = anchorMillis
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, parsedTime.hour24)
                set(Calendar.MINUTE, parsedTime.minute)
                if (parsedTime.month != null && parsedTime.day != null) {
                    set(Calendar.MONTH, parsedTime.month - 1)
                    set(Calendar.DAY_OF_MONTH, parsedTime.day)
                    if (timeInMillis < anchorMillis - 30L * 24L * 3600_000L) {
                        add(Calendar.YEAR, 1)
                    }
                } else {
                    if (timeInMillis <= anchorMillis) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                    if (isFiveHourWindow && timeInMillis - anchorMillis > 6 * 3600_000L) {
                        add(Calendar.DAY_OF_YEAR, -1)
                    }
                }
            }

            var targetMillis = targetCal.timeInMillis
            while (targetMillis <= nowMillis) {
                targetMillis += period
            }
            return targetMillis
        }

        return null
    }

    /**
     * 다음 리셋 시각까지 남은 시간(밀리초)을 계산합니다.
     * - 이미 리셋 시각이 지난 경우 주기(5시간 / 7일)를 가산하여 현재([nowMillis])보다 미래인 다음 리셋까지의 잔여 밀리초(> 0L)를 반환합니다.
     * - 구체적인 시간 단서가 없는 기본 문구("5시간 주기 자동 초기화" 등)는 null을 반환합니다.
     */
    fun computeNextResetRemainingMillis(
        rawText: String,
        nowMillis: Long = System.currentTimeMillis(),
        updatedAt: Long = 0L,
        isFiveHourWindow: Boolean = false
    ): Long? {
        val targetMillis = computeNextResetTargetMillis(rawText, nowMillis, updatedAt, isFiveHourWindow) ?: return null
        return (targetMillis - nowMillis).coerceAtLeast(0L)
    }

    /**
     * 다이얼로그 및 카드 표시용으로 `"에 초기화"` 반복 문구를 제거하고, 24시간제/축약 일시와 함께 현재 시각 기준 남은 시간을 병기합니다.
     * 예: `"오후 2:00에 초기화"` -> `"14:00 (4시간 10분 후)"`, `"10월 20일 오후 2:00에 초기화"` -> `"10/20/14:00 (4일 4시간 후)"`
     * 리셋 시각이 이미 지났다면 다음 주기(5시간 / 7일)의 초기화 일시 및 남은 시간을 계산하여 반환합니다.
     * 구체적인 시간 단서가 없는 기본 문구("5시간 주기 자동 초기화" 등)는 "5시간 주기" 등의 주기 문구를 반환합니다.
     */
    fun formatCompactResetWithRemaining(
        rawText: String,
        nowMillis: Long = System.currentTimeMillis(),
        updatedAt: Long = 0L,
        isFiveHourWindow: Boolean = false
    ): String {
        val initialCleaned = cleanResetText(rawText)
        if (initialCleaned.isBlank()) return ""

        val cleaned = initialCleaned
            .replace(RESET_PREFIX_NOISE_REGEX, "")
            .replace(RESET_SUFFIX_NOISE_REGEX, "")
            .trim()
        if (cleaned.isBlank()) return ""

        if (cleaned.contains("주기") ||
            initialCleaned == DEFAULT_FIVE_HOUR_RESET_TEXT ||
            initialCleaned == DEFAULT_WEEKLY_RESET_TEXT
        ) {
            return cleaned.ifBlank { initialCleaned }
        }

        if (RELATIVE_TIME_REGEX.containsMatchIn(cleaned)) {
            val daysMatch = RELATIVE_DAYS_REGEX.find(cleaned)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val targetMillis = computeNextResetTargetMillis(rawText, nowMillis, updatedAt, isFiveHourWindow)
            if (targetMillis != null && updatedAt > 0L && updatedAt <= nowMillis) {
                val targetCal = Calendar.getInstance().apply { timeInMillis = targetMillis }
                val hourStr = targetCal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
                val minuteStr = targetCal.get(Calendar.MINUTE).toString().padStart(2, '0')
                val timeStr = if (daysMatch > 0L) {
                    val month = targetCal.get(Calendar.MONTH) + 1
                    val day = targetCal.get(Calendar.DAY_OF_MONTH)
                    "$month/$day/$hourStr:$minuteStr"
                } else {
                    "$hourStr:$minuteStr"
                }
                val remainingMillis = targetMillis - nowMillis
                val durationStr = formatRemainingDuration(remainingMillis)
                return "$timeStr ($durationStr)"
            }
            return cleaned
        }

        val parsedTime = parseTargetTime(cleaned)
        if (parsedTime != null) {
            val targetMillis = computeNextResetTargetMillis(rawText, nowMillis, updatedAt, isFiveHourWindow)
            if (targetMillis != null) {
                val nextCal = Calendar.getInstance().apply { timeInMillis = targetMillis }
                val hourStr = nextCal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
                val minuteStr = nextCal.get(Calendar.MINUTE).toString().padStart(2, '0')
                val hasMonthDay = parsedTime.month != null && parsedTime.day != null
                val timePart = if (hasMonthDay) {
                    val month = nextCal.get(Calendar.MONTH) + 1
                    val day = nextCal.get(Calendar.DAY_OF_MONTH)
                    "$month/$day/$hourStr:$minuteStr"
                } else {
                    "$hourStr:$minuteStr"
                }
                val remainingMillis = targetMillis - nowMillis
                val durationText = formatRemainingDuration(remainingMillis)
                return "$timePart ($durationText)"
            }
        }

        return cleaned.ifBlank { initialCleaned }
    }

    private fun formatRemainingDuration(diffMillis: Long): String {
        if (diffMillis <= 0L) return "곧 초기화"
        val totalMinutes = (diffMillis + 30_000L) / 60_000L
        if (totalMinutes <= 0L) return "곧 초기화"

        val days = totalMinutes / (24L * 60L)
        val hours = (totalMinutes % (24L * 60L)) / 60L
        val minutes = totalMinutes % 60L

        return when {
            days > 0L -> if (hours > 0L) "${days}일 ${hours}시간 후" else "${days}일 후"
            hours > 0L -> if (minutes > 0L) "${hours}시간 ${minutes}분 후" else "${hours}시간 후"
            else -> "${minutes}분 후"
        }
    }

    private data class ParsedTargetTime(
        val month: Int?,
        val day: Int?,
        val hour24: Int,
        val minute: Int
    ) {
        fun formatFormattedTime(): String {
            val timePart = "${hour24.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
            return if (month != null && day != null) {
                "$month/$day/$timePart"
            } else {
                timePart
            }
        }
    }

    private fun parseTargetTime(text: String): ParsedTargetTime? {
        val match = KOREAN_TARGET_TIME_REGEX.find(text) ?: return null
        val month = match.groupValues[1].toIntOrNull()
        val day = match.groupValues[2].toIntOrNull()
        val amPm = match.groupValues[3]
        val rawHour = match.groupValues[4].toIntOrNull() ?: return null
        val colonMinute = match.groupValues[5].toIntOrNull()
        val koreanMinute = match.groupValues[6].toIntOrNull()
        val minute = (colonMinute ?: koreanMinute ?: 0).coerceIn(0, 59)

        val hour24 = when {
            amPm == "오후" && rawHour in 1..11 -> rawHour + 12
            amPm == "오전" && rawHour == 12 -> 0
            else -> rawHour.coerceIn(0, 23)
        }

        val hasValidMonthDay = month != null && day != null && month in 1..12 && day in 1..31
        val hasTimeIndicator = !amPm.isNullOrBlank() || colonMinute != null || koreanMinute != null || hasValidMonthDay || match.value.contains("시")
        if (!hasTimeIndicator) return null

        return ParsedTargetTime(
            month = if (hasValidMonthDay) month else null,
            day = if (hasValidMonthDay) day else null,
            hour24 = hour24,
            minute = minute
        )
    }

    private fun computeRemainingDurationText(
        parsedTime: ParsedTargetTime,
        nowMillis: Long
    ): String? {
        val targetCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, parsedTime.hour24)
            set(Calendar.MINUTE, parsedTime.minute)
            if (parsedTime.month != null && parsedTime.day != null) {
                set(Calendar.MONTH, parsedTime.month - 1)
                set(Calendar.DAY_OF_MONTH, parsedTime.day)
                if (timeInMillis < nowMillis - 30L * 24L * 3600_000L) {
                    add(Calendar.YEAR, 1)
                }
            } else {
                if (timeInMillis <= nowMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }

        val diffMillis = targetCal.timeInMillis - nowMillis
        if (diffMillis <= 0L) return null
        val totalMinutes = (diffMillis + 30_000L) / 60_000L
        if (totalMinutes <= 0L) return "곧 초기화"

        val days = totalMinutes / (24L * 60L)
        val hours = (totalMinutes % (24L * 60L)) / 60L
        val minutes = totalMinutes % 60L

        return when {
            days > 0L -> if (hours > 0L) "${days}일 ${hours}시간 후" else "${days}일 후"
            hours > 0L -> if (minutes > 0L) "${hours}시간 ${minutes}분 후" else "${hours}시간 후"
            else -> "${minutes}분 후"
        }
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

        val fiveHourFromBetween = findResetCandidate(betweenSegment)
        val fiveHourResetText = fiveHourFromBetween
            ?: findResetCandidate(beforeFirstSegment)
            ?: DEFAULT_FIVE_HOUR_RESET_TEXT

        val remainingBetweenSegment = if (fiveHourFromBetween != null) {
            dropThroughFirstMatchingLine(betweenSegment, fiveHourFromBetween).takeLast(200)
        } else {
            betweenSegment.takeLast(200)
        }

        val weeklyResetText = extractResetText(
            primarySegment = afterSecondSegment,
            fallbackSegment = remainingBetweenSegment,
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

    private fun dropThroughFirstMatchingLine(segment: String, matchedCandidate: String): String {
        val lines = segment.lines()
        val matchIdx = lines.indexOfFirst { line ->
            val cleaned = cleanResetText(line)
            cleaned == matchedCandidate || cleaned.contains(matchedCandidate)
        }
        return if (matchIdx >= 0 && matchIdx + 1 < lines.size) {
            lines.subList(matchIdx + 1, lines.size).joinToString("\n")
        } else {
            ""
        }
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

        // 2순위: 시간/카운트다운 표현(예: 2 hr 14 min, 3시간 20분, 09:00, 10/19 오전 9시)이 포함된 줄
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
