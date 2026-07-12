# 자동화 실행 경로 RAM·불필요 동작 검토 보고서

| 항목 | 내용 |
|------|------|
| 작성일 | 2026-07-12 |
| 수정 | 2026-07-12 — … (13) §4.3 package. (14) §4.6 Phase1: 프로세스 단일 RunAutomation + runState + 서비스 끊김 finish |
| 범위 | 자동화 실행 · 접근성 서비스 상시 비용 · **Activity/ViewModel 생명주기와 실행 소유권** |
| 방법 | 소스 코드 경로 추적만 (실기기 힙 덤프·Profiler 측정 **없음**) |
| 전제 | **기능 변경 없음**. 수정 제안은 후속 작업용 참고이며 본 문서는 구현하지 않음 |
| 판단 표시 | **코드상 유력** / **추정** / **의도적·필요** 를 구분 |

---

## 1. 한 줄 요약

**우선 최적화 축 (누적 RAM·안정성)**

0. **P0 세션 잔여:** Handler session token · stale callback (§4.5-B)·(중기) Job 구조 — Phase1(단일 엔진·runState·서비스 끊김 finish)은 적용됨 (§4.6)  
1. **P0 실행 중:** 트리 flatten · 스냅샷 캐시 (§4.1)  
2. **P1 baseline:** 와일드카드 탭 한 번 연 뒤 편집 텍스트·undo Activity 상주 (§4.7)  
3. **P2 baseline:** `AnalysisViewModel` 항상 생성·collect, 분석 결과가 자동화 중에도 유지 (§4.8)  

- `lastPrompt` / `generateFinalPrompt` 는 P2 필드 정리 (§4.0·§4.2). `recycle()` 누수 해석 철회 (§4.1.1).

기능상 필수: 와일드카드 로드, 회차 문자열 생성, SET_TEXT, 재시도, 클립보드 템플릿 쓰기(의도), root 폴링 전송.

---

## 2. 실행 파이프라인 (코드 기준)

```
MainViewModel.runAutomation()
  → AutomationRunPreparer.prepare()     // snapshot 저장, 클립보드 쓰기, wildcard 로드, compile
  → RunAutomationUseCase.startPreparedRun()
       IME → Null Keyboard
       대상 앱 launch
       sendMarker()                     // 고정 마커 프롬프트 1회
       sendNextPrompt() 루프 × N        // 생성 → Accessibility sendPrompt
  → finishRun()                         // IME 복구, currentRun = null
```

Accessibility 1회 `sendPrompt` 대략 흐름:

```
openNewChat → setPromptText(ACTION_SET_TEXT) → 입력 확인 → clickSendWhenReady → 전송 확인 → onDone
```

상태 전파:

```
onStateChange(Running)
  → MainViewModel.handleAutomationState
       · main uiState: Running 중에는 coarse(첫 Running 유지)  ← 이미 완화됨
       · automationBarUiState: 세부 step 전부 반영 (Running에 lastPrompt 필드도 실리지만 UI 미사용)
  → FloatingAutomationBar / AutomationActionBar (실제 표시: step · currentIndex · totalCount)
```

---

## 3. 이미 괜찮은 구조 (감점하지 않음)

| 항목 | 근거 |
|------|------|
| 준비 I/O를 백그라운드 | `AutomationRunPreparer.prepare` → `withContext(dispatchers.io)` |
| 반복분 미리 전부 생성하지 않음 | 루프마다 `promptPlan.generate(nextIndex)` 1건 (전체 `generate(1..N)` 미사용) |
| 메인 탭 Running 세부 스팸 완화 | `coarseAutomationStateFor`: Running→Running 시 main `uiState` 유지 |
| 플로팅 바 드래그 스로틀 | `Choreographer`로 프레임당 1회 layout 갱신 |
| 와일드카드 토큰 필터 | `load(tokens)` 로 템플릿에 있는 토큰만 파일 로드 |
| 노드 스냅샷 단기 캐시 | Gemini/ChatGPT finder 32ms 재사용 (트리 완전 재탐색 완화 목적) |
| 실행 종료 시 run 해제 | `finishRun` → `currentRun = null` |
| 접근성 XML content-changed 미구독 | `gemini_accessibility_service.xml` → `typeWindowStateChanged`만 (고빈도 content IPC 제거) |
| 접근성 이벤트 수신 중단 | `eventTypes = 0` (이벤트 미사용·폴링 전송) |
| 접근성 package 범위 | 전송 중 대상 앱만; idle·최근 앱 닫기 전 `packageNames = null` |
| 프로세스 단일 자동화 엔진 (Phase1) | `ProcessAutomationHolder` + `runState` 공유; 서비스 끊김 → `onAccessibilityLost` finish |

---

## 4. RAM·불필요 동작 후보 (우선순위)

심각도는 **앱 힙 할당·장기 참조 보유·동작 안정성** 기준입니다.  
(`recycle()` 미호출을 네이티브 누수로 보는 해석은 **부정확** — §4.1.1.)  
프레임 시간·MB 숫자는 측정하지 않았습니다.

### 4.0 항목별 판정 수정 — `lastPrompt` (2026-07-12)

| 판정 항목 | 결과 |
|-----------|------|
| UI에서 `lastPrompt` 미사용 | **맞음** |
| 제거 안전성 | **안전한 정리 작업** (표시 기능 변화 없음; 테스트 fixture만 조정) |
| 초기 문서의 “P0 RAM · 매 state 대형 문자열 복제” | **과장 → 철회** |
| 수정 등급 | **P0 RAM → P2 구조 정리 / Low-risk cleanup** |

**근거 (올바른 해석)**

1. `RunAutomationUseCase.updateRunState`는 매 단계 `CurrentRun.lastPrompt`를 `AutomationRunState.Running`에 넣는다.  
2. 실제 UI가 쓰는 필드는 **`step` · `currentIndex` · `totalCount`뿐**이다.  
   - `AutomationUiText.statusText` → `step`만  
   - `AutomationActionBar` → 진행 인덱스 + 상태 문자열  
3. Kotlin `data class` 복사·필드 전달은 **shallow copy**다. `String` **내용이 단계마다 재복제되지 않고 같은 객체 참조를 공유**한다.  
4. `StateFlow`는 과거 state를 쌓지 않고 **최신 값 하나**를 유지하며, 빠른 업데이트는 **conflation**된다.  
5. 제거해도:  
   - “단계마다 프롬프트 버퍼 복제” 문제는 **원래 없음**  
   - `step`이 계속 바뀌므로 바 **recomposition 빈도는 거의 줄지 않음**  
   - 전송 중 `Handler` callback이 이미 `prompt`를 참조함  

**정리:** 코드 품질·API 표면 축소에는 이득. **체감 RAM 개선은 작을 가능성이 큼.**

### P2 — Low-risk cleanup: UI 미사용 `lastPrompt` (구조 정리)

