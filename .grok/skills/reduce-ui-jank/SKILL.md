---
name: reduce-ui-jank
description: >
  gemgemgen 앱 UI 렉·스터터링·프레임 드랍을 코드 분석만으로 판단 → 기획(3안) →
  구현하는 루프. 실기기/adb 측정·실행 테스트로 성능을 증명하지 않는다.
  Compose 탭(자동화/분석/와일드카드), 긴 텍스트 편집, ViewModel StateFlow,
  플로팅 자동화 바 병목을 소스 근거로 우선 본다.
  Use when: "UI 렉", "스터터링", "프레임 드랍", "jank", "리컴포즈", "메인 스레드",
  "부드럽지 않", "성능 병목", "reduce-ui-jank", "/reduce-ui-jank",
  또는 화면이 무거워 코드 기준으로 개선하고 싶을 때.
metadata:
  short-description: "gemgemgen UI jank: code-only analyze → 3 options → fix"
---

# /reduce-ui-jank — gemgemgen UI 렉 줄이기 (코드 전용)

**gemgemgen** (`:app`, Jetpack Compose)에서 끊김·버벅임 후보를 **소스 코드만** 보고
분석 → 3안 기획 → 구현한다.

## 이 skill의 기본 모드 (필수)

| 한다 | 하지 않는다 |
|------|-------------|
| 코드 읽기·검색으로 병목 판단 | `adb` / `dumpsys gfxinfo` / 실기기 before·after |
| 근거 파일·줄 단위로 설명 | “측정했더니 p99가 …” 식 성능 숫자 주장 |
| 사용자가 고른 안으로 수정 | 성능 검증을 위해 앱 설치·수동 시나리오 실행 |
| (수정으로 깨진 경우만) 기존 유닛 테스트 맞춤 | jank 개선을 증명하려고 테스트/벤치 신설·필수 실행 |

**기본은 코드 판단 후 수정이다.**  
사용자가 명시적으로 “실측해줘 / 테스트 돌려줘”라고 할 때만 측정·실행을 한다.

이 skill은 “무조건 최소 변경”을 강요하지 않는다.  
**깊이 다른 3안**을 제시하고, 사용자가 고른 안으로 진행한다.

---

## 프로젝트 고정값

| 항목 | 값 |
|------|-----|
| applicationId / package | `com.example.gemgemgen` |
| 모듈 | `:app` only |
| 엔트리 | `ui/MainActivity` → `AndroidAutomationHost` → `AutomationApp` / 탭 |
| UI | Jetpack Compose + Material3 |
| 상태 | 피처별 `ViewModel` + `StateFlow` + `collectAsState` |
| 긴 텍스트 | `TextFieldState` + `AppMultilineTextField` (debounce) |
| 오버레이 | `FloatingAutomationBarController` + `FloatingAutomationBar` |
| 가이드 | `AGENTS.md`, `docs/clean-architecture.nano.md` |

### 소스 레이아웃 (병목 탐색 맵)

```
app/src/main/java/com/example/gemgemgen/
  ui/                 # 탭 셸, 공통 텍스트필드, 테마, Host
  automation/         # 자동화 탭 + 플로팅 바
    ui/ domain/ usecase/ android/
  analysis/           # 분석 생성 탭
    ui/ domain/ usecase/ android/
  wildcard/           # 와일드카드 관리 탭
    ui/ domain/ usecase/ android/
  environment/        # 접근성·설치 상태
  core/               # Dispatchers, Clipboard 등
```

피처 규칙: **policy = `domain`/`usecase`, UI = `ui`, 기기/파일/Prefs = `android`**.  
jank 수정이 레이어를 깨지 않게 한다.

### 메인 탭

| 탭 | `MainTab` | 대표 UI | ViewModel |
|----|-----------|---------|-----------|
| 자동화 | `AUTOMATION` | `AutomationScreen`, `PromptSection` | `MainViewModel` |
| 분석 생성 | `ANALYSIS` | `AnalysisScreen` | `AnalysisViewModel` |
| 와일드카드 | `WILDCARD` | `WildcardManagerScreen` (지연 로드) | `WildcardManagerViewModel` |

- `MainTabbedScreen`: 선택 탭 content만 구성.
- 와일드카드 VM: `shouldLoadWildcard` 이후 생성.
- Host가 `main` / `analysis` / `automationBar`를 동시에 collect →  
  한 Flow 갱신이 Host·형제 UI까지 리컴포즈하는지 **코드로** 추적.

