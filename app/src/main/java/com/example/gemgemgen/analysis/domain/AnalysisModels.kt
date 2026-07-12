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
    val warnings: List<String> = emptyList()
)

data class AnalysisDirection(
    val id: String,
    val title: String,
    val description: String,
    val hint: String
)

object AnalysisDummyDirections {
    val values = listOf(
        AnalysisDirection(
            id = "balanced",
            title = "균형 유지",
            description = "원문의 분위기와 물리 조건을 가장 안정적으로 유지합니다.",
            hint = "keep the original mood, visual balance, and realistic context"
        ),
        AnalysisDirection(
            id = "soft-detail",
            title = "부드러운 디테일",
            description = "과하지 않은 질감, 색감, 형태 차이를 중심으로 변주합니다.",
            hint = "soft texture, restrained variation, natural color harmony"
        ),
        AnalysisDirection(
            id = "clear-structure",
            title = "구조 선명화",
            description = "형태와 배치가 한눈에 읽히도록 명확한 후보를 만듭니다.",
            hint = "clear silhouette, readable structure, concise visual details"
        ),
        AnalysisDirection(
            id = "fresh-variation",
            title = "새로운 변주",
            description = "원문을 깨지 않는 범위에서 더 다양한 미감을 시도합니다.",
            hint = "fresh but context-aware alternatives, diverse visual styling"
        )
    )
}

const val DEFAULT_ANALYSIS_MODEL = "gemini-3.5-flash"
const val MODEL_GEMINI_3_5_FLASH = "gemini-3.5-flash"
const val MODEL_GEMINI_3_1_FLASH_LITE = "gemini-3.1-flash-lite"
const val MODEL_GROK_4_5 = "grok-4.5"