| | |
|--|--|
| **위치** | `AutomationRunState.Running.lastPrompt`, `RunAutomationUseCase.updateRunState`, `CurrentRun.lastPrompt` |
| **동작** | 매 step 갱신 시 `Running(..., lastPrompt = run.lastPrompt)` 생성 후 `automationBarUiState`에 전달 |
| **표시 여부** | UI 미사용 (테스트 fixture에서만 등장) |
| **RAM 관점** | 참조 공유 + StateFlow 단일 최신값 → **P0급 힙 폭증 아님**. 제거 시 체감 RAM 이득 **작음** |
| **기능 영향** | 제거해도 표시 기능 동일 (테스트 fixture 조정) |
| **후속 성격** | **구조 정리 / 데드 필드 제거**. RAM 최적화 최우선 아님 |

### 4.1 / P0 — Accessibility 전체 트리 flatten + 캐시 (핵심 방향 유지 · 해석 정밀화)

**판정:** 핵심 방향(우선 최적화 대상)은 **맞음**. 아래는 “32ms 캐시 = 32ms 후 메모리 해제” 식 오해를 바로잡은 버전이다.

| | |
|--|--|
| **위치** | `GeminiAccessibilityNodeFinder` / `ChatGptAccessibilityNodeFinder` 의 `nodes()` · flatten · `cachedRoot` / `cachedNodes` / `cachedAtMillis` |
| **수명** | Finder는 `GeminiPromptAutomation` / `ChatGptPromptAutomation` 안에 있고, 둘은 `GeminiAccessibilityService`의 **lazy 인스턴스** → **접근성 서비스 생애 동안** 유지될 수 있음 |
| **동작** | 두 finder 모두 트리를 **전체 순회**하여 `List<AccessibilityNodeInfo>`를 만든다. `nodes()`가 입력·보내기·메뉴 등 탐색 경로에서 반복 호출됨 |
| **재시도 간격** | `AutomationRetryWaitPolicy`: 경과 3초 미만 → **250ms**, 이후 **1000ms**, 단계 창 **최대 10s** (`TOTAL_RETRY_WINDOW_MS`). 문서 해석 **정확** |
| **캐시 상수** | `NODE_SNAPSHOT_CACHE_MS = 32L` — **같은 root 참조 + 32ms 이내**일 때만 이전 리스트 재사용 |

#### 실제 문제 (코드상)

1. **트리 전체 순회**에 따른 노드 조회(`getChild` 등)와 **리스트 할당**  
2. **retry 간격(250ms / 1s) ≫ 32ms** 이므로, 재시도 경로에서는 캐시 히트 가능성이 낮고 **반복 전체 순회**가 나기 쉬움  
3. **32ms는 재사용 유효시간일 뿐**, 만료 시 `cachedNodes`가 자동으로 비워지지 **않음**  
4. 다음 `nodes()`가 **새 스냅샷으로 필드만 덮어쓰거나**, 서비스/인스턴스가 끝날 때까지 **마지막 스냅샷이 finder 필드에 잔류**  
5. UI hierarchy가 바뀐 뒤에도 잘못된 조건으로 옛 리스트를 쓰면 **stale-node** 동작 가능  

#### 32ms 캐시에 대한 올바른 이해

| 오해 | 실제 |
|------|------|
| 32ms 후 캐시가 메모리에서 제거된다 | **아님.** 타이머 만료 해제 로직 없음 |
| 캐시가 있으면 재시도마다 순회를 줄인다 | retry 지연이 32ms보다 커서 **대부분 재순회** |
| 필드에 남은 노드는 항상 현재 화면과 일치 | **아님.** 공식 문서도 View와 빠르게 불일치할 수 있으므로 hierarchy 변경 검사 시 **새 노드**를 얻어야 한다고 함 |
| `recycle()`을 안 해서 네이티브가 샌다 | **부정확 (API 33+).** §4.1.1 |

#### 4.1.1 `recycle()` 미호출 = 네이티브 누수? → **수정·철회**

초기 문서/검토가 “노드를 `recycle()`하지 않아 네이티브 압력이 커질 수 있다”고 본 것은 **현재 Android 기준으로 부정확**하다.

Android 공식 문서 기준 (`AccessibilityNodeInfo.recycle()`):

| 사실 | 내용 |
|------|------|
| API 33 | `recycle()` **deprecated** |
| API 33+ | **object pooling 폐지** |
| 호출 효과 | API 33 이상에서는 **호출해도 아무 효과가 없음** |

따라서:

- **우선 최적화 이유에서 `recycle()` 미호출·네이티브 풀 누수를 근거로 쓰지 않는다.**  
- 남는 실질 이슈는 **Java/Kotlin 쪽 참조 보유**(finder 필드의 리스트·노드), **전체 순회로 인한 반복 할당**, **stale hierarchy** 이다.  
- 스냅샷 교체 시 할 일은 “옛 리스트를 `recycle()`로 반납”이 아니라, **참조를 끊고 새 hierarchy에서 노드를 다시 얻는 설계**에 가깝다.  
- **minSdk = 33** (Android 13+). `recycle()`·object pooling 구 API 분기는 이 프로젝트에서 다루지 않는다.

#### RAM / 안정성 / 기능

| 관점 | 설명 |
|------|------|
| **힙·할당** | 전체 순회 시 **단기 리스트·노드 객체 할당 피크** + 서비스 생애 동안 **마지막 스냅샷 참조 보유**. 확정 MB는 프로파일 필요 |
| **네이티브 풀** | API 33+에서 `recycle()` 기반 풀 반납 **해당 없음** (§4.1.1) |
| **안정성** | stale snapshot → 잘못된 클릭/입력 실패·재시도 루프 악화 가능 (**기능 버그 축**) |
| **기능** | 노드 탐색 자체는 전송에 **필요**. 바꿀 것은 “매 탐색마다 전체 flatten + 장기 필드 캐시” **방식** |

**후속 성격:** **실제 우선 최적화 대상** (할당 빈도·참조 수명·stale 방지). `recycle()` 추가가 아님. `lastPrompt` 정리보다 우선.

### P1 — 코드상 유력: 원본 프롬프트 문자열 다중 보유

실행 중 동시에 붙을 수 있는 동일·유사 문자열:

| 보유처 | 필드/경로 |
|--------|-----------|
| 요청 | `AutomationRunRequest.promptTemplate` (`PreparedAutomationRun`이 참조) |
| 런 | `CurrentRun.promptTemplate` |
| 컴파일 결과 | `PromptGenerator.CompiledPrompt` 내부 `basePrompt` |
| 생성 1회 | `GeneratedPrompt.basePrompt` + `finalPrompt` (매 회차 임시) |
| 런 추적 | `CurrentRun.lastPrompt` (= 직전 `finalPrompt` 참조; UI state와 공유 가능) |
| UI state | `Running.lastPrompt` (UI 미사용; **P2 정리**, §4.0) |
| 메인 VM (앱 생존) | `MainUiState.promptTemplate` / TextField / `promptTemplateValue` |
| 준비 단계 부수 | Prefs 스냅샷, **클립보드**에 템플릿 복사 (의도적) |

동일 `String` 인스턴스를 여러 필드가 가리키면 **내용 복제가 아니라 참조 공유**다.  
**실제 별도 버퍼**가 생기는 쪽은 주로 `generate`의 `replace` 결과(`finalPrompt`), Prefs 직렬화, 클립보드 기록 등이다.  
필드 개수가 많다는 것만으로 P0 RAM으로 올리지 않는다.

