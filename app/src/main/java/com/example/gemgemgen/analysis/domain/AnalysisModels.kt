// 역할: AI 분석 요청 상태, 결과 데이터, 세그먼트 모델, 4가지 기본 변주 방향 지시문 및 생성 개수/쿼터 정책을 정의합니다.
package com.example.gemgemgen.analysis.domain

enum class AnalysisStatus {
    IDLE,
    GENERATING,
    SUCCESS,
    ERROR
}

/** 생성 결과 표시 방식. 마지막 생성 모드에 따라 UI를 분기한다. */
enum class AnalysisResultPresentation {
    /** 결과 없음 */
    NONE,
    /** 「생성」모드: 카드 목록 */
    CARDS,
    /** 「TXT 생성」모드: 줄 단위 목록 + 파일 저장 */
    TXT
}

data class AnalysisTargetSegment(
    val text: String,
    val startIndex: Int,
    val endIndex: Int,
    val category: AnalysisCategory,
    /** 연쇄 보완을 포함한 치환 범위 안에서 실제 편집이 허용된 구간들. */
    val editableRanges: List<AnalysisSourceRange> = emptyList()
) {
    val isValid: Boolean
        get() = text.isNotBlank() && startIndex >= 0 && endIndex > startIndex
}

data class AnalysisDetectedSegment(
    val exactText: String,
    val startIndex: Int,
    val endIndex: Int
) {
    val isValid: Boolean
        get() = exactText.isNotBlank() && startIndex >= 0 && endIndex > startIndex
}

data class AnalysisVisualContext(
    val viewpoint: String = "알 수 없음",
    val distance: String = "알 수 없음",
    val visibleScope: String = "알 수 없음",
    val cameraAngle: String = "알 수 없음"
)

data class AnalysisCategoryConstraints(
    val allowed: List<String> = emptyList(),
    val avoid: List<String> = emptyList()
)

data class AnalysisReport(
    val targetSegment: AnalysisDetectedSegment? = null,
    val visualContext: AnalysisVisualContext = AnalysisVisualContext(),
    val categoryConstraints: AnalysisCategoryConstraints = AnalysisCategoryConstraints(),
    /**
     * 1단계 분석 모델이 정한 변주 Goal.
     * 원문 시각/공간 분석 + 선택 방향 칩 + 사용자 추가 요구사항을 반영한다.
     * 비어 있으면 생성 단계에서 카테고리 폴백 Goal을 쓴다.
     */
    val variationGoal: String = "",
    val warnings: List<String> = emptyList(),
    val targetVisualContext: AnalysisVisualContext = AnalysisVisualContext(),
    val cascadingTrace: AnalysisCascadingTrace = AnalysisCascadingTrace(),
    val clarificationQuestion: String = "",
    /** System Instruction 등 명시적으로 변경을 요청하지 않은 보호 구간. */
    val preservedSegments: List<AnalysisSourceRange> = emptyList()
)

data class AnalysisCascadingTrace(
    val newlyVisible: List<String> = emptyList(),
    val disappearing: List<String> = emptyList(),
    val undefinedAreas: List<String> = emptyList(),
    val requiredAdjustments: List<String> = emptyList(),
    val conflictingSegments: List<AnalysisSourceRange> = emptyList()
)

data class AnalysisDirection(
    val id: String,
    val title: String,
    val hint: String
)

/** 모든 카테고리 공통으로 제공하는 기본 추천 방향 칩. */
object AnalysisDefaultDirections {
    val values = listOf(
        AnalysisDirection(
            id = "length-only",
            title = "분량 추가",
            hint = "Do NOT invent new items, ornaments, or extra details; keep the exact same subject, items, and meaning as the original segment, and rewrite it into a noticeably longer, fuller phrasing by expanding the wording of those same elements (e.g. '흰 크롭티, 청바지' -> '깔끔한 흰색 톤의 짧은 크롭 티셔츠 상의와 기본 푸른 데님 소재의 청바지를 매치한 착장')."
        ),
        AnalysisDirection(
            id = "detail-add",
            title = "디테일 추가",
            hint = "Keep the exact original items/nouns as the base, and enrich each element by attaching specific visual details such as prints/graphics, accessories, ornaments, fit, cut, or material modifiers (e.g. '흰 크롭티, 청바지' -> '커다란 해바라기가 전면에 프린팅된 흰 크롭티, 얇은 가죽 벨트 로우라이즈 청바지'). Allow the length to expand naturally to include the added details."
        ),
        AnalysisDirection(
            id = "same-lineage-other-kind",
            title = "같은계열 다른 종류",
            hint = "Stay within the same style family, mood, and vibe as the original segment, but swap each item or element 1:1 to a different kind/sub-type and adjacent color or material within that same family while keeping a similar concise structure (e.g. '흰 크롭티, 청바지' -> '베이지 크롭 나시, 데님 미니스커트')."
        ),
        AnalysisDirection(
            id = "other-lineage",
            title = "다른계열",
            hint = "Shift the style family to a distinctly different realistic lineage (e.g. casual to formal/office, sporty, or chic) while keeping the corresponding pairing structure and concise length of the original segment, without jumping to unrelated fantasy genres (e.g. '흰 크롭티, 청바지' -> '흰 셔츠, 슬랙스')."
        )
    )
}

object AnalysisGenerationCountPolicy {
    const val FIXED_COUNT = 6
}

object AnalysisTxtCountPolicy {
    const val MIN_COUNT = 10
    const val MAX_COUNT = 150
    const val DEFAULT_COUNT = 50

    fun coerce(value: Int): Int = value.coerceIn(MIN_COUNT, MAX_COUNT)
}

object GrokQuotaPolicy {
    fun remainingPercent(used: Long, limit: Long): Int {
        if (limit <= 0L) return 0
        val remaining = (limit - used).coerceAtLeast(0L)
        val percent = ((remaining * 100.0) / limit.toDouble()).toInt()
        return percent.coerceIn(0, 100)
    }
}

const val MODEL_GEMINI_3_8_FLASH = "gemini-3.8-flash"
const val MODEL_GEMINI_3_7_FLASH = "gemini-3.7-flash"
const val DEFAULT_ANALYSIS_MODEL = MODEL_GEMINI_3_7_FLASH
const val MODEL_GEMINI_3_6_FLASH = "gemini-3.6-flash"
const val MODEL_GEMINI_3_5_FLASH_LITE = "gemini-3.5-flash-lite"
const val MODEL_GROK_4_5 = "grok-4.5"