### 코드상 jank 우선 후보

1. 긴 텍스트: `AppMultilineTextField` ↔ VM 문자열 동기화, 단락 하이라이트, transformation  
2. 대형 Screen: `AnalysisScreen`, `WildcardManagerScreen`, `PromptSection` — 상위 state 한 필드 → 전체 트리  
3. 플로팅 바: `automationBarUiState` 갱신 빈도·경로  
4. 탭/설정: 다이얼로그·포커스·폴더 변경 후 목록 재매핑  
5. I/O: use case의 `AppDispatchers.io` 여부 vs 메인 동기 호출·메인 파싱

### 범위 밖

- 외부 Gemini/ChatGPT 앱 Accessibility 속도  
- 네트워크/API 대기 시간 (체감 렉 ≠ Compose jank — 코드 경로로 구분)

---

## 언제 쓰는가

- 탭·텍스트·플로팅 바가 무겁다는 요청  
- 리컴포즈·메인 스레드·jank 키워드  
- `/reduce-ui-jank`

---

## 전체 루프 (필수 순서)

```
1) 분석(Analysis)  → 코드만으로 병목 후보 + 우선순위 (추정임을 명시)
2) 기획(Plan)      → 반드시 3안 → 사용자 선택
3) 구현(Implement) → 선택한 안만
4) 보고(Report)    → 무엇을 왜 바꿨는지 + 코드 근거 + 남은 리스크
```

- “분석만 / 기획만”이면 해당 단계만.  
- **실측·gradle 성능 검증 단계는 기본 루프에 넣지 않는다.**

---

## Phase 1 — 코드 분석

### 1.1 범위 (한 문장)

형식: **`[탭/오버레이] + [동작] + [상태]`**

예: “자동화 탭, 긴 프롬프트 타이핑 중”  
기본 탭 미지정 시 **자동화**.

### 1.2 코드 신호표

| 증상 후보 | 코드 신호 |
|-----------|-----------|
| 타이핑 끊김 | debounce 밖 매 입력마다 거대 `uiState` copy; 긴 문자열이 매 리컴포즈 props |
| 단락 hitch | `selectedParagraphRange` → transformation/`remember` 키 과다, 하이라이트 전체 재계산 |
| 화면 전체 갱신 | Host `collectAsState` 범위가 형제 탭·무관 서브트리까지 포함 |
| 오버레이 끊김 | `_automationBarUiState` 고빈도 `update`, 드래그+state 동시 경로 |
| 파일 오픈 멈칫 | 결과를 메인에서 무거운 파싱·리스트 전체 재매핑 |
| 상태 hitch | `environmentStatus` 등 큰 state 한 덩어리가 프롬프트 UI까지 무효화 |

검색:

```text
collectAsState, MutableStateFlow, _uiState.update, TextFieldState,
AppMultilineTextField, snapshotFlow, debounce, LaunchedEffect,
remember(, derivedStateOf, FloatingAutomationBar, automationBarUiState,
withContext, AppDispatchers, SharedPreferences, commit(
```

우선 파일:

| 우선 | 경로 |
|------|------|
| P0 | `ui/AppMultilineTextField.kt`, `ui/android/AndroidAutomationHost.kt` |
| P0 | `automation/ui/MainViewModel.kt`, `PromptSection.kt`, `AutomationScreen.kt` |
| P0 | `automation/ui/FloatingAutomationBar.kt`, `FloatingAutomationBarController.kt` |
| P1 | `analysis/ui/AnalysisScreen.kt`, `wildcard/ui/WildcardManagerScreen.kt` |
| P1 | `AnalysisViewModel.kt`, `WildcardManagerViewModel.kt` |

### 1.3 판단 기준 (코드만 — 상세는 reference)

- **확정에 가깝다:** 메인에서 동기 I/O/`commit`, 매 키마다 전체 Screen 재구성이 호출 그래프로 보임  
- **유력 추정:** 불필요 collect 범위, 거대 state 전파, 매 프레임급 매핑 — “코드상 유력”이라고 쓸 것  
- **근거 약함:** “파일이 커서 느릴 것 같다”만 있고 핫패스 연결이 없음 → P2 이하 또는 보류  