**기능:** 템플릿·생성 결과 자체는 필요. 레이어 축소는 **가독성·유지보수** 성격이 더 큼.  
`GeneratedPrompt`가 회차마다 `basePrompt`·`replacements`까지 담는 점은 **§4.2**에서 별도 검토.

### 4.2 매 회 `replacements` Map · `GeneratedPrompt` (제안 검토)

사용자 제시 개선안을 코드와 대조한 결과이다.

#### 사실 확인 (코드)

| 주장 | 판정 | 근거 |
|------|------|------|
| 회차마다 `tokens.mapNotNull { … }.toMap()` 로 치환 Map 생성 | **맞음** | `CompiledPrompt.chooseReplacements()` |
| 임시 Pair·리스트·Map 할당이 생김 | **맞음** | `mapNotNull` → `toMap()` 경로 |
| `GeneratedPrompt`가 `basePrompt` + `finalPrompt` + `replacements` 보유 | **맞음** | `GeneratedPrompt` data class · `generate(index)` |
| 자동화 실행 경로는 **`finalPrompt`만** 사용 | **맞음** | `RunAutomationUseCase.sendNextPrompt`: `generatedPrompt.finalPrompt` 만 `lastPrompt`·`sendPrompt`에 전달 |
| 실행 **간** 누적(맵이 계속 쌓임)은 아님 | **맞음** | 회차 객체는 다음 회차 전 GC 대상. StateFlow에 replacements를 쌓지 않음 |
| 토큰 多 · 반복 多 · 프롬프트 김 · 재실행 多 시 **1회 실행 할당량↑** | **타당 (추정)** | 회차 × (Map + wrapper + `finalPrompt` 문자열) 단기 할당. 확정 MB는 프로파일 필요 |
| 같은 토큰이 본문에 여러 번 나와도 **같은 값**으로 치환 | **현재 동작** | `chooseReplacements` 후 `tokenRegex.replace`가 lookup Map 사용. 경량 API에서도 **생성 중 lookup은 필요**할 수 있음 |

#### 개선안 타당성

제안:

```text
// 분석·테스트·디버그용 기존 API 유지
CompiledPrompt.generate(index): GeneratedPrompt
PromptGenerator.generate(...): List<GeneratedPrompt>

// 자동화 핫패스용 경량 API 추가
CompiledPrompt.generateFinalPrompt(index: Int): String
```

자동화(`RunAutomationUseCase`)는 `generateFinalPrompt`만 써서 다음을 **반환 경로에 올리지 않음**:

- `GeneratedPrompt` 객체  
- 외부로 노출되는 `basePrompt`  
- 외부로 노출되는 `replacements` Map  

생성 내부의 작은 lookup Map은 **동일 토큰 일관 치환**을 위해 둘 수 있고, **반환 직후 버려** `GeneratedPrompt`에 실지 않는 것이 핵심.

| 판정 | 내용 |
|------|------|
| **타당성** | **타당.** 핫패스 사용 필드와 API 표면이 어긋나 있음. 기존 API 유지 + 경량 API 추가는 레이어(domain) 안에서 안전하게 가능 |
| **기능 리스크** | 낮음 — 치환 규칙·랜덤 시드 동작을 `generate`와 공유하면 동일. 테스트는 기존 `generate`/`GeneratedPrompt` 유지 |
| **연동** | `RunAutomationUseCase`의 `generatePrompt` 주입 타입(`(…) -> GeneratedPrompt`)도 장기적으로 `String` 반환으로 단순화 가능 (테스트 fake 정리) |
| **심각도** | **P2 ~ 낮은 P1 (회차 할당 정리).** 실행 간 누적·P0 힙 폭증은 **아님**. Accessibility 트리(§4.1)보다 우선순위 낮음. `lastPrompt` UI 필드 정리와 비슷한 “품질·할당” 축 |
| **기대 효과** | 회차당 wrapper·Map·`basePrompt` 참조 필드 할당 감소. **`finalPrompt` 본문 할당·SET_TEXT·Handler 참조는 그대로**라 체감 RAM은 토큰 수·반복 수에 따라 **작~중** |
| **과장 금지** | “Map을 안 만들면 긴 프롬프트 RAM이 반으로” 식 주장은 부적절. 큰 비용은 여전히 **`finalPrompt` 문자열과 Accessibility 탐색** |

#### 권장 구현 스케치 (미구현 · 참고만)

```kotlin
// CompiledPrompt
fun generateFinalPrompt(index: Int): String {
    val replacements = chooseReplacements()
    return tokenRegex.replace(basePrompt) { match ->
        replacements[match.value] ?: match.value
    }
    // replacements·중간 구조는 스택/지역에서 종료 → GeneratedPrompt에 보관하지 않음
}

fun generate(index: Int): GeneratedPrompt {
    val replacements = chooseReplacements()
    val finalPrompt = tokenRegex.replace(basePrompt) { ... }
    return GeneratedPrompt(index, basePrompt, finalPrompt, replacements)
}
```

`generate`와 `generateFinalPrompt`가 치환 로직을 한곳에서 쓰도록 private 헬퍼로 묶으면 동작 드리프트를 막기 쉽다.

### 4.4 P0 — Activity/ViewModelStore clear 후 이전 자동화가 남는 구조 (조건부 · 제안 검토)

**판정: 맞지만 조건부.**  
정확한 분류는 **조건부 orphan 실행 및 lifecycle race** 이다.  
“백그라운드만 가면 항상 영구 누수”처럼 읽히면 **과장**이다.

#### 정확한 표현 (오해 방지)

| 구분 | 내용 |
|------|------|
| **핵심 조건** | `ViewModelStore`가 **실제로 clear**되는 경우 — 예: **task 제거**, Activity **finish** 등. 이때 프로세스가 AccessibilityService로 살아 있으면 Handler 쪽 실행이 남을 수 있음 |
| **해당 아님 (정상 경로)** | 자동화 시작 후 `moveTaskToBack(true)` 로 **백그라운드 이동만** 한 경우 → Activity/ViewModel이 **즉시 제거되지 않음**. 같은 VM·use case가 유지되는 것이 정상 UX |
| **설정 변경** | 회전 등으로 Activity 재생성 시 ViewModelStore 유지 → **같은** ViewModel/use case (orphan 시나리오 아님) |
| **영구 누수?** | **항상 아님.** 각 실행이 **정상 terminal**(`finishRun`)에 도달하면 callback·CurrentRun 참조는 해제될 수 있음. 문제는 끝나기 전 clear + 재실행 겹침, 또는 terminal 미도달(§4.5) |
| **기능 안정성** | **P0** — 긴 실행 중 task 제거 → 앱 재실행 → **새 실행 시작** 시 두 실행이 **같은 Handler에서 겹칠 가능성 실재** |
| **RAM** | **조건부 누적 위험** — orphan이 생기거나 여러 실행 그래프가 겹칠 때만 프롬프트·와일드카드·VM 캡처가 중첩. 정상 완료·정상 백그라운드만으로는 “항상 쌓임”이 아님 |

#### 현재 소유권 구조 (코드 확인)

