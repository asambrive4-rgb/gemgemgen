package com.example.gemgemgen.analysis.domain

enum class AnalysisStatus {
    IDLE,
    ANALYZING,
    GENERATING,
    SUCCESS,
    ERROR
}

enum class AnalysisTargetSource {
    AUTO,
    MANUAL
}

data class AnalysisTargetSegment(
    val text: String,
    val startIndex: Int,
    val endIndex: Int,
    val source: AnalysisTargetSource,
    val category: AnalysisCategory,
    val confidence: Double = 1.0,
    val reason: String = ""
) {
    val isValid: Boolean
        get() = text.isNotBlank() && startIndex >= 0 && endIndex > startIndex
}

data class AnalysisDetectedSegment(
    val exactText: String,
    val startIndex: Int,
    val endIndex: Int,
    val confidence: Double,
    val reason: String
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
    val warnings: List<String> = emptyList()
)

data class AnalysisDirection(
    val id: String,
    val title: String,
    val description: String,
    val hint: String
)

object AnalysisDummyDirections {
    // 관련 페어 묶기: 분량끼리 → 계열끼리 → 야한 엣지 → 장소 맞춤 의상
    val values = listOf(
        AnalysisDirection(
            id = "length-only",
            title = "분량 추가",
            description = "계열·야함·디테일 성격을 특별히 바꾸지 않고, 설명 분량과 풍성함만 늘립니다.",
            hint = "Do not change genre, erotic tone, or core concept. Mainly expand length and richness: add more descriptive clauses about the same subject while keeping the overall character of the original segment."
        ),
        AnalysisDirection(
            id = "detail-and-length",
            title = "디테일+분량",
            description = "같은 콘셉트를 유지한 채 질감·소재·색·형태 등 디자인 디테일을 촘촘히 넣고 문장도 더 길게 씁니다.",
            hint = "Keep the same concept and composition. Enrich design details (texture, material, color nuance, form, ornament, fabric folds, sheen) and write a longer, denser Korean fragment—not a short summary."
        ),
        AnalysisDirection(
            id = "same-lineage-other-kind",
            title = "같은 계열·다른 종류",
            description = "선택한 카테고리의 큰 축(계열)은 유지하고, 그 안에서 다른 종류·옵션으로만 바꿉니다.",
            hint = "Stay within the same lineage/family for the selected category (style genre, mood family, or item type). Swap only to a different kind/option inside that family—not a totally different genre."
        ),
        AnalysisDirection(
            id = "other-lineage",
            title = "다른 계열 변주",
            description = "선택한 카테고리 기준으로 계열·장르 축 자체를 다른 패밀리로 옮깁니다.",
            hint = "For the selected category, shift the lineage/genre axis itself to a different family (e.g. romantic soft → dark fantasy, casual everyday → high fashion). Bigger jump than same-lineage options; still replace only the target segment."
        ),
        AnalysisDirection(
            id = "erotic-edge",
            title = "야한 엣지",
            description = "장면 뼈대는 유지하되, 설정·인물 속성·포즈·의상 등을 바꿔 그 상황이 더 야해지도록 엣지를 넣습니다.",
            hint = "Keep the scene skeleton, but add erotic edge by changing what would make THIS situation hotter: relationship/setup (e.g. age gap, taboo power dynamic), character attributes (e.g. ethnicity, muscular build), pose intimacy/dominance, or clothing exposure/style. Prefer situational spice over vague adjectives alone."
        ),
        AnalysisDirection(
            id = "outfit-fits-location",
            title = "장소에 어울리는 의상",
            description = "원문의 장소·배경·상황에 자연스럽게 맞는 의상으로 바꿉니다. 장소 자체보다 그 장소에 어울리는 옷차림에 초점을 둡니다.",
            hint = "Rewrite the target toward clothing that naturally fits the location, background, and situation in the original prompt (e.g. beachwear at the beach, formal attire at a gala, outdoor gear on a mountain trail). Prioritize outfit-place coherence over random fashion changes; do not invent a new location unless the segment itself is the outfit description."
        )
    )
}

const val DEFAULT_ANALYSIS_MODEL = "gemini-3.5-flash"
const val MODEL_GEMINI_3_5_FLASH = "gemini-3.5-flash"
const val MODEL_GEMINI_3_1_FLASH_LITE = "gemini-3.1-flash-lite"
const val MODEL_GROK_4_5 = "grok-4.5"

