# 자동화 실행 경로 RAM·불필요 동작 검토 보고서

| 항목 | 내용 |
|------|------|
| 작성일 | 2026-07-12 |
| 범위 | 자동화 **실행 과정** (`RunAutomationUseCase` → prepare → IME → 대상 앱 → Accessibility 전송 루프 → 플로팅 바 state) |
| 방법 | 소스 코드 경로 추적만 (실기기 힙 덤프·Profiler 측정 **없음**) |
| 전제 | **기능 변경 없음**. 수정 제안은 후속 작업용 참고이며 본 문서는 구현하지 않음 |
| 판단 표시 | **코드상 유력** / **추정** / **의도적·필요** 를 구분 |

---

## 1. 한 줄 요약

자동화 중 RAM을 가장 불필요하게 키울 후보는  
**(1) UI에 쓰이지 않는 `lastPrompt`가 매 단계 state에 실려 복제되는 경로**,  
**(2) Accessibility 트리 전체 스냅샷을 자주 만들고 노드를 캐시·미회수하는 경로**,  
**(3) 같은 원본 프롬프트 문자열이 런타임 객체에 여러 겹으로 붙는 구조** 입니다.

기능상 필수인 부분(와일드카드 로드, 1회 1프롬프트 생성, SET_TEXT 전송, 재시도)은 별도로 표시했습니다.

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
       · automationBarUiState: 세부 step 전부 반영            ← lastPrompt 포함 가능
  → FloatingAutomationBar / AutomationActionBar (표시는 step·index 위주)
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

---

## 4. RAM·불필요 동작 후보 (우선순위)

심각도는 **앱 힙 / 네이티브 부담 가능성과 중복 정도** 기준입니다.  
프레임 시간·MB 숫자는 측정하지 않았습니다.

### P0 — 코드상 유력: UI 미사용 `lastPrompt`의 고빈도 복제

| | |
|--|--|
| **위치** | `AutomationRunState.Running.lastPrompt`, `RunAutomationUseCase.updateRunState`, `CurrentRun.lastPrompt` |
| **동작** | 매 step 갱신 시 `Running(step, index, total, lastPrompt = run.lastPrompt)` 생성 후 `automationBarUiState`에 전달 |
| **표시 여부** | `AutomationUiText.statusText` / `AutomationActionBar`는 **`step`·index만 사용**. `lastPrompt`를 그리는 Composable **없음** (테스트 fixture에서만 등장) |
| **RAM 관점** | 프롬프트가 길수록 `Running` data class·StateFlow 값마다 **동일 대형 문자열이 논리적으로 계속 실림**. step만 바뀌어도 이전/이후 state에 긴 문자열이 묶임 |
| **기능 영향** | 현재 UI 기능에는 미연결. 제거·분리해도 **표시 기능 변화 없음**(후속 시 테스트 fixture만 조정) |
| **추정 체감** | 짧은 프롬프트: 작음. 수 KB~ 긴 템플릿 + 잦은 step: 할당·GC·바 리컴포즈 부담 **중** |

### P0 — 코드상 유력: Accessibility 트리 전체 flatten + 노드 캐시·미회수

| | |
|--|--|
| **위치** | `GeminiAccessibilityNodeFinder.flattenNodes` / `nodes()`, `ChatGptAccessibilityNodeFinder.nodes()` |
| **동작** | `rootInActiveWindow` 기준 **패키지 필터된 전체 노드 리스트** 구축 → 최대 32ms `cachedNodes` 보관. 입력/보내기/메뉴 탐색·재시도마다 호출 |
| **재시도 빈도** | `AutomationRetryWaitPolicy`: 초반 250ms 간격, 이후 1s, 단계별 최대 약 10s 창 |
| **RAM/네이티브** | `AccessibilityNodeInfo`는 네이티브 쪽 리소스. 코드에 **`recycle()` / 캐시 교체 시 정리 호출 없음**. `getChild`·`parent` 탐색(`findClickableNodeOrParent`)도 추가 참조 |
| **추정** | 긴 자동화 + Gemini/ChatGPT UI 복잡도 ↑ 시 **단기 리스트 할당 + 네이티브 압력**이 가장 큰 후보. 확정 MB는 프로파일 필요 |
| **기능** | 트리 탐색 자체는 전송에 **필요**. “전체 스냅샷 + 장기 보유 방식”은 최적화 여지 |

### P1 — 코드상 유력: 원본 프롬프트 문자열 다중 보유

실행 중 동시에 붙을 수 있는 동일·유사 문자열:

| 보유처 | 필드/경로 |
|--------|-----------|
| 요청 | `AutomationRunRequest.promptTemplate` (`PreparedAutomationRun`이 참조) |
| 런 | `CurrentRun.promptTemplate` |
| 컴파일 결과 | `PromptGenerator.CompiledPrompt` 내부 `basePrompt` |
| 생성 1회 | `GeneratedPrompt.basePrompt` + `finalPrompt` (매 회차 임시) |
| 런 추적 | `CurrentRun.lastPrompt` (= 직전 `finalPrompt`) |
| UI state | `Running.lastPrompt` (위 P0) |
| 메인 VM (앱 생존) | `MainUiState.promptTemplate` / TextField / `promptTemplateValue` |
| 준비 단계 부수 | Prefs 스냅샷, **클립보드**에 템플릿 복사 |

Kotlin에서 동일 `String` 참조를 공유하면 힙은 한 번일 수 있으나,  
`generate`의 `replace` 결과·`Running` 복사·클립보드·Prefs는 **별도 버퍼**가 생깁니다.

**기능:** 템플릿·생성 결과 자체는 필요. **중복 보관 레이어 수**는 줄일 여지가 있음.