| 주장 | 판정 | 근거 |
|------|------|------|
| Activity마다 `AndroidAppContainer` | **맞음** | `MainActivity` lazy container |
| 새 `MainViewModel`마다 새 `RunAutomationUseCase` | **맞음** | factory가 매번 새 인스턴스 |
| 준비 코루틴은 Handler 등록 후 종료 가능 | **맞음** | `automation.run()` → `sendPrompt`가 `handler.post` 후 반환, Job 곧 완료 |
| 이후 실행은 Handler callback 체인 | **맞음** | post / postDelayed 재시도·입력·전송 확인 |
| 람다가 prompt, run, onStateChange, onDone 등 캡처 | **맞음** | |
| `onStateChange` → `MainViewModel::handleAutomationState` | **맞음** | |
| `onCleared`에서 자동화 cancel 없음 | **맞음** | |
| `currentRun`은 **인스턴스 로컬** 가드 | **맞음** | 프로세스 전역 잠금 아님 |
| Gateway·Handler는 서비스 공유 | **맞음** | |

#### 조건부 겹침 시나리오 (코드상 가능)

```text
실행 A 시작 → Handler에 A callback (prompt/run/onStateChange/onDone 캡처)
[정상] moveTaskToBack → VM 유지, orphan 아님
[위험] task 제거 / finish 등으로 ViewModelStore clear
  → cancel 없음 → Handler에 A callback 잔존 가능
앱 재실행 → ViewModelB·useCaseB (currentRun == null) → 실행 B 가능
동일 AccessibilityService Handler에서 A·B 교차 가능
A 또는 B가 정상 finishRun 하면 그 쪽 참조는 풀릴 수 있음
둘 다 살아 있는 구간 = lifecycle race + 기능 경합 + (조건부) RAM 중첩
```

`cancelCurrentRun()`의 `removeCallbacksAndMessages(null)`은 **호출되면** 큐를 비우지만, orphan A에 대해 호출되지 않으면 남고, B 시작 시 A를 끊는 코드도 없다.

#### 겹침 구간에 붙잡을 수 있는 것 (조건부)

| 보유 | 내용 |
|------|------|
| 실행 데이터 | template, wildcards, `CompiledPrompt`, `lastPrompt` 등 |
| IME 세션 | `finishRun` 전 |
| 상태 콜백 | bound `MainViewModel` (callback이 살리면 VM·TextField·undo 등) |

긴 반복 · 큰 와일드카드 · 긴 템플릿 · **실행 중 store clear** · **재실행 시작** 조합에서 기능·조건부 RAM 위험이 커진다는 점에 동의.

#### 부수 위험 (기능 축 · P0에 해당)

- A/B가 같은 입력창·보내기 동시 조작  
- A `finishRun`의 IME 복구가 B 중 개입  
- late callback과 새 UI state 엇갈림  

#### 개선 방향 (미구현 · 타당성)

| 방향 | 타당성 | 비고 |
|------|--------|------|
| 정책 B면 `onCleared`에서 session cancel | **타당** | store clear 시 orphan 방지 |
| 정책 A면 VM은 observer, cancel은 store clear와 분리 | **타당** | §4.6 · 플로팅 바와 정합 |
| **프로세스 단위 단일 실행** | **필수에 가까움** | 로컬 `currentRun`만으로는 race 불가피 |
| 새 실행 전 기존 세션 종료 + session ID | **타당** | late callback no-op |
| (중기) suspend + 단일 Job | **타당** | |

#### 심각도 (표현 정리)

| 축 | 등급 | 설명 |
|----|------|------|
| **기능 안정성** | **P0** | Handler 공유 + 인스턴스 로컬 가드 → **lifecycle race로 이중 실행 가능** |
| **RAM** | **조건부 누적 위험** | 영구 누수 단정 금지. clear+재실행·미종료 orphan 구간에서만 그래프 중첩 |
| **분류 명칭** | | **조건부 orphan 실행 및 lifecycle race** |
| **연관** | | 서비스 중단 시 finish 없음 → **§4.5**; 뿌리 → **§4.6** |

### 4.5 P0 — AccessibilityService 중단 시 callback만 삭제 · 실행 미종료 / 전역 Handler 취소 (제안 검토)

**판정: 타당.** §4.4(orphan 소유권)와 같은 “세션 수명” 축이며, **서비스 끊김·취소 API** 쪽 구멍이다.

#### A. 서비스 중단/종료 시 — callback 삭제만, 세션 미종료

| 주장 | 판정 | 근거 |
|------|------|------|
| Gemini·ChatGPT 자동화가 **동일 Handler** 사용 | **맞음** | `GeminiAccessibilityService`의 `handler` 하나를 두 lazy automation에 전달 |
| `onInterrupt` / `onDestroy`에서 `handler.removeCallbacksAndMessages(null)` | **맞음** | `GeminiAccessibilityService` 40–51행 |
| 그때 `onStateChange(Failure)` / `onDone` / `finishRun` 없음 | **맞음** | 서비스는 close-Gemini 쪽 `finishCloseGemini`만 호출. **RunAutomationUseCase에 알림 없음** |
| 예약 callback 사라짐 | **맞음** | 큐 전량 삭제 |
| `currentRun` 잔존 · `finishRun` 미호출 | **맞음** | use case는 서비스 생명주기를 모름 |
| IME 복구 누락 가능 | **맞음** | `finishRun` 안의 `imeManager.restore` 미실행 |
| UI `Running` 고착 · 프롬프트/와일드카드 유지 | **맞음** | terminal state 미전달; `CurrentRun` 유지 |
| **같은 ViewModel**에서는 새 실행 차단 | **맞음** | 해당 인스턴스 `currentRun != null` |
| Activity 재생성 후 새 use case면 새 실행 가능 | **맞음** | §4.4 orphan 누적과 **결합** |

정리:

```text
서비스 onInterrupt/onDestroy
  → Handler 큐 삭제 (자동화·재시도·closeGemini 지연 작업 포함 가능)
  → RunAutomationUseCase에는 통지 없음
  → currentRun / IME 세션 / UI Running / 대형 데이터 잔존
  → (같은 VM) 재시작 불가 또는 (새 VM) orphan A + 실행 B
```

#### B. `cancelCurrentRun()` — per-run이 아니라 **Handler 전체** 삭제

| 주장 | 판정 | 근거 |
|------|------|------|
| `cancelCurrentRun` = `removeCallbacksAndMessages(null)` | **맞음** | `AccessibilityPromptAutomation` |
| null token = **모든** callback/message | **맞음** | Android `Handler` API |
| 의도적 UI 취소(`RunAutomationUseCase.cancel`)는 해당 실행에 대해 `finishRun` 호출 | **맞음** | cancel 후 `finishRun(..., Stopped)` — **그 use case 인스턴스만** |
| 겹친 **다른** 실행의 callback도 같이 지워짐 | **맞음 (코드상)** | 공유 Handler + token 없음 |
| 다른 target(Gemini/ChatGPT) 자동화 callback도 동일 큐 | **맞음** | 같은 `handler` |
| closeGemini의 `postDelayed` 등 서비스 지연 작업도 삭제 가능 | **맞음** | 동일 `handler` 사용 |
| 지워진 **다른** 실행은 terminal callback 없이 `CurrentRun` 유지 가능 | **맞음** | 다른 use case 인스턴스는 `finishRun`을 받지 못함 |

