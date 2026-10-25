// 역할: 프롬프트 분석 및 개선에 사용할 수 있는 카테고리 종류와 변주 방향에 유연하게 대응하는 카테고리별 규칙을 정의합니다.
package com.example.gemgemgen.analysis.domain

enum class AnalysisCategory(val label: String) {
    FREE_EDIT("분석 수정"),
    COMPOSITION("구도"),
    CAMERA_TEXTURE("카메라 질감"),
    WOMEN_CLOTHING("여성 의상"),
    MEN_CLOTHING("남성 의상"),
    MEN_APPEARANCE("남성 외모"),
    LOCATION("장소"),
    WOMEN_POSE("여성 자세"),
    MEN_POSE("남성 자세"),
    WOMEN_EXPRESSION("여성 표정"),
    WOMEN_HAIRSTYLE("여성 헤어스타일");

    /**
     * 저장 기본 파일명: 라벨에서 공백을 제거해 토큰 호환 이름(`__이름__`)을 만든다.
     * 예: "여성 의상" → "여성의상.txt"
     */
    fun defaultWildcardSaveFileName(): String =
        "${label.filterNot { it.isWhitespace() }}.txt"
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
        AnalysisCategory.FREE_EDIT to AnalysisCategoryRule(
            goal = "원문 프롬프트의 의도와 사용자 변경 지시를 정밀 분석하여, 실패 교정·조건 보완·의도적 방향 변경 및 필수 연쇄 보완을 원본 보존형 편집 목록으로 수행",
            required = """
1. 추상적 표현을 관찰 가능한 시각적/물리적 조건으로 변환(요청·장면에 필요한 효과만 선택).
2. 필수 연쇄 보완: 새로 드러나거나 사라지는 신체·착장·신발·지지면·배경 구조선·조명/그림자 변화를 함께 정의하고, 충돌하는 기존·금지·강조 문장을 교정.
3. 인물 묘사는 관찰 가능한 외양(표정·시선·머리 기울기, 척추·어깨·골반·체중 지지, 손·팔·다리 위치·접촉, 의상 겹침·목선·소매·밑단·핏·주름)을 우선하고, 추상적 감정은 의미 손실 방지 시에만 약한 인상으로 1회 보조.
4. 원본 보존형 정밀 치환: 수정·제거·추가만 편집 목록으로 작성하고 비수정 구간·줄바꿈은 보존.
            """.trimIndent(),
            avoid = """
1. 추상적 분위기/감정 수식어('더 자연스럽게', '역동적으로', '영화처럼', '긴장감 있게')나 카메라/렌즈 명칭('하이 앵글', '광각', '어안 렌즈') 단독 사용.
2. 요청과 무관한 외모·의상·소품·서사 임의 변경, 충돌 조건·신규 노출 영역 미정의 방치, 불필요한 윤문·문체 개변.
            """.trimIndent(),
            variables = "방향성, 앵글·시점 높이, 촬영 거리, 화면 점유율, 전경/후경 크기비, 조명·그림자, 의상 핏, 표정·동작",
            output = "원본 언어·문체를 따르는 시각 묘사 편집 목록(비수정 원문 번역·윤문 금지)"
        ),
        AnalysisCategory.COMPOSITION to AnalysisCategoryRule(
            goal = "카메라 높이·기울기·거리, 프레이밍, 피사체 점유율, 전경·중경·후경 분리, 원근 왜곡 및 여백을 화면 결과 위주로 제어하는 구도 프롬프트 조각 생성",
            required = """
1. 프레이밍·범위: 프레임 비율, 크롭 범위(전신·무릎·허리·가슴·얼굴), 가장자리 잘림 위치, 여백 비율, 피사체 위치·점유율.
2. 카메라 위치·앵글: 눈높이 기준 카메라 높이, 상하 기울기, 촬영 방향(정면·측면·사선), 수직선·소실점.
3. 원근·렌즈 화면 결과(요청에 필요한 효과만 선택):
   - 하이 앵글: 눈높이 위에서 아래로 기울임, 머리/상체가 상대적으로 크고 하체는 아래로 짧게 압축, 바닥·사물 윗면 노출.
   - 로우 앵글: 눈높이 아래에서 위로 기울임, 하체/발이 크게 보이고 상체/머리는 위로 축소, 수직 길이 과장, 천장·높은 구조물 노출, 수직선 상단 수렴.
   - 광각/초광각: 근접 촬영, 전경 확대·후경 급격 축소, 가까운 부위 돌출, 가장자리 직선 외곽 휨.
   - 어안 렌즈: 중심 볼록 돌출, 가장자리 활처럼 휘는 반구형 왜곡.
   - 망원/원근 압축: 전경·후경 크기 차 축소, 원거리 대상 밀착감, 깊이 압축, 왜곡 최소화.
4. 필수 연쇄 보완: 새로 드러나는 하체/신발/바닥 정의, 여백 축소 시 경계 겹침 정의, 잘려 나간 요소 노출 요구 문장 제거.
            """.trimIndent(),
            avoid = """
1. '하이 앵글', '로우 앵글', '광각 렌즈', '아이 레벨', '역동적 구도' 등 명칭만 단독 작성하거나 거리·높이·기울기·원근 크기 변화를 생략.
2. 새로 노출되는 신체·의상·지지면 미정의 방치, 구도와 무관한 외모·의상·소품 임의 변경.
            """.trimIndent(),
            variables = "카메라 높이(바닥~부감), 기울기, 촬영 거리(익스트림 클로즈업~롱샷), 화면 점유율, 전경 배치, 소실점, 여백 비율",
            output = "카메라 위치·앵글·크기·원근 왜곡·프레이밍 경계가 시각적 결과로 드러난 구문형 프롬프트"
        ),
        AnalysisCategory.CAMERA_TEXTURE to AnalysisCategoryRule(
            goal = "광학 렌즈 특성, 센서/필름 입자 질감, 조명 콘트라스트, 피사계 심도, 색온도·톤 중심의 광학/질감 변주 프롬프트 생성",
            required = """
1. 조명·명암: 주광원 방향, 명암 대비 강도, 그림자 경계(소프트/하드), 하이라이트 감쇄.
2. 광학·심도: 조리개 심도, 보케 또는 팬포커스, 피사체·배경 분리감.
3. 필름/센서 텍스처: 필름 그레인, 센서 노이즈, 윤곽 선예도 또는 소프트 블러.
4. 색감·수차: 비네팅, 색수차, 플레어, 색온도 편향(골든아워, 차가운 푸른빛, 빈티지 톤).
5. 연출 예시(질감만 요청 시 구도·여백·장소 유지): 패션 화보(정돈된 조명, 선명한 텍스처, 통제된 팔레트), 영화적 연출(명확한 광원 방향, 극적 대비, 깊은 계조), 빈티지/아날로그(소프트 콘트라스트, 필름 그레인, 부드러운 계조).
            """.trimIndent(),
            avoid = """
1. 근거 없는 카메라·렌즈·필름 제품명 및 초점거리·조리개·거리 수치 날조.
2. '영화 같은 분위기', '빈티지한 느낌', '화보 스타일' 등 추상 수식어 단독 나열, 무관한 행동·표정·장소 변경, 기존 광원 조건과 충돌 방치.
            """.trimIndent(),
            variables = "필름 그레인 밀도, 조명 대비, 광원 색온도, 피사계 심도, 플레어·비네팅, 섀도우 계조",
            output = "조명 방향·명암 대비·입자감·심도·색감이 물리적으로 관찰 가능한 광학/질감 프롬프트"
        ),
        AnalysisCategory.WOMEN_CLOTHING to AnalysisCategoryRule(
            goal = "여성 의상의 아이템 구성, 디자인, 디테일을 묘사하는 프롬프트 생성",
            required = "핵심 의상 아이템, 구조, 재질, 실루엣 묘사",
            avoid = "의상과 무관한 배경, 조명, 장소, 카메라 설정 임의 변경",
            variables = "아이템 종류, 색상, 핏, 프린팅, 장식, 절개, 소재, 디테일",
            output = "원문 호흡 또는 선택된 변주 방향(분량·디테일·계열)에 맞춘 여성 의상 프롬프트"
        ),
        AnalysisCategory.MEN_CLOTHING to AnalysisCategoryRule(
            goal = "남성 의상의 아이템 구성, 디자인, 디테일을 묘사하는 프롬프트 생성",
            required = "핵심 의상 아이템, 구조, 테일러링, 재질, 핏 묘사",
            avoid = "의상과 무관한 배경, 조명, 장소, 카메라/분위기, 외모/자세 임의 변경",
            variables = "아이템 종류, 색상, 실루엣, 재단, 소재, 절개, 장식, 레이어링, 마감",
            output = "원문 호흡 또는 선택된 변주 방향(분량·디테일·계열)에 맞춘 남성 의상 프롬프트"
        ),
        AnalysisCategory.MEN_APPEARANCE to AnalysisCategoryRule(
            goal = "남성 인물의 외형적 특징 전체 묘사",
            required = "이목구비, 체형, 골격, 피부 질감, 그루밍의 구체적 묘사",
            avoid = "과도한 배경이나 서사 연출",
            variables = "비율, 체격, 턱선, 눈매, 의상 핏, 자세, 피부톤",
            output = "원문 호흡 또는 선택된 변주 방향에 맞춘 남성 외모 프롬프트"
        ),
        AnalysisCategory.LOCATION to AnalysisCategoryRule(
            goal = "구체적인 장소와 공간 구성을 바로 쓸 수 있는 프롬프트 조각으로 묘사",
            required = "핵심 공간 구조, 주된 기물, 배치 중심의 시각적 묘사",
            avoid = "장소와 무관한 인물 외모·의상 임의 변경, 임의의 빈티지 과용",
            variables = "공간 종류, 건축/인테리어 양식, 표면 소재, 오브제 배치",
            output = "원문 호흡 또는 선택된 변주 방향에 맞춘 장소 프롬프트"
        ),
        AnalysisCategory.WOMEN_POSE to AnalysisCategoryRule(
            goal = "여성의 신체 자세와 동작을 직관적인 포즈 프롬프트로 묘사",
            required = "기본 자세, 시선/방향, 손·팔·다리 위치",
            avoid = "불필요한 관절·무게중심의 기계적 나열과 해부학 해설(지지·접촉에 필요한 경우만 명시)",
            variables = "자세 종류, 다리 위치, 상체 방향, 지지물 접촉",
            output = "원문 호흡 또는 선택된 변주 방향에 맞춘 포즈 지시문"
        ),
        AnalysisCategory.MEN_POSE to AnalysisCategoryRule(
            goal = "남성의 신체 자세와 부분별 동작 정밀 묘사",
            required = "자세, 사지 위치, 관절, 무게중심, 시선, 지지물 접촉",
            avoid = "외모 평가, 감성 서사, 상세 배경·의상 묘사",
            variables = "신체 각도, 무게 이동, 긴장감, 팔/다리 배치, 시선",
            output = "원문 호흡 또는 선택된 변주 방향에 맞춘 남성 포즈 프롬프트"
        ),
        AnalysisCategory.WOMEN_EXPRESSION to AnalysisCategoryRule(
            goal = "여성의 얼굴 표정과 시선을 직관적인 프롬프트로 묘사",
            required = "핵심 표정, 시선 방향",
            avoid = "불필요한 볼·턱·눈꺼풀의 기계적 나열과 과도한 감정 분석",
            variables = "표정 종류, 입꼬리, 눈매, 미소 정도, 시선 처리",
            output = "원문 호흡 또는 선택된 변주 방향에 맞춘 얼굴 표정 프롬프트"
        ),
        AnalysisCategory.WOMEN_HAIRSTYLE to AnalysisCategoryRule(
            goal = "여성 헤어스타일 및 모발 묘사 프롬프트 생성",
            required = "헤어스타일명, 앞머리 유무, 기장, 머릿결·텍스처",
            avoid = "추상적 인상, 무관한 메이크업·안면 특징이나 의상 임의 변경",
            variables = "헤어스타일 종류, 기장, 파마/웨이브 여부, 컬러·톤, 앞머리, 가르마, 장식",
            output = "원문 호흡 또는 선택된 변주 방향에 맞춘 헤어스타일 지시문"
        )
    )

    fun ruleFor(category: AnalysisCategory): AnalysisCategoryRule {
        return rules.getValue(category)
    }
}
