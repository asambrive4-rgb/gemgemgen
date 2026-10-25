# 핸드오프 체크리스트 B: 분석 파이프라인 속도 개선

> 이 문서만 읽고 작업을 시작할 수 있도록 작성했습니다. 프로젝트 루트의 `AGENTS.md`를 먼저 읽고 따르세요. 코드 정리·단순화 성격이 있으므로 `.agents/skills/code-simplification/SKILL.md`도 확인하세요.

## 0. 배경과 목표
- 대상: **분석 생성 탭**(`app/src/main/java/com/example/gemgemgen/analysis/`)만. 자동화 변주는 수정하지 않습니다.
- 현재 흐름: ① 분석 호출(대상 구간 탐색, 시각 맥락, 연쇄 점검 `cascadingTrace`, `variationGoal`) → ② 생성 호출(후보 N개). 두 호출 모두 프롬프트가 길고, 분석 JSON 스키마가 큽니다.
- 목표: **결과 품질은 유지하거나 향상**시키면서 **응답 시간을 단축**합니다.
- 확정된 결정(변경 금지)
  - 분석 → 생성 2단계 구조 유지, 분석 호출 유지
  - JSON 스키마 방식과 한국어 유지
  - FREE_EDIT(`edits` 배열)와 일반 카테고리(와일드카드 조각) 출력 방식 둘 다 유지
  - 공통 시각 규칙 12개(원문 보존, 연쇄 점검, 감정 라벨 금지, 임의 카메라 제품명 금지 등)의 **의미는 유지**
  - 연쇄 점검 5개 항목(`newlyVisible`, `disappearing`, `undefinedAreas`, `requiredAdjustments`, `conflictingSegments`)의 효과 유지 또는 향상
  - 공통 규칙 2번의 예시 3개(더 압도적으로, 평면감 축소, 더 가까운 느낌)는 **그대로 둡니다**
  - 속도 개선 허용 범위: 프롬프트·스키마 압축, 분석·생성 호출의 병렬화·축소

## 1. 절대 금지 (품질 가드레일)
- [ ] **출력 프롬프트 길이를 속도를 이유로 줄이지 않습니다.** 길이 언급이 없으면 마스킹된 원문 구간과 비슷한 길이를 유지합니다(현재 `AnalysisPromptBuilder.lengthPolicy`). 속도 개선은 입력 프롬프트·응답 구조·호출 구조에서만 얻습니다.
- [ ] 후보 개수(`count`)를 줄여서 속도를 얻지 않습니다.
- [ ] 12개 규칙의 의미를 바꾸거나 삭제하지 않습니다(문장 압축은 허용, 의미 손실은 불가).

## 2. 반드시 먼저 읽을 파일
- [ ] `analysis/domain/AnalysisPromptBuilder.kt` (시스템 지시·스키마)
- [ ] `analysis/usecase/ExecuteAnalysisGenerationUseCase.kt` (분석→생성 흐름)
- [ ] `analysis/usecase/ResolveAnalysisTargetUseCase.kt` (분석 호출, 캐시)
- [ ] `analysis/usecase/GenerateAnalysisTxtUseCase.kt` (생성 호출)
- [ ] `analysis/domain/AnalysisResponseParser.kt`, `AnalysisMaskingPolicy.kt` (캐시 유효성 판정)
- [ ] `analysis/android/AndroidGeminiAnalysisGateway.kt`, `AndroidGrokAnalysisGateway.kt` (네트워크, 타임아웃)
- [ ] `analysis/domain/AnalysisCategory.kt` (카테고리 규칙, 프롬프트 길이에 영향)

## 3. 작업 순서 (반드시 이 순서)
### 3-1. 기준 측정 (변경 전)
- [ ] 같은 원문·카테고리·칩으로 분석 호출 시간, 생성 호출 시간, 입력 토큰 수(프롬프트 글자 수로 대체 가능)를 기록
- [ ] 대표 케이스 3개 이상: FREE_EDIT 1개, 일반 카테고리 2개
- [ ] 변경 전 출력 샘플(분석 JSON, 후보)을 저장해 두고 변경 후와 **품질 비교**