즉 UI에서 “지금 이 실행 취소”를 해도, orphan/겹침이 있으면 **옆 실행은 좀비 `currentRun`** 이 될 수 있다.

#### 개선안 타당성

**1) 실행별 token (`AutomationSession`) — 타당 · 현 Handler 구조에 맞는 최소 수정**

```text
data class AutomationSession(val id: Long, val callbackToken: Any)

// 등록 (개략)
handler.postDelayed(runnable, session.callbackToken, delayMillis)

// 해당 실행만 제거
handler.removeCallbacksAndMessages(session.callbackToken)
```

| 항목 | 내용 |
|------|------|
| **타당성** | **높음.** `post { }` / token 없는 `postDelayed`를 token 있는 등록으로 통일해야 함 |
| **효과** | 한 실행 취소가 다른 실행·closeGemini 지연 작업을 쓸어 버리지 않음 |
| **한계** | 서비스 `onDestroy` 시에는 **활성 세션 전부**에 대해 token 제거 **+** use case에 Failure/Stopped 통지 필요. token만 지우면 §4.5-A와 같은 좀비 `currentRun` 재발 |
| **API** | `Handler.postDelayed(Runnable, Object token, long)` / `removeCallbacksAndMessages(token)` (프로젝트 minSdk 33과 호환) |

**2) 서비스 중단 시 세션 종료 통지 — 타당 · 필수 보완**

- `onInterrupt` / `onDestroy`에서: 큐 정리 **이전 또는 이후**에 **단일 세션 레지스트리**에 “접근성 끊김” 알림  
- 레지스트리가 `finishRun` 상당 처리 (IME 복구, `currentRun = null`, UI Failure/Stopped)  
- §4.4의 **프로세스 단위 단일 세션**과 맞물리면 구현이 단순해짐  

**3) callback 체인 → suspend + structured concurrency — 타당 · 더 큰 구조 개선**

| 항목 | 내용 |
|------|------|
| **타당성** | **높음 (중장기).** application/service scope의 **하나의 Job**으로 실행하면 cancel = 자식 전부 취소 + `finally`에서 IME/상태 정리 |
| **효과** | Handler token 수작업·재귀 callback 캡처 감소; §4.4 소유권과 정합 |
| **비용** | `AccessibilityPromptAutomation` 전면 개편; 테스트·재시도 타이밍 재검증 |

권장 순서: **(단기)** session token + 서비스 끊김 시 세션 Failure 통지 + §4.4 단일 세션 → **(중기)** suspend/Job 구조.

#### 심각도 · §4.4와의 관계

| 항목 | 내용 |
|------|------|
| **등급** | **P0** (세션 좀비 · IME · UI 고착 · 취소 시 타 실행 손상) |
| **§4.4** | ViewModelStore clear 후 **조건부 orphan · lifecycle race** (기능 P0 / RAM 조건부) |  
| **§4.5** | 서비스 끊김/전역 cancel로 **콜백은 죽이고 세션 메타는 살림** 또는 **타 세션까지 콜백만 죽임** |
| **결합** | 둘 다 “실행 수명 = Handler 큐”에 의존하고 수명 종료 시 **대칭적인 finish가 없음** |
| **뿌리** | 프로세스 전역 세션 부재 → **§4.6** |

### 4.6 P0 — 프로세스 전체 자동화 세션이 없음 (권장 구조 · 제안 검토)

**판정: 타당.** §4.4·§4.5는 증상이고, 여기서 말하는 **단일 active session 부재**가 구조적 원인에 가깝다.

#### 현재 상태 분산 (코드 확인)

| 위치 | 무엇을 아는가 | 무엇을 모르는가 |
|------|----------------|-----------------|
| `RunAutomationUseCase.currentRun` | **그 인스턴스**의 실행 데이터 | 다른 use case·서비스 전역 실행 여부 |
| AccessibilityService 공용 `Handler` | 예약된 callback 큐 | “지금 활성 세션 ID / 소유자” |
| `GeminiAccessibilityService` | target별 gateway (`gatewayFor`) | run 소유권, run ID, activeSession |
| `MainViewModel` | UI state + `::handleAutomationState` | 프로세스에 다른 실행이 있는지 |

서비스 생애 동안 Gemini/ChatGPT automation 인스턴스와 Handler는 lazy로 **유지**되지만, 그 위에 **“현재 활성 자동화 세션” 레지스트리는 없다.**  
실행 배타성(`currentRun != null`)은 use case **로컬**뿐이라 §4.4처럼 인스턴스가 바뀌면 무력화된다.

#### 제품 정책이 불명확한 “중간 상태” (타당)

현재 코드는 두 정책을 **어느 쪽도 끝까지** 구현하지 않는다.

| 정책 | 의미 | 현재 |
|------|------|------|
| **A. Activity 없어도 자동화 계속** | VM은 observer; 세션은 app/service 스코프 | 부분: `moveTaskToBack` + 플로팅 바 **의도**. store **clear**(task 제거 등) 시에만 §4.4 race — 백그라운드 이동만으로는 VM 즉시 제거 아님 |
| **B. Activity 사라지면 자동화 종료** | `onCleared`에서 session cancel | **미구현** (`onCleared` 없음) |

→ 백그라운드는 이어 가려 하면서, 수명은 VM/Handler에 흩어져 있어 **A도 B도 아닌 중간 상태**라는 진단에 동의.

**이 앱 UX 힌트:** 시작 시 `moveTaskToBack` + 오버레이 바 → 정책 **A(세션 지속, VM 관찰)** 쪽이 제품 의도에 더 가깝다.  
다만 task **완전 제거** 시 계속 vs 중지 는 **별도 명시**가 필요하다 (A를 택해도 coordinator는 필수).

#### 권장 구조 (제안 · 타당)

```text
MainViewModel
  └─ start / cancel / observe 요청만

Application-scoped (또는 Service-scoped) AutomationRunCoordinator
  ├─ activeSession: AutomationSession?   // 프로세스 전체 최대 1
  ├─ global run exclusivity
  ├─ prompt / wildcard plan
  ├─ IME session
  └─ terminal cleanup (정확히 1회)

AccessibilityService
  └─ session ID가 포함된 실제 Android 동작 (노드·제스처·Handler token)
```

| 핵심 규칙 | 타당성 | 비고 |
|-----------|--------|------|
| 프로세스 전체 active session **최대 1개** | **필수** | §4.4 다중 CurrentRun 차단 |
| 새 Activity여도 **같은 session 관찰** | **A 정책 시 필수** | StateFlow/SharedFlow를 coordinator가 소유 |
| session 살아 있으면 **새 실행 거부** | **필수** | 인스턴스 로컬 가드 대체 |
| service disconnect 시 active session **Failure 종료** | **필수** | §4.5-A 보완 |
| callback마다 **session ID 확인** · 오래된 것은 no-op | **필수** | §4.5-B late callback 방어 |
| terminal cleanup **정확히 한 번** | **필수** | IME 복구·`activeSession=null` 이중 호출 방지 |

