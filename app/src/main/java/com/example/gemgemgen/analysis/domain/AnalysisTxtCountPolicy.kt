// 역할: 생성된 분석 텍스트의 줄 수 및 항목 개수 제한 정책을 관리합니다.
package com.example.gemgemgen.analysis.domain

object AnalysisTxtCountPolicy {
    const val MIN_COUNT = 10
    const val MAX_COUNT = 150
    const val DEFAULT_COUNT = 50

    fun coerce(value: Int): Int {
        return value.coerceIn(MIN_COUNT, MAX_COUNT)
    }
}

