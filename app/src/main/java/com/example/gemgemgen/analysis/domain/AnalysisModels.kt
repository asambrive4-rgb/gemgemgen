// 역할: AI 분석 요청 상태, 결과 데이터, 세그먼트 모델 및 생성 개수/쿼터 정책을 정의합니다.
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
    val confidence: Double = 1.0,
    /** 연쇄 보완을 포함한 치환 범위 안에서 실제 편집이 허용된 구간들. */
    val editableRanges: List<AnalysisSourceRange> = emptyList()
) {
    val isValid: Boolean
        get() = text.isNotBlank() && startIndex >= 0 && endIndex > startIndex
}

data class AnalysisDetectedSegment(
    val exactText: String,
    val startIndex: Int,
    val endIndex: Int,
    val confidence: Double
) {
    val isValid: Boolean
        get() = exactText.isNotBlank() && startIndex >= 0 && endIndex > startIndex
}

data class AnalysisVisualContext(
    val viewpoint: String = "알 수 없음",
    val distance: String = "알 수 없음",
    val visibleScope: String = "알 수 없음",
    val cameraAngle: String = "알 수 없음",
    val visibleElements: List<String> = emptyList(),
    val hiddenOrUnclearElements: List<String> = emptyList()
)

data class AnalysisSpatialLayout(
    val subjectPlacement: String = "",
    val foreground: List<String> = emptyList(),
    val midground: List<String> = emptyList(),
    val background: List<String> = emptyList(),
    val leftSide: List<String> = emptyList(),
    val center: List<String> = emptyList(),
    val rightSide: List<String> = emptyList(),
    val above: List<String> = emptyList(),
    val below: List<String> = emptyList(),
    val behindSubject: List<String> = emptyList(),
    val besideSubject: List<String> = emptyList(),
    val fixedAnchors: List<String> = emptyList(),
    val mutableZones: List<String> = emptyList()
)

data class AnalysisCategoryConstraints(
    val allowed: List<String> = emptyList(),
    val avoid: List<String> = emptyList()
)

data class AnalysisReport(
    val targetSegment: AnalysisDetectedSegment? = null,
    val visualContext: AnalysisVisualContext = AnalysisVisualContext(),
    val spatialLayout: AnalysisSpatialLayout = AnalysisSpatialLayout(),
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

object AnalysisDummyDirections {
    // 관련 페어 묶기: 분량끼리 → 계열끼리 → 야한 엣지 → 장소 맞춤 의상
    val values = listOf(
        AnalysisDirection(
            id = "length-only",
            title = "분량 추가",
            hint = "Do not change genre, erotic tone, or core concept. Mainly expand length and richness: add more descriptive clauses about the same subject while keeping the overall character of the original segment."
        ),
        AnalysisDirection(
            id = "detail-and-length",
            title = "디테일+분량",
            hint = "Keep the same concept and composition. Enrich design details (texture, material, color nuance, form, ornament, fabric folds, sheen) and write a longer, denser Korean fragment—not a short summary."
        ),
        AnalysisDirection(
            id = "same-lineage-other-kind",
            title = "같은 계열·다른 종류",
            hint = "Stay within the same lineage/family for the selected category (style genre, mood family, or item type). Swap only to a different kind/option inside that family—not a totally different genre."
        ),
        AnalysisDirection(
            id = "other-lineage",
            title = "다른 계열 변주",
            hint = "For the selected category, shift the lineage/genre axis itself to a different family (e.g. romantic soft → dark fantasy, casual everyday → high fashion). Bigger jump than same-lineage options; still replace only the target segment."
        ),
        AnalysisDirection(
            id = "erotic-edge",
            title = "야한 엣지",
            hint = "Keep the scene skeleton, but add erotic edge by changing what would make THIS situation hotter: relationship/setup (e.g. age gap, taboo power dynamic), character attributes (e.g. ethnicity, muscular build), pose intimacy/dominance, or clothing exposure/style. Prefer situational spice over vague adjectives alone."
        ),
        AnalysisDirection(
            id = "outfit-fits-location",
            title = "장소에 어울리는 의상",
            hint = "Rewrite the target toward clothing that naturally fits the location, background, and situation in the original prompt (e.g. beachwear at the beach, formal attire at a gala, outdoor gear on a mountain trail). Prioritize outfit-place coherence over random fashion changes; do not invent a new location unless the segment itself is the outfit description."
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

