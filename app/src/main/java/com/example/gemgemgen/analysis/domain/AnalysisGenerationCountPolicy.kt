// 역할: AI 분석 시 요청할 생성 후보 개수의 유효 벾위를 제한하고 결정합니다.
package com.example.gemgemgen.analysis.domain

/**
 * 「생성」모드(카드 결과)의 후보 개수 정책.
 * TXT 생성용 [AnalysisTxtCountPolicy]와 분리한다.
 */
object AnalysisGenerationCountPolicy {
    const val FIXED_COUNT = 6
}
