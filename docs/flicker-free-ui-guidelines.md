# flicker-free-ui-guidelines.md: 화면 전환 및 다이얼로그 깜빡임 방지 가이드라인

본 문서는 `gemgemgen` 프로젝트에서 화면 전환, 다이얼로그 전환, 탭 전환 시 발생하는 **깜빡임(Flicker), 번쩍임(Dim-Flicker), 빈 박스 노출(Pop-in)**을 원천 차단하기 위한 UI/UX 상태 아키텍처 및 Compose 구현 지침입니다.

---

## 1. 4대 핵심 원칙 (Core Principles)

### 원칙 1: 다이얼로그 단일 윈도우 (Single Dialog Host & Zero-Dim-Flicker)
- 연속된 다이얼로그 전환(예: 조건 입력 → 진행 중 로딩 → 미리보기 → 덮어쓰기 확인, 또는 목록 ↔ 수정) 시 다이얼로그 창을 닫고 새로 열지 않습니다.
- **화면 뒤 검정 딤(Dim) 배경이 켜졌다 꺼졌다 번쩍이는 현상을 100% 금지**합니다.
- 단 하나의 시스템 `Dialog` 윈도우 컨테이너를 열어두고, 내부 컨텐츠만 `AnimatedContent`로 부드럽게 크로스페이드(FadeIn / FadeOut) 전환합니다.

### 원칙 2: 데이터 준비 후 원자적 화면 전환 (Hold Screen & Atomic Transition)
- `화면 이동/탭 클릭 → 빈 화면/로딩 스피너 → 데이터 노출` 순서의 전환을 엄격히 금지합니다.
- 다음 파일이나 데이터가 100% 준비될 때까지 **현재 화면 상태를 그대로 유지(Hold Screen)**한 뒤, 단 1회의 상태 갱신(Atomic StateFlow Update)으로 새 화면과 데이터를 동시에 원자적으로 교체합니다.

### 원칙 3: 탭 전환 시 빈 화면 깜빡임 차단 (Zero-Flash Tab Transition)
- 탭을 오갈 때마다 ViewModel을 완전히 파괴(`ViewModelStoreOwner.clear()`)하여 파일 목록과 본문이 매번 사라졌다 다시 뜨는 팝인(Pop-in) 현상을 방지합니다.
- 탭을 벗어날 때는 무거운 임시 캐시나 무제한 실행 취소(Undo) 스택만 경량화(`trimForInactiveTab`)하고, 가벼운 텍스트 본문과 메타데이터는 유지하여 재진입 시 **0ms 즉시 표시**를 보장합니다.

### 원칙 4: 전역 UI 소유권 분리 및 가상 키보드(IME) 보호
- 상단 앱바나 헤더의 전역 설정 버튼은 특정 탭 내부가 아닌 **최상위 호스트(App / Host) 레벨**에서 다이얼로그를 띄웁니다.
- 하위 탭 내부 뷰 트리에 다이얼로그를 가두지 않음으로써 가상 키보드(IME) 충돌, 레이아웃 덜컥거림, 탭 스와이프 시 다이얼로그 오작동을 차단합니다.

---

## 2. 패턴 비교: 금지 패턴 vs 권장 패턴 (Before vs After)

### 패턴 1. 연속 다이얼로그 전환 (AI 분류, 마법사 스텝)

#### ❌ 금지 패턴 (Anti-pattern)
각 단계마다 별도의 `AlertDialog`를 `if`문으로 열고 닫음 → 매 스텝마다 시스템 윈도우가 파괴/생성되며 배경 검정 딤이 번쩍임.
```kotlin
// BAD: 다이얼로그가 바뀔 때마다 배경 딤이 깜빡거림
if (uiState.showClassifyCriteriaDialog) {
    ClassifyCriteriaDialog(...)
}
if (uiState.isClassifying) {
    AlertDialog(...) // 로딩 다이얼로그
}
if (uiState.classifyPreview != null) {
    ClassifyPreviewDialog(...)
}
if (uiState.classifyOverwriteConflicts.isNotEmpty()) {
    ClassifyOverwriteDialog(...)
}
```

#### ✅ 권장 패턴 (Recommended: Single Dialog Host)
하나의 시스템 `Dialog` 윈도우 내에서 `AnimatedContent`로 컨텐츠만 전환.
```kotlin
// GOOD: 단 1개의 Dialog 윈도우 내에서 컨텐츠만 크로스페이드
val activeDialog = deriveActiveWildcardDialog(uiState)

if (activeDialog !is WildcardDialogType.None) {
    Dialog(
        onDismissRequest = { onDismissCurrent(activeDialog) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedContent(
            targetState = activeDialog,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "WildcardDialogCrossfade"
        ) { targetDialog ->
            when (targetDialog) {
                is WildcardDialogType.Criteria -> ClassifyCriteriaContent(...)
                is WildcardDialogType.Progress -> ClassifyProgressContent(...)
                is WildcardDialogType.Preview -> ClassifyPreviewContent(...)
                is WildcardDialogType.Overwrite -> ClassifyOverwriteContent(...)
                ...
            }
        }
    }
}
```

---

### 패턴 2. 파일/데이터 전환 (Hold Screen & Atomic Update)