### 3-2. 프롬프트·스키마 압축
- [ ] 공통 규칙 12개와 `lengthPolicy`, 카테고리 규칙에서 **중복 문장**을 찾아 압축(의미 유지)
- [ ] 스키마 필드 `description`을 짧게. 단, `cascadingTrace` 5개 필드와 `conflictingSegments`는 유지
- [ ] 생성 호출에 넘기는 분석 결과는 필요한 항목만 전달(예: 사용하지 않는 `visibleElements` 등 점검 후 제거는 **실제로 쓰이지 않는지 확인한 뒤에만**)
- [ ] 압축 전후 프롬프트 글자 수 비교를 기록

### 3-3. 호출 구조 개선
- [ ] 독립적으로 병렬 가능한 준비 작업이 있는지 조사(대상 구간 탐색, 캐시 판정 등). 분석 결과가 생성의 입력이므로 두 호출 자체는 순차임을 전제로 합니다.
- [ ] 불필요한 재분석 방지: `AnalysisMaskingPolicy.shouldAnalyzeMasking`이 지시·원문·카테고리 변경 시에만 재분석하는지 확인하고, 불필요한 무효화(`analysisCache = null`) 경로가 있으면 보고
- [ ] 스트리밍·타임아웃·재시도 설정 중 속도에 영향을 주는 부분 조사

### 3-4. 검증
- [ ] 변경 후 같은 케이스를 같은 조건으로 재측정, 표로 비교
- [ ] 변경 후 출력 품질을 변경 전 샘플과 비교(길이 유지, 연쇄 수정 누락 여부, 규칙 위반 여부)
- [ ] 품질 저하가 보이면 압축을 되돌립니다

## 4. 테스트 체크리스트 (타겟 테스트만)
- [ ] 테스트 더블을 만들기 전에 대상 인터페이스·데이터 클래스 소스를 직접 열어 시그니처를 확인
- [ ] 프롬프트 문자열을 검사하는 기존 테스트가 있다면(`AnalysisFeatureTest.kt`, `AnalysisPrecisionEditingTest.kt`) 의미가 같은지 확인하며 갱신
- [ ] `lengthPolicy`가 분석·생성 프롬프트 양쪽에 남아 있는지 검사하는 테스트 유지
- [ ] 실행 명령: `.\gradlew testDebugUnitTest --tests "com.example.gemgemgen.Analysis*"` (전체 테스트 금지)
- [ ] 빌드·테스트는 **한 번에 하나만** 실행(동시 실행 금지). 충돌 시 `AGENTS.md` 5장 복구 프로토콜을 따릅니다

## 5. 사용자 확인 체크리스트 (완료 보고에 포함)
- [ ] 전후 응답 시간 비교 표
- [ ] 결과 길이가 원문 구간과 비슷하다
- [ ] 연쇄 수정(예: 클로즈업으로 바꿀 때 신발 문장 삭제)이 이전처럼 동작한다
- [ ] FREE_EDIT와 일반 카테고리 모두 정상 동작한다

## 6. 완료 보고 형식 (`AGENTS.md` 6장)
- [ ] 한국어, 비전공자도 이해할 수 있게 설명
- [ ] 측정 표(전/후), 변경 파일 표(프로덕션/테스트 분리, 클릭 가능한 링크, 신규/수정/삭제, 라인 수 증감, 핵심 변경), 총 파일 수·순증감 요약
- [ ] 변경한 파일의 첫 줄 `// 역할: ...` 주석 최신화 여부

## 7. 하지 말 것
- 되묻기 다이얼로그 작업(핸드오프 A)과 같은 파일을 동시에 수정하지 않습니다. `AnalysisPromptBuilder.kt`, `ResolveAnalysisTargetUseCase.kt`, `ExecuteAnalysisGenerationUseCase.kt`는 두 작업이 겹치므로, **A가 끝난 뒤 B를 시작**하거나 같은 브랜치에서 순차로 진행하세요.
- 요청과 무관한 리팩터링, 전체 단위 테스트 실행
