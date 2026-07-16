# 코드만으로 jank 판단하기 — gemgemgen

실기기·adb·프레임 숫자 없이, 소스 경로로 우선순위를 매길 때 쓴다.

## 1. 핫패스 추적

시나리오 한 문장 → 진입 Composable/ViewModel 메서드 → 상태 갱신 → 다시 그려질 자식.

질문:

1. 이 동작마다 **어떤 `StateFlow` / `mutableState` / `TextFieldState`가 바뀌는가?**  
2. 그 구독자가 **Host 전체인가, 한 탭인가, 한 위젯인가?**  
3. 갱신 때 **긴 문자열·전체 리스트·파일 파싱**이 메인에서 도는가?  
4. `withContext(AppDispatchers.io)` / 백그라운드 경계가 있는가?

## 2. 심각도 휴리스틱

| 등급 | 코드 패턴 예 (이 프로젝트) |
|------|---------------------------|
| P0 | 메인 스레드 동기 파일/Prefs `commit`, 입력 1회마다 대형 Screen 전체 파라미터 재생성, Host에서 고빈도 Flow를 전 탭이 공유 collect |
| P1 | 단락 하이라이트/transformation이 범위 변경마다 통째 재생성, 플로팅 바 state가 불필요 필드로 자주 `update`, 목록 전체 remap |
| P2 | 가독성·구조상 아쉽지만 핫패스 연결이 약함, 저빈도 설정 화면만 해당 |

프레임 시간을 지어내지 말 것.  
“P0 = 코드상 메인 비용·갱신 범위가 분명히 큼” 정도로 말한다.

## 3. 이미 괜찮은 신호 (감점 금지)

- `MainTabbedScreen`이 선택 탭만 content 호출  
- 와일드카드 VM 지연 생성 (`shouldLoadWildcard`)  
- use case의 `withContext(dispatchers.io)`  
- `AppMultilineTextField`의 `debounce` + `distinctUntilChanged`  
- 텍스트 입력 경로에서 `updateTextFieldState = false`로 이중 갱신 완화

분석 때 “이미 잘 된 부분”에 적는다.

## 4. 수정 효과 서술 (코드 관점)

좋은 예:

- “`AndroidAutomationHost`에서 `automationBarUiState` collect를 플로팅 바 쪽으로만 옮겨, 실행 중 진행 갱신이 자동화 탭 트리를 다시 안 타게 함.”  
- “`promptTemplate` 전체 copy 대신 단락 범위만 구독하는 하위 컴포저블로 하이라이트 무효화 범위를 줄임.”

나쁜 예:

- “janky 30% → 5% 될 것” (측정 없음)  
- “체감 두 배 부드러움” (단정 금지)

## 5. 수정 후 자가 점검 (실행 없이)

- [ ] 의도한 갱신 경로만 줄였는가?  
- [ ] domain/usecase에 UI/프레임워크 타입이 들어가지 않았는가?  
- [ ] 고르지 않은 안(C 등)의 대규모 리팩터를 섞지 않았는가?  
- [ ] API 키·경로 비밀이 로그에 안 남는가?  

사용자가 나중에 체감할 때 볼 동작만 한두 줄로 안내한다. 에이전트가 기기에서 돌리지 않는다.
