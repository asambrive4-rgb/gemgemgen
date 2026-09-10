# mobile-interaction-ux-guidelines.md: 모바일 터치 및 가상 키보드(IME) 인터랙션 UX 가이드

본 문서는 `gemgemgen` 프로젝트에서 스마트폰 모바일 환경의 특성으로 인해 발생하는 **가상 키보드(IME) 화면 가림, 포커스 고착, 복잡한 텍스트 입력 피로도**를 원천 해결하기 위한 터치 및 키보드 인터랙션 UX 구현 지침입니다.

모바일 화면의 입력창, 스크롤 뷰, 하단 액션 바, 자동완성 기능을 구현하거나 수정할 때는 반드시 이 문서의 기준을 따릅니다.

---

## 1. 3대 핵심 원칙 (Core Principles)

### 원칙 1: 외부 탭 시 가상 키보드 및 포커스 즉시 해제 (Clear Focus On Outside Tap)
- 사용자가 텍스트 입력창 밖의 빈 배경이나 스크롤 영역을 가볍게 탭하면, **즉시 포커스를 해제하고 가상 키보드를 내려야 합니다.**
- 모바일 화면의 절반 이상을 차지하는 거대한 가상 키보드가 불필요하게 화면에 남아 다른 UI 조작을 방해해서는 안 됩니다.
- 단, 텍스트필드 자체나 다른 대화형 버튼을 탭했을 때는 해당 컴포넌트가 자연스럽게 동작하도록 제스처 우선순위(Pass)를 정교하게 제어해야 합니다.

### 원칙 2: 키보드(IME) 높이 반응형 하단 동적 스페이서 (Dynamic IME Bottom Spacing)
- 하단 고정 액션 패널(Sticky Bottom Action Panel)이 있는 화면에서는 **키보드가 올라왔을 때 스크롤 컨텐츠의 최하단 요소가 가려지는 현상을 100% 차단**합니다.
- 현재 키보드(IME)의 노출 여부를 실시간으로 감지하여, 스크롤 컨텐츠 최하단 여백(`Spacer`)의 높이를 동적으로 가변 확장합니다.
- 다이얼로그 창 내부에서는 `.imePadding()`을 적용하여 키보드가 팝업 버튼을 가리지 않도록 윈도우 인셋을 처리합니다.

### 원칙 3: 모바일 친화적 원터치 자동완성 칩 (Inline Autocomplete & Chip Bar)
- 모바일 가상 키보드로 긴 파일명이나 특수 기호(`__와일드카드__`)를 일일이 타이핑하는 고비용 입력을 지양합니다.
- 커서 위치의 토큰을 실시간 감지하여, 에디터 바로 위에 가로 스크롤 가능한 3D 조약돌 칩 바를 노출하고 **원터치 탭 1회로 본문에 즉시 삽입**할 수 있도록 구현합니다.

---

## 2. 패턴별 구현 지침 및 Before vs After

### 패턴 1. 외부 탭 포커스 해제 (`Modifier.clearFocusOnOutsideTap`)

#### ❌ 금지 패턴 (Anti-pattern)
사용자가 텍스트를 다 쳤음에도 키보드가 계속 열려 있어 다른 탭이나 버튼을 보기 위해 뒤로가기 키를 억지로 눌러야 함.
```kotlin
// BAD: 외부를 터치해도 포커스와 키보드가 그대로 남아있음
Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
) {
    AppMultilineTextField(...)
    Button(...)
}
```

