# GemGemGen Design System & Performance Master Specification

> **적용 원칙 (Hierarchy of Truth):**
> 1. 특정 화면에 개별 예외가 필요한 경우 `design-system/gemgemgen/pages/[화면명].md`를 우선 적용합니다.
> 2. 페이지별 파일이 없는 경우, 이 `MASTER.md`에 명시된 디자인, 터치, 성능 및 아키텍처 규칙을 엄격히 준수합니다.
> 3. **[사용자 명시적 핵심 원칙] 터치 타겟 규격은 현재 프로젝트에 최적화된 크기(36~44dp)를 유지합니다.**

---

**프로젝트:** GemGemGen (Android Native / Jetpack Compose)  
**기반 스택:** `jetpack-compose`, Material 3, Kotlin Coroutines & Flow  
**디자인 컨셉:** 소프트 3D 뉴모피즘 (Clay & Pebble)  
**최신 갱신일:** 2026-09-10  

---

## 1. 비주얼 조형 규격: 소프트 3D 뉴모피즘 (Clay & Pebble)

평면적(Flat) 네모 상자를 지양하고, **만지고 싶은 조약돌 형태(Pebble Shape)**와 **쫀득한 입체감(Soft 3D / Clay Extrusion)**을 유지합니다.  
모든 화면은 하드코딩 대신 공통 컴포넌트 라이브러리인 [`NeumorphicElements.kt`](file:///c:/Users/joajo/AndroidStudioProjects/gemgemgen/app/src/main/java/com/example/gemgemgen/ui/theme/NeumorphicElements.kt)를 사용합니다.

### 1) 3D 조약돌 카드 (`NeuCard`)
- **역할:** 주요 섹션 컨테이너, 프롬프트 카드, 설정 그룹
- **라운딩:** `22.dp` (`RoundedCornerShape(22.dp)`)
- **입체감:** 기본 `elevation = 6.dp`
- **그림자:** `AppTheme.colors.shadowDark` 기반 양방향 그림자 (ambient 0.5f, spot 0.4f) + 1dp `cardBorder`

### 2) 3D 음각 인셋 베드 (`NeuInsetBed`)
- **역할:** 텍스트 입력창 뒷배경, 상단 탭 트랙, 유틸리티 액션 아일랜드
- **라운딩:** `14~16.dp`
- **효과:** 바닥면이 오목하게 파인 음각 질감(`AppTheme.colors.insetBed` + 1dp `insetBorder`)으로 입력 및 트랙 위계 분리

### 3) 쫀득한 조약돌 칩 (`NeuPillChip`)
- **역할:** 타겟 앱 선택(Gemini/ChatGPT), AI 모델 선택, 필터 칩
- **라운딩:** `14.dp` (패딩: 가로 12dp, 세로 6dp)
- **피드백:** 선택 시 `AppTheme.colors.primary` 배경과 은은한 글로우 그림자(elevation 4.dp) 발광

### 4) 쫀득한 클레이 버튼 (`NeuButton`)
- **역할:** 시작/중지, 생성, 복사, 저장 등 주요 터치 액션
- **라운딩:** `16.dp`
- **입체감:** Primary 버튼(elevation 6.dp), Secondary 서브 버튼(elevation 3.dp)
- **비활성 피드백:** 비활성화 시 `alpha = 0.5f` 및 `elevation = 0.dp`로 자연스럽게 바닥으로 가라앉는 시각적 피드백 제공

---

## 2. 3가지 선택형 테마 팔레트

[`AppThemePalette.kt`](file:///c:/Users/joajo/AndroidStudioProjects/gemgemgen/app/src/main/java/com/example/gemgemgen/ui/theme/AppThemePalette.kt)에 정의된 3가지 테마 팔레트를 지원하며, **기본값은 팔레트 2(노르딕 세이지)**입니다. 모든 색상은 `AppTheme.colors` 토큰을 통해 참조합니다.

| UI 역할 토큰 | 🍑 팔레트 1 (웜 코랄 크림) | 🌿 팔레트 2 (노르딕 세이지) [기본] | ⚡ 팔레트 3 (퓨어 인디고) |
| :--- | :--- | :--- | :--- |
| `background` (캔버스) | `#F6F2EA` (우유빛 크림) | `#EBF0E9` (소프트 세이지) | `#EEF4FA` (아이시 쿨 화이트) |
| `card` (3D 조약돌) | `#FAF6EE` (오트밀 크림) | `#F2F6F0` (말차 크림) | `#F8FAFD` (퓨어 스노우) |
| `insetBed` (음각 트랙) | `#F0EAE0` | `#E2EBE0` | `#E6EEF8` |
| `inputBackground` (입력창) | `#FFFFFF` (순백) | `#FFFFFF` (순백) | `#FFFFFF` (순백) |
| `inputBorder` (기본 테두리) | `#C4B6A3` (웜 토프) | `#9AB197` (세이지 올리브) | `#9EB7CF` (아이스 블루) |
| `primary` (메인 포인트) | `#D9532F` (차분한 웜 테라코타) | `#1B7A5A` (깊은 딥 세이지) | `#2E5EB8` (단정한 미드나잇 인디고) |
| `onPrimary` (버튼 텍스트) | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` |
| `textPrimary` (제목/본문) | `#2E2419` | `#14261C` | `#0B1A30` |
| `textSecondary` (보조 설명) | `#7A6F62` | `#52665B` | `#587494` |
| `shadowDark` (양각 하단) | `#DCD4C7` (alpha 0.6) | `#C5D3C2` (alpha 0.6) | `#CCD9E8` (alpha 0.65) |

> **명도 대비 규칙 (WCAG AA):**  
> 모든 텍스트(`textPrimary`, `textSecondary`, `onPrimary`)는 해당 배경색과 최소 **4.5:1 이상**의 명도 대비를 유지해야 합니다.

---

## 3. [핵심 제약] 터치 타겟 규격 (현재 앱 규격 엄격 유지)

안드로이드 일반 가이드(48dp)를 획일적으로 적용하여 화면 배치가 깨지지 않도록, **현재 앱에서 정립된 모바일 최적화 규격을 엄격히 유지**합니다.

| UI 요소 | 시각적 높이 / 크기 | 외부 터치 히트박스 보장 규격 | 간격 리듬 |
| :--- | :--- | :--- | :--- |
| **소형 조약돌 칩** (`NeuPillChip`) | 높이 26~30dp, 라운딩 14~15dp | **최소 36~44dp** 확보 (Box 패딩 또는 히트박스 영역) | 가로 간격 6~8dp |
| **스텝퍼 조절 버튼** (+ / -) | 크기 28~32dp | **최소 36~40dp** 클릭 영역 유지 | 인접 요소 간격 4~8dp |
| **유틸리티 액션 버튼** | 높이 36dp, 라운딩 12dp | **36~44dp** 유지 | 버튼 간격 8dp |
| **메인 액션 바 버튼** (실행/생성) | 높이 48~52dp, 라운딩 16dp | **48~52dp** | 하단 여백 12~16dp |
| **화면 레이아웃 패딩** | - | 외곽 패딩 16dp, 섹션 간격 12dp, 아이템 8dp/4dp | 8dp 그리드 리듬 |

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

- [ ] **조형 일관성:** 모든 카드는 `NeuCard`, 입력 베드는 `NeuInsetBed`, 칩은 `NeuPillChip`을 사용했는가?
- [ ] **색상 토큰:** 하드코딩된 Hex 컬러를 배제하고 `AppTheme.colors` 토큰을 참조했는가?
- [ ] **터치 타겟 규격:** 소형 칩/스텝퍼는 기존 36~44dp 터치 영역을 유지하고, 메인 버튼은 48dp 이상인가?
- [ ] **터치 피드백:** 클릭 시 시각적 반응(리플, 눌림 또는 활성 글로우)이 즉각 전달되는가?
- [ ] **가독성 (명도 대비):** 3개 테마 팔레트 모두에서 텍스트와 배경의 명도 대비가 4.5:1 이상인가?
- [ ] **접근성 (A11y):** 텍스트 없는 아이콘 버튼에 명확한 `contentDescription`이 부여되었는가?
- [ ] **깜빡임/화면 유지:** 다이얼로그나 탭 전환 시 화면 깜빡임이 없는가?
- [ ] **렌더링 최적화:** 불필요한 레이아웃 중첩이 없으며 `LazyColumn` 항목에 `key`가 지정되었는가?