**정책 분기 (구현 전 결정):**

| 선택 | `onCleared` | Coordinator |
|------|-------------|-------------|
| **A. 백그라운드/재진입 후에도 계속** (플로팅 바와 정합) | **cancel 하지 않음** · VM은 collect만 | Activity 재생성 시 동일 session 재구독 |
| **B. UI 생명주기 = 실행 생명주기** | **session cancel** | 여전히 단일 session + service Failure 통지는 필요 |

#### 레이어 배치 메모 (clean architecture)

- **Coordinator** 위치: `automation` 유스케이스/애플리케이션 서비스에 가깝고, Android Handler·Accessibility는 `automation/android` 어댑터.  
- domain은 session ID·상태 enum 정도만 순수하게 둘 수 있음.  
- `RunAutomationUseCase`의 `currentRun`은 coordinator 내부 구현으로 흡수하거나, coordinator가 **유일 호출자**가 되게 한다.

#### 심각도

| 항목 | 내용 |
|------|------|
| **등급** | **P0 구조** — §4.4·§4.5 수정이 이 모델 없이 부분 패치되면 재발하기 쉬움 |
| **우선** | 세션 소유권 이전 + 단일 activeSession + 정책 A/B 명시 → 그다음 Handler token / flatten |
| **RAM** | 다중 orphan 그래프 방지; 정상 1세션 피크는 별개 |

### 4.7 P1 — 와일드카드 탭을 한 번 열면 편집 데이터가 이후 자동화 baseline에 남음 (제안 검토)

**판정: 타당.** 실행마다 무한 증가는 아니지만, 탭을 연 **이후 모든 자동화**가 더 높은 메모리 baseline에서 시작한다.

#### 사실 확인 (코드)

| 주장 | 판정 | 근거 |
|------|------|------|
| `shouldLoadWildcard` 초기 false | **맞음** | `AndroidAutomationHost` |
| 와일드카드 탭(또는 폴더 선택 경로)에서 true로 전환 | **맞음** | `onSelectTab(WILDCARD)` / `selectWildcardFolder` 실패 시 true |
| 다시 false로 내리는 경로 없음 | **맞음** | 코드 전역 `shouldLoadWildcard = false` 없음 |
| 이후 `WildcardManagerViewModel`이 Activity 수명 유지 | **맞음** | `if (shouldLoadWildcard) viewModel(...)` — Activity scope. Composable 조건만으로는 **clear 안 됨** |
| editor가 `savedText` / `editingText` / `undoStack` 보유 | **맞음** | `WildcardEditorSession` |
| 클립보드 undo가 전체 파일 텍스트 최대 5개 | **맞음** | `WildcardTextEditPolicy.MAX_UNDO_COUNT = 5`, paste 시 `listOf(currentText) + undoStack` |
| 파일 open 시 saved+editing에 본문 유지, 탭 전환만으로 clear 없음 | **맞음** | `editor.clear()`는 삭제/특정 경로 등; **탭 leave 훅 없음** |
| 자동화 prepare가 같은 파일을 다시 읽어 `WildcardSet.items` 생성 | **맞음** | `AutomationRunPreparer` → `wildcardSetRepository.load(tokens)` — **VM editor와 별 객체** |
| 피크 = 편집기 사본 + undo + 자동화 items + 파싱 raw + finalPrompt | **타당** | 중복 보관(동일 파일 내용의 여러 사본) 가능. 무한 누적 아님 |
| `shouldLoadWildcard = false`만으로는 VM clear 부족 | **맞음** | Activity `ViewModelStore`에 이미 생성된 VM은 조건 false만으로 destroy되지 않음 |

#### 피크 구성 (와일드카드 탭 방문 후 자동화)

```text
WildcardManagerViewModel.editor
  · savedText
  · editingText          // 저장 전이면 또 한 벌
  · undoStack[0..4]      // 각 원소 = 당시 전체 파일 텍스트
+ prepare 경로
  · 파일 raw read (일시)
  · WildcardSet.items
  · CompiledPrompt / finalPrompt
```

자동화 탭으로 돌아가도 위 editor 쪽은 **명시 trim 전까지 상주**.

#### 개선안 타당성

| 제안 | 판정 | 비고 |
|------|------|------|
| 탭 leave 시: 저장 완료면 selected text 해제, undo 즉시 해제, 목록 metadata만 유지 | **타당** | UX: 재진입 시 파일 다시 open 필요 — 수용 가능한 tradeoff |
| 미저장이면 editingText만 유지, savedText·undo 축소 | **타당** | dirty 보호와 baseline 절충 |
| 자동화 시작 전 inactive editor 대용량 trim | **타당** | Coordinator/MainVM start 훅. dirty면 trim 범위 정책 필요 |
| **tab별 ViewModelStoreOwner** 로 탭 unload 시 Wildcard VM clear | **타당 (구조적)** | 근본 해결. `shouldLoadWildcard=false`만으로는 불충분하다는 지적 **정확** |

**심각도:** **P1** — 세션 orphan(P0)보다 낮고, “탭 한 번 연 뒤 상시 baseline”. 대용량 txt·undo 5단이면 체감 가능.

**주의:** 자동화와 편집기가 **같은 인메모리 버퍼를 공유하지는 않음**. 문제는 공유가 아니라 **이중 로드 + 편집 스냅샷 상주**.

### 4.8 P2 — 분석 ViewModel이 항상 생성되고 결과가 자동화 중에도 유지됨 (제안 검토)

**판정: 타당.** 자동화 **누적** 데이터는 아니지만, 분석 탭 사용 후 **Activity 수명 baseline**을 높인다. §4.7과 같은 “탭 데이터 상주” 축이며 심각도는 보통 더 낮다(항상 로드되는 점만 와일드카드와 다름).

#### 사실 확인 (코드)

| 주장 | 판정 | 근거 |
|------|------|------|
| 선택 탭과 무관하게 `AnalysisViewModel` 항상 생성 | **맞음** | `AndroidAutomationHost`: `viewModel(factory = analysisViewModelFactory)` — wildcard의 `shouldLoad*` 가드 없음 |
| 항상 `analysisUiState` collect | **맞음** | `analysisViewModel.uiState.collectAsState()` Host 최상단 |
| 큰 데이터: source / hint / candidates / target | **맞음** | `AnalysisUiState` + `sourcePromptTextFieldState` (TextField 본문도 별도) |
| 자동화 실행 누적과 무관 | **맞음** | prepare/run이 Analysis VM을 키우지 않음 |
| 분석 후 자동화 반복 시 baseline 유지 | **맞음** | 탭 전환·자동화만으로 candidates 전량 clear 경로 없음 (일부 액션에서만 `emptyList()`) |

와일드카드와의 차이:

| | 와일드카드 (§4.7) | 분석 (§4.8) |
|--|-------------------|-------------|
| 최초 로드 | lazy (`shouldLoadWildcard`) | **즉시 항상** |
| 대형 데이터 시점 | 탭 연 뒤·파일 open | 앱 기동부터 VM 존재, 생성 후 candidates 등 |
| 자동화 prepare와 이중 로드 | 있음 (파일 재읽기) | 없음 (분석 결과는 자동화와 무관 상주) |