#### ✅ 권장 패턴 (Recommended: Clear Focus Modifier)
`PointerEventPass.Initial`과 터치 허용 오차(`touchSlop`)를 사용하여, 화면 바깥 단순 탭 시 안전하게 포커스를 해제.
- 구현체: [`ClearFocusOnTapModifier.kt`](file:///c:/Users/joajo/AndroidStudioProjects/gemgemgen/app/src/main/java/com/example/gemgemgen/ui/ClearFocusOnTapModifier.kt)
```kotlin
// GOOD: 최상위 컨테이너에 clearFocusOnOutsideTap 적용
val focusManager = LocalFocusManager.current
val clearInputFocus = remember(focusManager) {
    { focusManager.clearFocus(force = true) }
}

Box(
    modifier = Modifier
        .fillMaxSize()
        .imePadding()
        .clearFocusOnOutsideTap(clearInputFocus) // 빈 공간 탭 시 키보드 즉시 하강!
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ...
    }
}
```
> **원리**: `PointerEventPass.Initial` 단계에서 탭 제스처를 감지하여 포커스를 먼저 해제(`onClearFocus()`)하더라도, 포커스 가능한 다른 TextField나 버튼을 직접 탭한 경우에는 해당 자식 뷰가 같은 제스처 내에서 즉시 포커스를 다시 획득하므로 정상 동작합니다.

---

### 패턴 2. 키보드 노출 대응 동적 하단 여백 (`Dynamic IME Bottom Spacing`)

#### ❌ 금지 패턴 (Anti-pattern)
하단에 고정된 버튼 바가 있는 상태에서 키보드가 올라오면, 스크롤 뷰의 마지막 입력창이나 카드가 하단 바 밑으로 숨어버려 보이지도 않고 스크롤해도 닿지 않음.
```kotlin
// BAD: 고정 여백으로 인해 키보드가 떴을 때 마지막 요소가 하단 바에 가려짐
Column(modifier = Modifier.verticalScroll(scrollState)) {
    TextField(...)
    ResultCards(...)
    Spacer(modifier = Modifier.height(100.dp)) // 키보드 올라오면 결과 카드 가려짐!
}
StickyBottomBar(modifier = Modifier.align(Alignment.BottomCenter))
```

#### ✅ 권장 패턴 (Recommended: WindowInsets IME Detection)
`WindowInsets.ime` 높이를 감지하여 키보드가 올라왔을 때 스크롤 컨텐츠 하단 스페이서 높이를 자동으로 증폭.
- 적용 파일: [`AnalysisScreen.kt`](file:///c:/Users/joajo/AndroidStudioProjects/gemgemgen/app/src/main/java/com/example/gemgemgen/analysis/ui/AnalysisScreen.kt)
```kotlin
// GOOD: 키보드 노출 시 하단 여백을 160dp -> 260dp로 동적 확장
val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    SourcePromptAndMaskingRow(...)
    CountSection(...)
    FeedbackSection(...)
    ResultSection(...)

    // 하단 고정바(StickyBottomActionPanel)에 가려지지 않도록 키보드 상태에 따라 동적 여백 제공
    Spacer(modifier = Modifier.height(if (isKeyboardVisible) 260.dp else 160.dp))
}

// 하단 고정 액션 바
Surface(
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
) {
    StickyBottomActionPanel(...)
}
```

---

### 패턴 3. 와일드카드 실시간 자동완성 제안 칩 바 (`Inline Autocomplete & Chip Bar`)

#### ❌ 금지 패턴 (Anti-pattern)
토큰을 입력하기 위해 다이얼로그를 따로 열어 목록을 찾아서 복사해오게 하거나, 자동완성이 텍스트 필드를 가려 타이핑 흐름을 끊음.

#### ✅ 권장 패턴 (Recommended: Contextual Chip Bar)
에디터의 텍스트와 커서 위치(`selection.max`)를 관찰하여 후보군을 계산하고, 에디터 상단 트랙에 가로 스크롤 조약돌 칩으로 자연스럽게 제안.
- 적용 파일: [`PromptSection.kt`](file:///c:/Users/joajo/AndroidStudioProjects/gemgemgen/app/src/main/java/com/example/gemgemgen/automation/ui/PromptSection.kt), [`WildcardTokenAutocomplete.kt`](file:///c:/Users/joajo/AndroidStudioProjects/gemgemgen/app/src/main/java/com/example/gemgemgen/automation/domain/WildcardTokenAutocomplete.kt)
```kotlin
// GOOD: 커서 위치를 실시간 추적하여 자동완성 토큰 목록 계산
@Composable
internal fun rememberWildcardSuggestionTokens(
    promptTemplateState: TextFieldState,
    wildcardTokenCandidates: List<WildcardTokenAutocomplete.Candidate>,
    isParagraphSelectionMode: Boolean,
    isTargetSelectionEnabled: Boolean
): List<String> {
    val fieldText = promptTemplateState.text.toString()
    val selection = promptTemplateState.selection
    return remember(
        fieldText,
        selection,
        wildcardTokenCandidates,
        isParagraphSelectionMode,
        isTargetSelectionEnabled
    ) {
        if (isParagraphSelectionMode || !isTargetSelectionEnabled) {
            emptyList()
        } else if (selection.min != selection.max) {
            emptyList() // 드래그 선택 중에는 제안 숨김
        } else {
            // 커서 위치 기준으로 와일드카드 토큰 후보 추출 (순수 도메인 규칙)
            WildcardTokenAutocomplete.suggestions(
                text = fieldText,
                cursor = selection.max,
                candidates = wildcardTokenCandidates
            )
        }
    }
}

// 제안 바 UI (가로 스크롤 조약돌 칩)
if (showWildcardSuggestions && suggestionTokens.isNotEmpty()) {
    WildcardTokenSuggestionBar(
        tokens = suggestionTokens,
        onTokenClick = onWildcardTokenSuggestionClick
    )
}
```

---

## 3. 개발자/AI 셀프 체크리스트 (Checklist)

구현 또는 수정 후 다음 항목을 반드시 확인합니다:

- [ ] **외부 탭 포커스 해제**: 입력창이 아닌 스크롤 빈 곳이나 상단 바깥 영역을 탭했을 때 가상 키보드가 즉시 내려가고 커서 포커스가 사라지는가?
- [ ] **자식 클릭 간섭 없음**: 외부 탭 포커스 해제 Modifier가 적용된 상태에서, 다른 텍스트필드나 버튼을 클릭했을 때 정상적으로 해당 컨트롤이 반응하는가?
- [ ] **키보드 업 시 스크롤 끝 도달성**: 가상 키보드가 완전히 올라온 상태에서 스크롤을 끝까지 내렸을 때, 최하단 컨텐츠와 버튼이 하단 바 뒤로 숨지 않고 온전히 노출되는가?
- [ ] **다이얼로그 키보드 가림 방지**: 팝업/다이얼로그 창에 텍스트필드가 있을 때, 키보드가 팝업 하단의 '확인/취소' 버튼을 가리지 않는가 (`imePadding` 적용 여부)?
- [ ] **자동완성 원터치 삽입**: 와일드카드 토큰 입력 시 칩 바가 즉시 노출되고, 칩 터치 시 커서 위치에 정확하게 토큰이 들어가며 타이핑 흐름이 끊기지 않는가?