#### ❌ 금지 패턴 (Anti-pattern)
새 파일을 클릭하자마자 에디터를 빈 상태로 비우고 로딩을 시작하거나, 파일 정보와 텍스트를 따로따로 갱신함.
```kotlin
// BAD: 파일 클릭 즉시 비워서 흰 화면이 스쳐 지나감
fun selectFile(file: WildcardTextFile) {
    _uiState.update { it.copy(selectedFile = file, editingText = "") } // 빈 화면 깜빡임!
    scope.launch {
        val text = repository.readText(file)
        _uiState.update { it.copy(editingText = text) }
    }
}
```

#### ✅ 권장 패턴 (Recommended: Hold Screen & Atomic Transition)
새 파일 데이터가 준비될 때까지 이전 화면을 그대로 유지(Hold)하고, 단 1회의 원자적 갱신으로 교체.
```kotlin
// GOOD: 이전 내용을 그대로 유지하다가, 새 데이터가 준비된 순간 한 번에 원자적 교체
fun selectFile(file: WildcardTextFile) {
    if (state.isFileOperationInProgress || state.selectedFile?.id == file.id) return
    
    scope.launch {
        beginFileOperation() // 화면은 이전 파일 내용 그대로 유지(Hold)
        try {
            val text = repository.readText(file)
            _uiState.update { current ->
                // 단 1회의 업데이트로 파일과 텍스트가 동시에 교체됨!
                current.copy(
                    editor = current.editor.open(file, text),
                    isFileOperationInProgress = false
                )
            }
        } catch (e: Exception) {
            endFileOperation()
            showError("파일을 열지 못했습니다.")
        }
    }
}
```

---

### 패턴 3. 탭 수명주기 및 상태 보존 (Zero-Flash Tab Transition)

#### ❌ 금지 패턴 (Anti-pattern)
메모리를 아낀다는 이유로 탭을 나갈 때마다 ViewModel을 파괴(`clear()`)하거나 텍스트 본문까지 모두 비움.
```kotlin
// BAD: 탭 바꿀 때마다 파괴하여 재진입 시 0초 표시 불가능
if (selectedTab == MainTab.WILDCARD && tab != MainTab.WILDCARD) {
    wildcardStoreOwner.clear() // 매번 ViewModel 재생성 및 디스크 재조회 유발
}
```

#### ✅ 권장 패턴 (Recommended: Lightweight Inactive Tab Trimming)
ViewModel과 파일 목록, 현재 텍스트 본문(수십 KB 수준)은 유지하고, 무거운 Undo 스택 등만 정리.
```kotlin
// GOOD: 본문은 유지하여 재진입 시 0ms 즉시 표시, Undo 스택만 정리
fun trimForInactiveTab(): WildcardEditorSession {
    if (selectedFile == null) return this
    // 텍스트 본문(savedText, editingText)은 보존하고, 큰 히스토리(undoStack)만 비움
    return if (undoStack.isEmpty()) this else copy(undoStack = emptyList())
}
```

---

### 패턴 4. 전역 설정 다이얼로그의 호스트 위치

#### ❌ 금지 패턴 (Anti-pattern)
상단바는 전역에 있는데, 다이얼로그는 특정 탭 스크린 내부에 하드코딩.
```kotlin
// BAD: AutomationScreen 내부에 갇혀 있어 다른 탭에서 열 때 키보드/생명주기 충돌
@Composable
fun AutomationScreen(...) {
    ...
    if (uiState.showSettings) {
        StatusSettingsDialog(...)
    }
}
```

#### ✅ 권장 패턴 (Recommended: Root Host Elevation)
최상위 호스트 컨테이너 레벨로 다이얼로그를 승격.
```kotlin
// GOOD: 최상위 AutomationApp 또는 Host 레벨에서 단일하게 관리
@Composable
fun AutomationApp(...) {
    MainTabbedScreen(...)
    
    // 어느 탭에서든 즉시 안전하게 최상위 윈도우로 열림
    if (mainUiState.showSettings) {
        StatusSettingsDialog(...)
    }
}
```

---

## 3. 개발자/AI 셀프 체크리스트 (Checklist)

구현을 마친 후 다음 항목을 반드시 검증해야 합니다:

- [ ] **다이얼로그 딤 깜빡임 검증**: 연속된 팝업 전환(조건입력 → 로딩 → 결과미리보기 → 확인) 동안 배경 검정 딤(Dim)이 한 번이라도 사라졌다 다시 뜨는가? (절대 없어야 함)
- [ ] **이중 다이얼로그 중첩 검증**: 한 다이얼로그 위에 다른 다이얼로그가 겹쳐서 뜨는 지점이 없는가?
- [ ] **에디터 0ms 전환 검증**: 파일 목록에서 다른 파일을 빠르게 눌렀을 때, 이전 텍스트가 유지되다가 단 한 프레임에 새 텍스트로 깔끔하게 교체되는가? (빈 화면 노출 금지)
- [ ] **탭 재진입 즉시 노출 검증**: 탭을 왕복 전환할 때 흰 박스, 깜빡임, 재로딩 스피너가 보이지 않고 이전 상태가 즉각 나타나는가?
- [ ] **설정 다이얼로그 독립성**: 와일드카드 탭, 분석 탭, 자동화 탭 어느 위치에서든 상단 톱니바퀴를 눌렀을 때 다이얼로그가 즉시 안정적으로 표시되는가?
- [ ] **회귀 테스트**: `./gradlew testDebugUnitTest` 명령어가 오류 없이 정상 통과하는가?