### P1 — 추정: 준비 단계 클립보드 전체 쓰기 (자동화 입력 경로와 무관)

| | |
|--|--|
| **위치** | `AutomationRunPreparer.prepare` → `clipboardGateway.writeText(request.promptTemplate)` |
| **전송 경로** | `AccessibilityPromptAutomation`은 `ACTION_SET_TEXT`로 `prompt` 직접 설정. **페이스트/클립보드 읽기 없음** |
| **의미** | 실행 파이프라인상 **전송 필수 동작으로 보이지 않음**. 사용자 편의·백업·과거 설계 잔존 가능 |
| **RAM/부작용** | 시스템 클립보드에 긴 템플릿 상주, 기존 클립보드 덮어씀. 앱 힙보다 **시스템·UX 부작용**에 가까움 |
| **주의** | “불필요” 단정 전, 의도 문서/이슈 확인 권장. 제거는 **기능 정책 결정** 후 |

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
2. 매 회: `finalPrompt` 생성 → `lastPrompt` 갱신 → 다수 `Running(..., lastPrompt)` → 바 StateFlow  
3. 매 회: 새 채팅 UI 탐색 시 트리 flatten 반복  
4. 종료: `currentRun` 해제, 바 hide. 메인 VM·클립보드·Prefs 템플릿은 남을 수 있음  

**RAM 피크 추정 구간:** 회차 중 `lastPrompt`+트리 스냅샷+`finalPrompt` 동시 존재.

### 5.2 재시도가 많은 불안정 UI

- 250ms~1s 간격으로 `findInputNode` / `findSendNode` / 메뉴 탐색  
- 캐시 32ms 밖이면 **매번 전체 노드 리스트 재구축**  
- step 문자열만 바뀌어도 `lastPrompt` 포함 state 갱신 가능  

**네이티브·할당 압력 추정 ↑**

### 5.3 와일드카드 대용량 txt

- 토큰별 `items: List<String>` 전부 메모리 상주 (랜덤 선택용)  
- **기능상 필요**에 가깝음. 스트리밍/샘플링은 큰 설계 변경  

---

## 6. “불필요한 동작” 체크리스트 (기능 유지 전제)

| # | 동작 | 자동화 전송에 필수? | RAM/중복 | 비고 |
|---|------|---------------------|----------|------|
| 1 | `lastPrompt`를 매 Running에 실음 | 아니오 (UI 미표시) | 높음(긴 문자열) | 최우선 정리 후보 |
| 2 | 트리 전체 flatten + 미 recycle 캐시 | 탐색은 필요, 방식은 개선 여지 | 높음(추정) | 네이티브 리스크 |
| 3 | prepare 시 클립보드에 템플릿 기록 | 전송 경로상 아니오 | 중(시스템) | 정책 확인 후 |
| 4 | 템플릿 다중 필드 중복 참조 | 일부 필요 | 중 | 참조 공유로 완화 가능 |
| 5 | `GeneratedPrompt.basePrompt` 매 회 포함 | 생성 API 형태 | 낮~중(임시) | final만 써도 됨 |
| 6 | 입력 확인 full `contains` | 확인 로직 필요 | 할당 빈도 | 비교 방식 최적화 여지 |
| 7 | CurrentRun 미사용 카운터 | 아니오 | 매우 낮음 | 정리용 |
| 8 | 마커 1회 전송 | 제품 의도 가능 | 낮음(문자 짧음) | 시간 비용 위주 |
| 9 | main uiState coarse Running | 이미 완화 | - | 유지 권장 |
| 10 | 회차마다 프롬프트 생성 | 예 | 필요 | 유지 |

---

## 7. 후속 개선 시 가이드 (구현하지 않음)

기능 동등성을 깨지 않는 방향만 적습니다. **우선순위는 RAM 중심.**

1. **`lastPrompt`를 UI state에서 분리 또는 제거**  
   - 바/메인에는 `step`, `currentIndex`, `totalCount`만  
   - 디버그가 필요하면 로그/별도 디버그 플래그  
2. **Accessibility 노드 수명 관리**  
   - 스냅샷 교체 시 이전 리스트 정리 정책 검토 (플랫폼 버전별 `recycle` 권장 여부 확인)  
   - 가능하면 viewId·단일 조회로 전체 flatten 횟수 축소  
3. **prepare 클립보드 쓰기 의도 확인 후 제거 검토**  
4. **런타임 템플릿 단일 소스**  
   - `CurrentRun`이 `promptPlan`만 갖고 템플릿 필드 중복 제거 등  
5. **미사용 CurrentRun 필드 정리** (가독성)

레이어: domain/usecase 순수성 유지. Accessibility recycle·Handler는 `automation/android`에 한정.

---

## 8. 한계 (이 보고서가 말하지 않는 것)

- 실기기 **MB 전후 비교**, LeakCanary, Android Profiler 결과 없음  
- “X MB 낭비” 같은 **정량 단정 금지**  
- 외부 앱 OOM·접근성 프레임워크 내부 비용은 앱 코드만으로 분리 불가  
- 기능 삭제를 권고하지 않음. **중복·미사용 전파**만 지적  

---

## 9. 결론

| 등급 | 후보 |
|------|------|
| **먼저 볼 것 (RAM)** | UI 미사용 `lastPrompt` 고빈도 state 전파 · Accessibility 전체 트리 스냅샷/캐시 |
| **정책 확인 후** | prepare 클립보드 템플릿 쓰기 |
| **구조 정리** | 템플릿 다중 보유 · 미사용 CurrentRun 필드 |
| **유지** | 회차별 생성, SET_TEXT 전송, wildcard 토큰 필터 로드, main Running coarse, 드래그 스로틀 |

본 문서는 검토 결과 기록이며, **앱 동작 변경 코드는 포함하지 않습니다.**