#### 개선안 타당성

| 제안 | 판정 | 비고 |
|------|------|------|
| Analysis ViewModel **lazy** 생성 (첫 ANALYSIS 탭 등) | **타당** | 와일드카드 패턴 정렬. Host 초기 비용↓ |
| tab-scoped ViewModel | **타당 (구조)** | unload 시 clear. `shouldLoad`만 false로는 부족 (§4.7과 동일) |
| generated candidates **clear** UX/API | **타당** | 저비용. 자동화 시작 전 또는 탭 leave 시 호출 가능 |
| inactive tab memory trim | **타당** | source/candidates/segment 정책적 축소; dirty/재사용 UX와 tradeoff |

**심각도:** **P2** — 무한 누적 아님. 후보 리스트가 길면 P1에 가깝게 체감 가능. 세션 orphan(P0)·이벤트(P0)보다 후순위.

### (의도 확인됨) 준비 단계 클립보드 전체 쓰기 — 제거 대상 아님

| | |
|--|--|
| **위치** | `AutomationRunPreparer.prepare` → `clipboardGateway.writeText(request.promptTemplate)` |
| **전송 경로** | `AccessibilityPromptAutomation`은 `ACTION_SET_TEXT`로 `prompt` 직접 설정. 페이스트에 의존하지 않음 |
| **제품 의도** | **의도적 동작** (2026-07-12 확인). 실행 중/직후 수동 붙여넣기·백업 등 사용자 편의용. 코드 주석 참고 |
| **RAM/부작용** | 시스템 클립보드에 긴 템플릿 상주, 기존 클립보드 덮어씀. 앱 힙보다 시스템·UX 쪽 |
| **후속** | **최적화/삭제 후보에서 제외** |

### P1 — 추정: 입력/전송 확인 시 전체 프롬프트 `contains`

| | |
|--|--|
| **위치** | `isPromptTextApplied`, `checkPromptInputAfterSend` |
| **동작** | 입력창 `text.toString()` 후 **전체 `prompt` 문자열** `contains` |
| **비용** | 확인마다 입력창 CharSequence→String 할당 + 긴 문자열 비교. RAM 피크는 일시적, **CPU/할당 빈도** 이슈에 가깝음 |
| **기능** | 반영·전송 확인용. 접두/해시/길이 비교 등으로 완화 가능(후속, 기능 동등성 검증 필요) |

### P2 — 작은 필드·죽은 카운터 (힙 영향 미미)

`CurrentRun` 안에서 **쓰이지만 이후 읽히지 않거나 중복**인 추적값:

| 필드 | 관찰 |
|------|------|
| `startedAtMillis` | 생성 시 저장 후 **미사용** |
| `markerStatus` | `"성공"` 기록만, **미사용** |
| `completedCount` | `successCount`와 함께 증가, **분기 미사용** |
| `failureCount` | 증가만, **분기 미사용** |
| `lastStep` | 기록만, **미사용** |

RAM 자체는 작음. “쓸데없는 동작” 정리 후보(가독성·유지보수).

### P2 — 서비스 상주 이중 Automation 인스턴스

| | |
|--|--|
| **위치** | `GeminiAccessibilityService` lazy `geminiAutomation` + `chatGptAutomation` |
| **관찰** | 한 실행은 한 타겟만 사용. 둘 다 한 번이라도 쓰이면 서비스 생애 동안 공존 가능 |
| **RAM** | 인스턴스·finder 캐시 수준으로 **작음**. 트리 스냅샷이 더 큼 |

### P2 — 마커 프롬프트 1회 (기능 동작)

| | |
|--|--|
| **위치** | `MARKER_PROMPT` + `sendMarker` |
| **관찰** | 매 실행 시작 시 **추가 1회** 전송 루프 전부 수행 |
| **판단** | RAM보다 **시간·외부 앱 호출** 비용. “불필요” 여부는 제품 정책(세션 워밍업) 문제. 본 보고서에서는 **의도적 동작 후보**로만 기록 |

### 범위 밖 (이번 검토에서 제외)

- Gemini/ChatGPT 앱 자체 메모리
- 네트워크·모델 응답 대기
- 분석 탭·와일드카드 편집 UI (실행 루프 아님)
- 메인 화면 타이핑 jank (별도 reduce-ui-jank)

---

## 5. 시나리오 ↔ 코드 연결

### 5.1 반복 100회 · 긴 프롬프트

1. prepare: 템플릿 Prefs + 클립보드 + 와일드카드 리스트 + `CompiledPrompt`  
2. 매 회: `promptPlan.generate` → `GeneratedPrompt`(base+final+replacements) 생성 후 **`finalPrompt`만** 사용 (§4.2)  
3. 매 회·단계마다: 입력/보내기/새 채팅 탐색 → **전체 트리 flatten** (32ms 창 밖이면 재구축)  
4. 회차 사이·재시도 사이에도 finder 필드의 **마지막 `cachedNodes`는 서비스 생애 동안 잔류**할 수 있음  
5. 종료: `currentRun` 해제, 바 hide. **접근성 서비스·lazy automation·finder 캐시는 서비스가 살아있는 한 남을 수 있음**

**부담 추정 구간:** 트리 순회 할당 피크 + 장기 스냅샷 보유 + 현재 `finalPrompt`/Handler 참조.  
`lastPrompt` state 필드는 §4.0 기준 부가 이슈.

### 5.2 재시도가 많은 불안정 UI

- 250ms / 1s 지연 → **항상 32ms 캐시 창 밖** → 사실상 **재시도마다 전체 순회**  
- 순회 사이에 화면이 바뀌면, 이론상 새 root로 새 리스트를 만들지만, 교체 전·조건 불일치 시 **stale 노드** 위험  
- step 변경 → 바 recomposition (lastPrompt 제거로 거의 안 줄어듦)

**할당 반복 + stale 가능성 ↑** → RAM만이 아니라 **전송 안정성** 축에서도 우선 대상

### 5.3 와일드카드 대용량 txt · 탭 상주 (§4.7)

- 토큰별 `items: List<String>` 전부 메모리 상주 (랜덤 선택용, prepare 시) — 기능상 필요에 가까움  
- **추가:** 와일드카드 탭을 한 번이라도 열면 editor `savedText`/`editingText`/undo≤5가 Activity 수명 상주 → 이후 자동화마다 **baseline↑**  
- 탭 leave trim 또는 tab-scoped ViewModelStore로 완화 가능  

### 5.4 접근성 ON · 장시간

1. 서비스 연결·idle: `eventTypes = 0`, `packageNames = null`  
2. 프롬프트 전송 중: 대상 앱 package만 (Gemini 계열 / ChatGPT)  
3. 최근 앱 닫기 직전: `packageNames` 해제 (시스템 UI 조회)  


### 5.5 실행 중 ViewModelStore clear 후 재실행 (조건부 orphan · lifecycle race)

