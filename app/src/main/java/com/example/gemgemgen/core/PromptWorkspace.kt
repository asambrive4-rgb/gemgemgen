// 역할: 자동화와 AI 분석 화면 간의 프롬프트 동기화 및 구간 치환 이벤트를 중계하는 공유 작업 공간입니다.
package com.example.gemgemgen.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PromptHandoffEvent {
    data class ReplaceEntirely(val replacement: String) : PromptHandoffEvent
}

class PromptWorkspace {
    private val _currentPrompt = MutableStateFlow("")
    val currentPrompt: StateFlow<String> = _currentPrompt.asStateFlow()

    private val _handoffEvents = MutableSharedFlow<PromptHandoffEvent>(extraBufferCapacity = 1)
    val handoffEvents: SharedFlow<PromptHandoffEvent> = _handoffEvents.asSharedFlow()

    var segmentReplacer: ((expectedSegment: String, replacement: String, preferredStartIndex: Int) -> Int?)? = null

    fun updateCurrentPrompt(prompt: String) {
        _currentPrompt.value = prompt
    }

    fun replaceSegment(
        expectedSegment: String,
        replacement: String,
        preferredStartIndex: Int
    ): Int? {
        return segmentReplacer?.invoke(expectedSegment, replacement, preferredStartIndex)
    }

    fun handoffEntirely(replacement: String) {
        _currentPrompt.value = replacement
        _handoffEvents.tryEmit(PromptHandoffEvent.ReplaceEntirely(replacement))
    }
}
