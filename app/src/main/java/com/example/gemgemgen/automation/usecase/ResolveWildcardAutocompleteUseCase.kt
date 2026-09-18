// 역할: 현재 텍스트와 커서 위치, 선택 모드를 분석하여 와일드카드 자동완성 추천 토큰 목록을 추출합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete

/**
 * 프롬프트 입력창의 현재 텍스트와 커서/선택 상태를 분석하여
 * 사용자에게 노출할 와일드카드 토큰 추천 후보 목록을 도출하는 유스케이스.
 * Compose UI 수명 주기 및 ViewModel과 무관하게 독립적으로 동작하며 검증 가능합니다.
 */
class ResolveWildcardAutocompleteUseCase {

    operator fun invoke(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        candidates: List<WildcardTokenAutocomplete.Candidate>,
        isParagraphSelectionMode: Boolean = false,
        isEnabled: Boolean = true,
        maxCount: Int = WildcardTokenAutocomplete.MAX_SUGGESTIONS
    ): List<String> {
        if (!isEnabled || isParagraphSelectionMode) return emptyList()
        if (selectionStart != selectionEnd) return emptyList() // 드래그 선택 중에는 제안 숨김
        if (candidates.isEmpty() || text.isEmpty()) return emptyList()

        return WildcardTokenAutocomplete.suggestions(
            text = text,
            cursor = selectionEnd,
            candidates = candidates,
            maxCount = maxCount
        )
    }
}
