# 핸드오프 체크리스트 A: 되묻기(clarificationQuestion) 다이얼로그

> 이 문서만 읽고 작업을 시작할 수 있도록 작성했습니다. 프로젝트 루트의 `AGENTS.md`를 먼저 읽고 따르세요. UI 작업이므로 `docs/디자인.md`와 `docs/flicker-free-ui-guidelines.md`도 먼저 확인하세요.

## 0. 배경과 목표
- 대상: **분석 생성 탭**(`app/src/main/java/com/example/gemgemgen/analysis/`)만. 자동화 변주(`automation/`)는 수정하지 않습니다.
- 현재 동작: 1단계 분석 응답의 `clarificationQuestion`이 비어 있지 않으면 `ResolveAnalysisTargetUseCase.requireNoClarification`이 예외(`"추가 요구사항에 답을 적어 주세요: ..."`)를 던지고, 화면에는 오류 문구만 보입니다. 질문에 답하는 전용 UI가 없습니다.
- 목표: 질문이 오면 **다이얼로그**로 질문과 답변 입력창을 띄우고, 사용자가 답하면 그 답변을 사용자 지시에 이어붙여 **재분석 후 이어서 생성**합니다.
- 확정된 결정(변경 금지): 방식은 다이얼로그(A). 답변은 사용자 지시에 합쳐서 전달. 칩과 추가 요청은 같은 비중.

## 1. 반드시 먼저 읽을 파일
- [ ] `analysis/usecase/ResolveAnalysisTargetUseCase.kt` (`requireNoClarification`, 약 59행·163행)
- [ ] `analysis/usecase/ExecuteAnalysisGenerationUseCase.kt` (결과 타입 `ExecuteAnalysisGenerationResult`)
- [ ] `analysis/ui/AnalysisViewModel.kt` (`handleGenerationResult`, `createGenerationRequest`, `customHint` 처리)
- [ ] `analysis/ui/AnalysisDialogHost.kt` (`AnalysisDialogType`, `deriveActiveAnalysisDialog`, 이미 있는 `AddDirectionContent` 참고)
- [ ] `analysis/ui/AnalysisUiState.kt`, `analysis/ui/AnalysisScreenActions.kt`
- [ ] `analysis/domain/AnalysisPromptBuilder.kt` (`formatUserDirective`, 사용자 지시 블록)
- [ ] `analysis/domain/AnalysisModels.kt` (`AnalysisReport.clarificationQuestion`)
- [ ] `docs/flicker-free-ui-guidelines.md` (Single Dialog Host 규칙)

## 2. 설계 지침
- [ ] 예외로 던지는 대신 **결과 타입**으로 되묻기를 전달합니다. 예: `ExecuteAnalysisGenerationResult.NeedsClarification(question: String)`. 기존 `Failure`로 섞지 마세요.
- [ ] 다이얼로그는 기존 **Single Dialog Host**에 새 타입으로 추가합니다(`AnalysisDialogType.Clarification(question)`). 별도 `Dialog`를 따로 띄우지 마세요(깜빡임 방지 규칙).
- [ ] 답변 입력창은 한 줄 이상 가능한 텍스트 필드. 빈 답변은 확정 불가.
- [ ] 답변을 보내면 `customHint`(추가 요청) 뒤에 이어붙인 **새 사용자 지시**로 분석을 다시 실행하고, 성공하면 이어서 생성까지 진행합니다.
- [ ] 취소 시 아무것도 바꾸지 않고 다이얼로그만 닫습니다. 사용자가 입력해 둔 추가 요청과 칩 선택은 유지합니다.
- [ ] 같은 질문이 반복되는 무한 루프를 막습니다(재분석 후에도 질문이 나오면 한 번 더 보여주되, 연속 횟수 한도를 두고 한도를 넘으면 오류 문구로 안내).
- [ ] 답변 길이 한도는 기존 추가 요청 한도(100자)를 참고하되, 합쳐진 지시가 한도를 넘을 때의 처리(잘라내기 금지, 사용자에게 안내)를 정하고 문서에 남기세요.
- [ ] 분석 캐시(`analysisCache`)는 답변이 들어오면 무효화되어야 합니다(지시가 바뀌었으므로).
- [ ] 불필요한 하위 호환 레이어를 남기지 마세요(`AGENTS.md` 2장).

## 3. 구현 체크리스트
- [ ] `ExecuteAnalysisGenerationResult`에 되묻기 결과 추가, `ResolveAnalysisTargetUseCase`에서 예외 대신 되묻기를 전달하도록 변경
- [ ] `AnalysisUiState`에 되묻기 질문과 답변 입력 상태 추가
- [ ] `AnalysisScreenActions`에 되묻기 제출·취소 액션 추가
- [ ] `AnalysisViewModel`에서 되묻기 결과 처리, 제출 시 재분석·재생성 흐름 구현
- [ ] `AnalysisDialogHost`에 되묻기 다이얼로그 추가(`deriveActiveAnalysisDialog` 우선순위 포함)
- [ ] 변경한 파일 **첫 줄 `// 역할: ...` 주석**을 최신 상태로 수정, 새 파일은 첫 줄에 작성

## 4. 테스트 체크리스트 (타겟 테스트만)
- [ ] 테스트 더블을 만들기 전에 대상 인터페이스·데이터 클래스 소스를 직접 열어 시그니처를 확인
- [ ] 되묻기 결과가 오면 다이얼로그 상태가 열리는지(ViewModel 테스트, `AnalysisFeatureTest.kt` 참고)
- [ ] 답변 제출 시 사용자 지시에 답변이 합쳐져 재분석되는지
- [ ] 취소 시 입력값이 유지되는지
- [ ] 연속 되묻기 한도 동작
- [ ] 기존 테스트 중 `"추가 요구사항에 답을 적어 주세요"` 문구에 의존하는 테스트 갱신
- [ ] 실행 명령: `.\gradlew testDebugUnitTest --tests "com.example.gemgemgen.Analysis*"` (전체 테스트 금지)
- [ ] 빌드·테스트는 **한 번에 하나만** 실행(동시 실행 금지)

## 5. 사용자 확인 체크리스트 (완료 보고에 포함)
- [ ] 모호한 요청으로 생성했을 때 질문 다이얼로그가 뜬다
- [ ] 답변 후 생성이 이어진다
- [ ] 취소하면 입력해 둔 칩과 추가 요청이 그대로다
- [ ] 다이얼로그가 깜빡이지 않고, 키보드가 입력창을 가리지 않는다

## 6. 완료 보고 형식 (`AGENTS.md` 6장)
- [ ] 한국어, 비전공자도 이해할 수 있게 설명
- [ ] 변경 파일 표(프로덕션/테스트 분리, 클릭 가능한 링크, 신규/수정/삭제, 라인 수 증감, 핵심 변경)와 총 파일 수·순증감 요약

## 7. 하지 말 것
- 자동화 변주(`VariationPromptConfig` 등)와 공통 시각 규칙 12개의 의미 변경
- 요청과 무관한 리팩터링·포맷 변경
- 전체 단위 테스트(`testDebugUnitTest` 전체) 실행
