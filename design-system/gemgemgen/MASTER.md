# GemGemGen Design System & Performance Master Specification

> **적용 원칙 (Hierarchy of Truth):**
> 1. 특정 화면에 개별 예외가 필요한 경우 `design-system/gemgemgen/pages/[화면명].md`를 우선 적용합니다.
> 2. 페이지별 파일이 없는 경우, 이 `MASTER.md`에 명시된 디자인, 터치, 성능 및 아키텍처 규칙을 엄격히 준수합니다.
> 3. **[사용자 명시적 최종 목표 및 표준 레퍼런스 화면] GemGemGen의 최종 디자인 컨셉은 토스 스타일(Toss-Like Clean & Borderless)이며, 현재의 '자동화(Automation) 탭'([AutomationScreen.kt](file:///c:/Users/joajo/AndroidStudioProjects/gemgemgen/app/src/main/java/com/example/gemgemgen/automation/ui/AutomationScreen.kt))이 앱 전체 화면이 지향하는 최우선 표준 레퍼런스 모델입니다.**
> 4. **[핵심 제약] 터치 타겟 규격은 현재 프로젝트에 최적화된 크기(소형 요소 36~44dp / 메인 액션 48~52dp)를 엄격히 보장합니다.**

---

**프로젝트:** GemGemGen (Android Native / Jetpack Compose)  
**기반 스택:** `jetpack-compose`, Material 3, Kotlin Coroutines & Flow  
**디자인 컨셉:** 토스 스타일 무테두리 고대비 카드 (Toss-Like Clean & Borderless)  
**최신 갱신일:** 2026-10-30  

---

## 1. 비주얼 조형 규격: 토스 스타일 클린 & 보더리스 (Clean & Borderless)

과도한 외곽선(Border)과 무거운 3D 입체 그림자를 지양하고, **배경(Canvas)과 순백색 카드(Card)의 뚜렷한 명도 대비**, **부드러운 톤온톤 무테두리 인셋 베드(Inset Bed)**, **직관적인 3단계 알약 칩 및 솔리드 버튼 위계**를 통해 한눈에 들어오는 직관성과 부드러운 모바일 조작감을 제공합니다.  
모든 화면은 하드코딩 대신 공통 컴포넌트 라이브러리인 [`NeumorphicElements.kt`](file:///c:/Users/joajo/AndroidStudioProjects/gemgemgen/app/src/main/java/com/example/gemgemgen/ui/theme/NeumorphicElements.kt)를 사용합니다.

### 1) 무테두리 클린 카드 (`NeuCard`)
- **역할:** 주요 섹션 컨테이너, 프롬프트 카드, 결과 후보 카드, 설정 그룹
- **라운딩:** `20.dp` (`RoundedCornerShape(20.dp)`)
- **입체감:** 은은한 확산형 소프트 그림자 `elevation = 2.dp` (ambient 0.22f, spot 0.16f)
- **테두리 규칙:** **완전 무테두리 (`borderColor = Color.Transparent`, 0dp)**. 테두리 선 대신 캔버스 배경과 순백색 카드의 자연스러운 명도 차이로 깔끔한 분리감을 형성합니다.

### 2) 무테두리 톤온톤 인셋 베드 (`NeuInsetBed`)
- **역할:** 텍스트 입력창 뒷배경, 상단 탭 트랙, 보조 유틸리티 영역, 목록 베이스라인
- **라운딩:** `14.dp` (`RoundedCornerShape(14.dp)`)
- **효과:** 외곽선 없이 부드러운 회색/톤온톤 면(`AppTheme.colors.insetBed`) 색상 차이만으로 자연스러운 침전감과 단계 분리를 제공합니다.
- **텍스트필드 포커스 안내:** 텍스트 입력창은 평소 무테두리로 편안함을 주며, 포커스 시에만 얇은 포인트 컬러 테두리로 활성 상태를 안내합니다 ([`appTextFieldColors`](file:///c:/Users/joajo/AndroidStudioProjects/gemgemgen/app/src/main/java/com/example/gemgemgen/ui/theme/NeumorphicElements.kt#L209)).

### 3) 토스 3단계 무테두리 알약 칩 (`NeuPillChip`)
- **역할:** AI 모델 선택, 필터 칩, 타겟 앱 선택, 자동화 모드 토글, 카테고리 칩
- **라운딩:** `12.dp` (`RoundedCornerShape(12.dp)`) (패딩: 가로 12dp, 세로 7dp)
- **3단계 플랫 칩 위계 (토스 시그니처):**
  - **Tier 1 (Primary Solid):** 활성 선택 시 선명한 `AppTheme.colors.primary` 배경 + `onPrimary` 텍스트 (무테두리)
  - **Tier 2 (Primary Soft Tint):** `primary.copy(alpha = 0.14f)` 소프트 배경 + `primary` 텍스트 (보조 액션, 변주, 복사 등)
  - **Tier 3 (Neutral Gray Inset):** 비선택 시 `AppTheme.colors.insetBed` 배경 + `textSecondary` 텍스트 (무테두리 중립 상태)

### 4) 솔리드 & 소프트 클린 버튼 (`NeuButton`)
- **역할:** 시작/중지, 생성, 복사, 저장 등 주요 터치 액션
- **라운딩:** `14.dp` (`RoundedCornerShape(14.dp)`)
- **입체감 & 테두리:** 무테두리(0dp border). Primary 메인 CTA 버튼은 `elevation = 2.dp`, Secondary 보조 버튼은 `elevation = 0.dp`
- **비활성 피드백:** 비활성화 시 `alpha = 0.45f` 및 `elevation = 0.dp`

---

## 2. 4가지 선택형 테마 팔레트 (기본값: 팔레트 0 토스 시그니처)

[`AppThemePalette.kt`](file:///c:/Users/joajo/AndroidStudioProjects/gemgemgen/app/src/main/java/com/example/gemgemgen/ui/theme/AppThemePalette.kt)에 정의된 4가지 테마 팔레트를 지원하며, **기본값은 팔레트 0(토스 시그니처)**입니다. 모든 라이트 팔레트는 카드(`card`)를 순백색(`#FFFFFF`)으로 통일하여 캔버스 배경과의 고대비 단계 분리감을 극대화합니다. 모든 색상은 반드시 `AppTheme.colors` 토큰을 통해 참조합니다.

| UI 역할 토큰 | 💙 팔레트 0 (토스 시그니처) [기본값] | 🍑 팔레트 1 (웜 코랄 크림) | 🌿 팔레트 2 (노르딕 세이지) | ⚡ 팔레트 3 (퓨어 인디고) |
| :--- | :--- | :--- | :--- | :--- |
| `canvas` (배경) | `#E9EDF2` (쿨 그레이) | `#EAE3DC` (웜 스톤) | `#E2EBE4` (미스트 그린) | `#E4EAF3` (아이스 블루) |
| `card` (클린 카드) | `#FFFFFF` (순백) | `#FFFFFF` (순백) | `#FFFFFF` (순백) | `#FFFFFF` (순백) |
| `insetBed` (인셋 베드) | `#F2F5F8` | `#F4EFEA` | `#EDF3EF` | `#F0F4FA` |
| `primary` (메인 포인트) | `#3182F6` (토스 블루) | `#E55A34` (테라코타 코랄) | `#15805D` (딥 세이지) | `#4F46E5` (로얄 인디고) |
| `accent` (보조 강조) | `#00C471` (에메랄드 그린) | `#FF8A65` | `#2563EB` | `#EC4899` |
| `onPrimary` (버튼 텍스트) | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` |
| `textPrimary` (제목/본문) | `#191F28` | `#221C16` | `#14221B` | `#111827` |
| `textSecondary` (보조 설명) | `#8B95A1` | `#7E7266` | `#64786D` | `#64748B` |

### 다크 모드 3단계 표면 대비 (`crisp_toss`)
- **캔버스 (`canvas`):** `#0D1117` (딥 블랙 배경)
- **클린 카드 (`card`):** `#1C222D` (부유 카드 서피스)
- **인셋 베드 (`insetBed`):** `#131720` (카드 내부 입력창 및 중립 칩)
- **메인 포인트 (`primary`):** 각 팔레트 고유 포인트 컬러 유지

> **명도 대비 규칙 (WCAG AA):**  
> 모든 텍스트(`textPrimary`, `textSecondary`, `onPrimary`)는 해당 배경색과 최소 **4.5:1 이상**의 명도 대비를 유지해야 합니다.

---

## 3. [핵심 제약] 모바일 최적화 터치 타겟 규격

화면 배치의 간결함을 유지하면서도 모바일 터치 오작동을 원천 차단하기 위해, **현재 앱에서 정립된 모바일 최적화 규격을 엄격히 보장**합니다.

| UI 요소 | 시각적 높이 / 크기 | 외부 터치 히트박스 보장 규격 | 간격 리듬 |
| :--- | :--- | :--- | :--- |
| **소형 조약돌/알약 칩** (`NeuPillChip`) | 높이 26~30dp, 라운딩 12dp | **최소 36~44dp** 확보 (`defaultMinSize` 또는 Box 패딩) | 가로 간격 6~8dp |
| **스텝퍼 조절 버튼** (+ / −) | 크기 28~32dp, 라운딩 10dp | **최소 36~40dp** 클릭 영역 유지 | 인접 요소 간격 4~8dp |
| **유틸리티 액션 버튼 / 아이콘 버튼** | 높이 36dp, 라운딩 12dp | **최소 36~44dp** 유지 | 버튼 간격 8dp |
| **메인 액션 바 CTA 버튼** (시작/중지/생성/저장) | 높이 **48~52dp**, 라운딩 16dp | **48~52dp** 풀 터치 영역 | 하단 여백 12~16dp |
| **화면 레이아웃 패딩** | - | 외곽 패딩 12~16dp, 단계별 카드 간격 12dp, 카드 내부 16dp | 8dp 그리드 리듬 |

---

## 4. Jetpack Compose 성능 및 아키텍처 규칙 (`ui-ux-pro-max`)

`ui-ux-pro-max`의 `jetpack-compose` 스택 가이드라인을 기반으로 코드의 가독성과 성능을 보장합니다.

### 1) 순수 UI Composable (Pure UI)
- UI Composable 함수는 오직 상태(`UiState`)와 사용자 이벤트 람다(`onAction`)만 인자로 받습니다.
- Composable 내부에서 UseCase, Repository, DB를 직접 호출하지 않습니다.

### 2) 단방향 데이터 흐름 (UDF) & 불변성
- ViewModel은 불변의 `StateFlow<UiState>`를 노출하고, UI는 `collectAsStateWithLifecycle()`을 통해 수집합니다.
- 복수 개의 흩어진 `mutableStateOf` 대신 하나의 일원화된 `UiState` 데이터 클래스를 사용합니다.
- Composable 파라미터에는 변경 가능한 컬렉션(`MutableList`)을 넘기지 않고 `List` 또는 불변 컬렉션을 사용합니다.

### 3) 레이아웃 트리 평탄화 (Flat Layout)
- 단순 마진이나 정렬을 위해 불필요한 중첩 `Box`나 `Column`을 만들지 않고 `Modifier.padding()`과 `Arrangement`/`Alignment`를 활용합니다.
- `Box`는 뷰가 실제로 겹쳐야 하는 경우(Overlay/Z-order)에만 사용합니다.

### 4) 리스트 및 렌더링 최적화
- 동적 목록은 반드시 `LazyColumn`을 사용하며, 아이템마다 고유하고 안정적인 `key = { it.id }`를 제공하여 불필요한 재구성을 방지합니다.
- 무거운 연산이나 자주 재계산되는 값은 ViewModel에서 미리 계산하거나 `remember` / `derivedStateOf`로 감쌉니다.

---

## 5. 인터랙션 및 모바일 화면 규칙

1. **깜빡임 없는 UI (Flicker-Free):**
   - 다이얼로그는 개별 컴포넌트 내부가 아닌 화면 최상위 단일 호스트([Single Dialog Host](file:///c:/Users/joajo/AndroidStudioProjects/gemgemgen/docs/flicker-free-ui-guidelines.md))에서 관리합니다.
   - 탭 전환 시 화면 내용이 사라졌다 다시 뜨는 현상을 방지하기 위해 `Hold Screen` 패턴을 유지합니다.
2. **모바일 가상 키보드(IME) 대응:**
   - [mobile-interaction-ux-guidelines.md](file:///c:/Users/joajo/AndroidStudioProjects/gemgemgen/docs/mobile-interaction-ux-guidelines.md)에 따라 IME가 올라올 때 하단 고정 액션바가 화면을 가리지 않도록 적응형 패딩을 적용합니다.
   - 화면 빈 영역 터치 시 입력창 포커스가 자연스럽게 해제되도록 [`ClearFocusOnTapModifier.kt`](file:///c:/Users/joajo/AndroidStudioProjects/gemgemgen/app/src/main/java/com/example/gemgemgen/ui/ClearFocusOnTapModifier.kt)를 유지합니다.

---

## 6. Pre-Delivery 사전/사후 점검 체크리스트

모든 UI 및 디자인 관련 코드 작성 후, 다음 체크리스트를 점검하고 작업 결과에 명시합니다.

- [ ] **토스 스타일 일관성:** 카드는 `NeuCard`(20dp, 순백색, 2dp 소프트 그림자, 0dp 무테두리), 입력 베드는 `NeuInsetBed`(14dp, 무테두리)를 사용했는가?
- [ ] **칩 & 버튼 위계:** 칩은 `NeuPillChip`(12dp, 3단계 위계), 버튼은 `NeuButton`(14dp, 무테두리)을 사용했는가?
- [ ] **색상 토큰 일관성:** 하드코딩된 Hex 컬러나 `MaterialTheme.colorScheme`을 배제하고 `AppTheme.colors` 토큰을 참조했는가?
- [ ] **터치 타겟 규격 보장:** 소형 칩/아이콘 버튼은 최소 36~44dp 터치 히트박스를 확보하고, 메인 실행 버튼은 48~52dp인가?
- [ ] **터치 피드백:** 클릭 시 시각적 반응(리플, 소프트 틴트 전환)이 즉각 전달되는가?
- [ ] **가독성 (명도 대비):** 4개 테마 팔레트 모두에서 텍스트와 배경의 명도 대비가 4.5:1 이상인가?
- [ ] **접근성 (A11y):** 텍스트 없는 아이콘 버튼에 명확한 `contentDescription`이 부여되었고, 선택 요소에 `selected` 시맨틱이 있는가?
- [ ] **다이얼로그 일관성:** 공통 다이얼로그 프레임(`NeuModalDialogFrame`, `NeuConfigDialogContent`)을 사용하고 버튼에 `useNeuButtons = true`가 적용되었는가?
