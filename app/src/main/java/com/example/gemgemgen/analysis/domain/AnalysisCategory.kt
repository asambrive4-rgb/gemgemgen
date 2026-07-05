package com.example.gemgemgen.analysis.domain

enum class AnalysisCategory(val label: String) {
    WOMEN_CLOTHING("여성 의상"),
    MEN_CLOTHING("남성 의상"),
    MEN_APPEARANCE("남성 외모"),
    LOCATION("장소"),
    WOMEN_POSE("여성 자세"),
    MEN_POSE("남성 자세"),
    WOMEN_EXPRESSION("여성 표정"),
    WOMEN_HAIRSTYLE("여성 헤어스타일"),
    WAKA("와카")
}

data class AnalysisCategoryRule(
    val goal: String,
    val required: String,
    val avoid: String,
    val variables: String,
    val output: String = ""
)

object AnalysisCategoryRules {
    private val rules = mapOf(
        AnalysisCategory.WOMEN_CLOTHING to AnalysisCategoryRule(
            goal = "짧고 실용적인 여성 의상 프롬프트 생성",
            required = "핵심 의상 묘사 (구조, 재질, 실루엣 세 가지만 집중)",
            avoid = "추상적 인상, 산문형/설명문 톤의 긴 서술형 문장, 과도한 디테일 열거",
            variables = "절개, 마감, 장식, 디테일 등은 필수 요소와 결합하여 다양성만 주되 길어지지 않게 함",
            output = "설명문이 아닌 명사구 위주의 짧은 프롬프트형"
        ),
        AnalysisCategory.MEN_CLOTHING to AnalysisCategoryRule(
            goal = "남성 의상의 디자인과 디테일 집중 묘사",
            required = "의상 중심. 구조, 테일러링, 재질, 핏에 기반한 물리적 묘사",
            avoid = "추상적 인상, 배경, 조명, 장소, 카메라/분위기 연출, 인물 외모/자세",
            variables = "실루엣, 재단, 소재, 절개, 장식, 레이어링, 마감"
        ),
        AnalysisCategory.MEN_APPEARANCE to AnalysisCategoryRule(
            goal = "남성 인물의 외형적 특징 전체 묘사",
            required = "얼굴 이목구비, 체형 구조, 골격, 피부 질감, 그루밍 상태 등 구체적 묘사",
            avoid = "과도한 배경이나 장면 서사 연출",
            variables = "비율, 체격, 턱선, 눈매, 의상 핏, 자세, 피부톤"
        ),
        AnalysisCategory.LOCATION to AnalysisCategoryRule(
            goal = "구체적인 장소를 바로 쓸 수 있는 프롬프트 조각 구문 형태로 묘사",
            required = "핵심 공간 구조, 주된 기물, 배치 중심의 짧은 나열",
            avoid = "산문형/서술형 문장, 설명적 연결어, 임의의 빈티지 과용",
            variables = "건축/인테리어 양식, 표면 소재, 오브제 배치를 간결하게 변주",
            output = "짧고 간결한 구문 형태, 쉼표 구분 단어 위주"
        ),
        AnalysisCategory.WOMEN_POSE to AnalysisCategoryRule(
            goal = "여성의 신체 자세를 짧은 포즈 지시문 수준으로 단순화하여 묘사",
            required = "기본 자세, 시선 또는 보는 방향, 손/팔의 대략적 위치",
            avoid = "관절 꺾임, 무게중심, 체중 분산, 해부학적 해설, 길고 자세한 설명문",
            variables = "다리 위치, 상체 방향, 지지물 접촉 등의 변형을 간결하게 추가",
            output = "짧고 직관적인 포즈 지시문"
        ),
        AnalysisCategory.MEN_POSE to AnalysisCategoryRule(
            goal = "남성의 신체 자세와 부분별 동작 정밀 묘사",
            required = "자세, 사지의 위치, 관절, 무게중심, 시선, 지지물 접촉 여부",
            avoid = "외모 평가, 감성적 서사, 상세 배경 묘사, 의상 중심 묘사",
            variables = "신체 각도, 무게 이동, 긴장감, 팔/다리 배치, 시선",
            output = "\"남성은 [자세]로 [배치/상태]이다.\""
        ),
        AnalysisCategory.WOMEN_EXPRESSION to AnalysisCategoryRule(
            goal = "여성의 얼굴 표정을 짧고 직관적인 구문으로 묘사",
            required = "핵심 표정, 시선 방향",
            avoid = "볼, 턱 근육, 눈꺼풀 긴장, 얼굴 미세 근육 분석, 감정 분석",
            variables = "입꼬리, 눈매, 미소 정도를 가볍게 변주",
            output = "간단한 얼굴 표현 프롬프트"
        ),
        AnalysisCategory.WOMEN_HAIRSTYLE to AnalysisCategoryRule(
            goal = "여성 헤어스타일 및 모발 묘사 프롬프트 생성",
            required = "헤어스타일명, 앞머리 유무, 기장감, 머릿결 및 텍스처 중심 묘사",
            avoid = "추상적 인상, 무관한 메이크업이나 안면 특징 묘사, 길고 서술형 문장",
            variables = "머리 기장, 파마 여부, 컬러 및 톤, 앞머리 스타일, 가르마 등을 간결하게 변형",
            output = "짧고 직관적인 헤어스타일 지시문"
        ),
        AnalysisCategory.WAKA to AnalysisCategoryRule(
            goal = "와일드카드 텍스트 치환용 항목",
            required = "",
            avoid = "",
            variables = ""
        )
    )

    fun ruleFor(category: AnalysisCategory): AnalysisCategoryRule {
        return rules.getValue(category)
    }
}
