package com.example.gemgemgen.analysis.domain

object AnalysisTxtCountPolicy {
    const val MIN_COUNT = 10
    const val MAX_COUNT = 150
    const val DEFAULT_COUNT = 50

    fun coerce(value: Int): Int {
        return value.coerceIn(MIN_COUNT, MAX_COUNT)
    }
}