1. 실행 A: Handler callback이 prompt/run/onStateChange 캡처  
2. **`moveTaskToBack`만** → VM 유지, 이 절 해당 없음  
3. **task 제거 / finish 등으로 store clear** — cancel 없음 → A callback 잔존 가능  
4. 재실행: `useCaseB`, `currentRun == null` → B 시작 가능  
5. A·B Handler 교차 → **기능 경합 (P0)** + **조건부** 참조 중첩  
6. A/B가 각각 정상 `finishRun`하면 그쪽 참조는 풀릴 수 있음 (영구 누수 단정 금지)  

### 5.6 접근성 서비스 중단 / 전역 cancel (§4.5)

**서비스 끄기·interrupt 중 자동화 중:**

1. `removeCallbacksAndMessages(null)` → 큐 비움  
2. `finishRun` 없음 → `currentRun`·IME·UI Running·대형 데이터 잔존  
3. 같은 VM: 재시작 불가 / 새 VM: §4.4와 결합  

**겹친 실행 중 한쪽 UI 취소:**

1. `cancelCurrentRun`이 **전체** 큐 삭제  
2. 취소한 쪽만 `finishRun`  
3. 다른 쪽은 callback 소멸 + `currentRun` 좀비  

---

## 6. “불필요한 동작” 체크리스트 (기능 유지 전제)

| # | 동작 | 자동화 전송에 필수? | RAM/중복 | 비고 |
|---|------|---------------------|----------|------|
| 0 | 프로세스 단일 자동화 엔진 | Phase1 적용 | 배타성·runState 공유 | §3. `ProcessAutomationHolder` |
| 0a | ViewModelStore clear 후 재실행 겹침 | 완화(단일 엔진) | 잔여: Handler token 없음 | §4.4 잔여 → Phase2 token |
| 0b | 서비스 중단 시 finish 없음 | Phase1 적용 | `onAccessibilityLost` | §3 |
| 0c | **`cancelCurrentRun` = Handler 전량 삭제** | 부분 | 타 작업·closeGemini 지연 손상 | **P0 잔여**. §4.5-B |
| 1 | `lastPrompt`를 매 Running에 실음 | 아니오 (UI 미표시) | **낮음** (참조 공유; 내용 복제 아님) | **P2** |
| 2 | 트리 전체 flatten + 32ms 재사용 캐시 | 탐색은 필요, 방식 개선 여지 | 높음(추정) | **P0** 실행 중. recycle 누수 아님 |
| 3 | 접근성 이벤트·package 범위 | 폴링 전송만 필수 | 완화됨 | §3. 전송 중 package 제한 + 닫기 시 해제 |
| 4 | prepare 시 클립보드에 템플릿 기록 | 전송엔 불필요, **제품 의도(편의)** | 중(시스템) | **유지** (의도 확인됨) |
| 5 | 템플릿 다중 필드 중복 참조 | 일부 필요 | 중 | 참조 공유로 완화 가능 |
| 6 | 회차마다 `GeneratedPrompt` + `replacements` Map | 자동화는 **final만** 필요 | 임시 할당 (누적 아님) | **§4.2** 경량 API 타당 |
| 6b | **와일드카드 탭 후 editor·undo Activity 상주** | 편집 UX용; 자동화엔 불필요 중복 | baseline↑ (무한 증가 아님) | **P1**. §4.7. `shouldLoad=false`만으론 clear 안 됨 |
| 6c | **AnalysisViewModel 항상 생성·결과 상주** | 분석 UX; 자동화와 무관 | baseline↑ (누적 아님) | **P2**. §4.8. lazy/tab-scope/clear 후보 |
| 7 | 입력 확인 full `contains` | 확인 로직 필요 | 할당 빈도 | 비교 방식 최적화 여지 |
| 8 | CurrentRun 미사용 카운터 | 아니오 | 매우 낮음 | 정리용 |
| 9 | 마커 1회 전송 | 제품 의도 가능 | 낮음(문자 짧음) | 시간 비용 위주 |
| 10 | main uiState coarse Running | 이미 완화 | - | 유지 권장 |
| 11 | 회차마다 프롬프트 **문자열** 생성 | 예 | 필요 (`finalPrompt`) | 유지. wrapper만 경량화 |

---

## 7. 후속 개선 시 가이드 (구현하지 않음)

기능 동등성을 깨지 않는 방향만 적습니다.

**0순위 — 세션 잔여 (§4.5-B · Phase2)**

1. ~~정책 A + 프로세스 단일 엔진 + runState + 서비스 끊김 finish~~ **Phase1 적용**  
2. **Handler token per session** + stale session ID no-op (`removeCallbacksAndMessages(null)` 폐기)  
3. (중기) suspend + **단일 Job** structured concurrency  

**1순위 — 실행 중 트리 탐색·캐시 (§4.1)**

4. flatten 축소 · 스냅샷 참조 수명 · stale 방지  

**2순위 — 와일드카드 탭 baseline (§4.7, P1)**

5. 탭 leave / 자동화 시작 전 editor·undo trim (저장·dirty 정책 분기)  
6. (구조) tab-scoped `ViewModelStoreOwner` 로 Wildcard VM unload  

**3순위 — 분석 탭 baseline (§4.8, P2)**

7. Analysis VM lazy · candidates clear · inactive trim  

**4순위 — 회차 할당 (§4.2) · 필드 정리 (P2)**

8. `generateFinalPrompt` · `lastPrompt` UI 제거 · 미사용 CurrentRun 필드  

**유지**

9. prepare 클립보드 템플릿 쓰기 (의도)  

레이어: domain/usecase 순수성 유지. Accessibility 탐색·Handler는 `automation/android`에 한정.

---

## 8. 한계 (이 보고서가 말하지 않는 것)

- 실기기 **MB 전후 비교**, LeakCanary, Android Profiler 결과 없음  
- “X MB 낭비” 같은 **정량 단정 금지**  
- data class / StateFlow에 대한 **내용 복제 과장**은 §4.0에서 수정함  
- **32ms 캐시 = 자동 메모리 해제** 오해는 §4.1에서 수정함  
- **`recycle()` 미호출 = 네이티브 누수** 오해는 §4.1.1에서 수정함 (API 33+ deprecated·pooling 폐지·무효과)  
- 외부 앱 OOM·접근성 프레임워크 내부 비용은 앱 코드만으로 분리 불가  
- 기능 삭제를 권고하지 않음. 데드 필드·**탐색/캐시 방식**만 구분  

---

## 9. 결론

| 등급 | 후보 |
|------|------|
| **P0 세션 잔여** | Handler token / 전역 cancel 부작용 (§4.5-B). Phase1: 단일 엔진·runState·서비스 끊김 finish **적용** |
| **P0 실행 중** | 트리 flatten · 스냅샷 잔류 · stale (§4.1) |
| **P1 baseline** | 와일드카드 탭 상주 editor/undo + prepare 재로드 (§4.7) |
| **P2 baseline** | AnalysisViewModel 항상 생성·candidates 등 상주 (§4.8) |
| **P2 필드** | `generateFinalPrompt` (§4.2) · `lastPrompt` UI (§4.0) |
| **유지** | 폴링 전송·SET_TEXT, 클립보드 템플릿 쓰기(의도), 기존 `GeneratedPrompt` API |

본 문서는 검토 결과 기록이며, **앱 동작 변경 코드는 포함하지 않습니다.**
