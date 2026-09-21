// 역할: 프롬프트 본문에서 커서 위치의 단어를 선택된 와일드카드 토큰 또는 상용구 문구로 안전하게 치환합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete

/**
 * 추천 칩 클릭 시 커서 위치의 단어를 해당 와일드카드 토큰 또는 상용구 문구로 치환하는 유스케이스.
 * 차단 상태, 문단 모드, 드래그 선택 상태, 토큰 유효성 및 치환 결과를 철저히 검증하여 안전성을 보장합니다.
 */
class ApplyWildcardTokenUseCase {

    data class Result(
        val newText: String,
        val cursorAfter: Int
    )

    operator fun invoke(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        candidate: WildcardTokenAutocomplete.Candidate,
        candidates: List<WildcardTokenAutocomplete.Candidate>,
        isParagraphSelectionMode: Boolean = false,
        isBlocked: Boolean = false
    ): Result? = invoke(
        text = text,
        selectionStart = selectionStart,
        selectionEnd = selectionEnd,
        token = candidate.token,
        candidates = candidates,
        isParagraphSelectionMode = isParagraphSelectionMode,
        isBlocked = isBlocked
    )

    operator fun invoke(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        token: String,
        candidates: List<WildcardTokenAutocomplete.Candidate>,
        isParagraphSelectionMode: Boolean = false,
        isBlocked: Boolean = false
    ): Result? {
        if (isBlocked || isParagraphSelectionMode) return null
        if (token.isBlank()) return null
        if (selectionStart != selectionEnd) return null // 드래그 선택 중에는 치환 불가
        if (candidates.none { it.token == token }) return null

        val replacement = WildcardTokenAutocomplete.replaceWordAtCursor(
            text = text,
            cursor = selectionEnd,
            token = token
        ) ?: return null

        if (replacement.newText == text) return null

        return Result(
            newText = replacement.newText,
            cursorAfter = replacement.cursorAfter
        )
    }
}