**프레임 ms·janky %를 코드만으로 단정하지 않는다.**  
“이렇게 바뀌면 갱신 범위/메인 작업이 줄어든다” 수준으로 말한다.

판단 체크리스트: `references/code-judgment-checklist.md`

### 1.4 분석 산출물 (한국어·쉬운 말)

1. 한 줄 요약  
2. 이미 괜찮은 구조 (탭 단일 content, 지연 로드, `withContext(io)` 등)  
3. 병목 P0/P1/P2 + **파일·심볼·왜 무거운지**  
4. 시나리오 ↔ 코드 경로 연결  
5. “코드 기반 추정” 명시 (실측 없음)  
6. 다음: 기획 3안  

파일 저장은 요청 시에만. 기본은 대화.

---

## Phase 2 — 기획 (3안 필수)

### 안 A — 기존 구조 보완  
debounce/`remember`/파라미터 축소, collect 범위 줄이기, 가드, item key 등.

### 안 B — 소폭 구조 수정  
Host 구독 분리, 바 state 분리, 서브컴포저블, VM 필드 쪼개기, 텍스트 동기화 API 정리.

### 안 C — 큰 재설계  
대형 Screen 분해, 편집 상태 단일 소스 재설계, 오버레이 렌더 경로 재배치.

| | 안 A | 안 B | 안 C |
|--|------|------|------|
| 핵심 아이디어 | | | |
| 주요 파일 | | | |
| 레이어 충돌? | | | |
| 리스크 | | | |
| 기대 효과 (코드 관점) | 갱신 범위↓ / 메인 작업↓ 등 | | |
| 권장 | (한 줄) | | |

- 추천 1개, 강요 금지. 선택 전 큰 수정 금지.  
- clean-architecture / `AGENTS.md` 충돌 짧게 표기.

---

## Phase 3 — 구현

1. 고른 안만 구현.  
2. 다른 안 리팩터 몰래 섞지 않음.  
3. 한국어·쉬운 설명.  
4. **성능 실측·필수 테스트 실행 없음.**  
   수정이 기존 유닛 테스트를 깨면 그 범위만 맞춘다 (사용자가 테스트 실행을 요청하지 않으면 실행 생략 가능; 깨진 테스트 코드를 방치하지 말 것).  
5. 범위 밖 발견은 보고만.

---

## Phase 4 — 보고 (코드 근거)

- 무엇을 왜 바꿨는지 (파일·의도)  
- 분석의 어떤 신호를 줄였는지  
- 한계: 체감·프레임 숫자는 확인하지 않음  
- 남은 P0/P1, 다음에 쓸 안(B/C)  
- 사용자가 나중에 체감 확인할 때 쓸 **짧은 수동 체크 문구**만 제안 (실행은 사용자 몫; 에이전트가 돌리지 않음)

---

## 행동 원칙

1. **코드 근거** — 파일·심볼·데이터 흐름. 추측이면 “추정”이라고 쓴다.  
2. **실측 금지(기본)** — adb·설치·시나리오 측정·성능 벤치를 먼저 하지 않는다.  
3. **3안 강제** — A/B/C 생략 금지.  
4. **선택 존중** — C여도 최소 변경으로 되돌리지 않음. 리스크 명시.  
5. **레이어 존중** — domain/usecase 순수성 유지.  
6. **보안** — API 키·토큰을 로그/예시에 넣지 않음.  
7. **단일 모듈** — 새 모듈보다 피처 패키지 경계 우선.

---

## 안티패턴

- 분석 없이 대형 Screen 리라이트  
- 3안 없이 한 길로 직행  
- “실측 없이 60fps 됐다” 식 과장  
- 기본 모드에서 adb/gfxinfo 루프 강제  
- Accessibility 외부 앱 지연을 Compose jank로 단정  
- 요청 밖 포맷·정리 섞기  
- 정책 로직을 Composable에 넣으며 성능 핑계

---

## 빠른 체크리스트

- [ ] 시나리오 한 문장  
- [ ] P0 + 코드 근거 (파일/심볼)  
- [ ] 추정 vs 확정 구분  
- [ ] 안 A / B / C + 추천 + 레이어  
- [ ] 사용자 선택  
- [ ] 구현  
- [ ] 코드 근거 보고 + 남은 리스크  
- [ ] (하지 않음) 실기기 측정·성능 테스트 필수화  
