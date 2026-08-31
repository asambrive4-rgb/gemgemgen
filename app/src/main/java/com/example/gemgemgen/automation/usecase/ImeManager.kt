package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.core.AppDefaults

class ImeManager(
    private val settings: ImeSettings,
    private val nullKeyboardCandidates: List<String> = AppDefaults.NULL_KEYBOARD_IME_CANDIDATES
) {
    constructor(settings: ImeSettings, nullKeyboardImeId: String) : this(
        settings = settings,
        nullKeyboardCandidates = listOf(nullKeyboardImeId)
    )

    fun switchToNullKeyboard(): ImeSwitchResult {
        val originalImeId = settings.getDefaultInputMethod()
        if (originalImeId.isNullOrBlank()) {
            return ImeSwitchResult.Failure(
                message = "현재 입력기 ID를 읽지 못했습니다.",
                originalImeId = originalImeId
            )
        }

        val currentMatchingCandidate = nullKeyboardCandidates.firstOrNull { it == originalImeId }
        if (currentMatchingCandidate != null) {
            return ImeSwitchResult.Success(
                session = ImeSwitchSession(
                    originalImeId = originalImeId,
                    targetImeId = currentMatchingCandidate,
                    changed = false
                )
            )
        }

        val enabledImeIds = settings.getEnabledInputMethods()
        val orderedCandidates = if (enabledImeIds.isNotEmpty()) {
            val enabledCandidates = nullKeyboardCandidates.filter { candidate ->
                enabledImeIds.any { enabled -> enabled.equals(candidate, ignoreCase = true) }
            }
            val remainingCandidates = nullKeyboardCandidates.filterNot { it in enabledCandidates }
            enabledCandidates + remainingCandidates
        } else {
            nullKeyboardCandidates
        }

        for (candidate in orderedCandidates) {
            if (!settings.setDefaultInputMethod(candidate)) {
                continue
            }

            val currentImeId = settings.getDefaultInputMethod()
            if (currentImeId == candidate) {
                return ImeSwitchResult.Success(
                    session = ImeSwitchSession(
                        originalImeId = originalImeId,
                        targetImeId = candidate,
                        changed = true
                    )
                )
            }
        }

        val currentImeId = settings.getDefaultInputMethod()
        if (currentImeId != originalImeId && currentImeId != null) {
            settings.setDefaultInputMethod(originalImeId)
        }

        return ImeSwitchResult.Failure(
            message = "Null Keyboard로 전환하지 못했습니다.",
            originalImeId = originalImeId
        )
    }

    fun restore(session: ImeSwitchSession): ImeRestoreResult {
        if (!session.changed) {
            return ImeRestoreResult.Success
        }

        return if (settings.setDefaultInputMethod(session.originalImeId)) {
            ImeRestoreResult.Success
        } else {
            ImeRestoreResult.Failure(
                originalImeId = session.originalImeId,
                currentImeId = settings.getDefaultInputMethod()
            )
        }
    }
}

data class ImeSwitchSession(
    val originalImeId: String,
    val targetImeId: String,
    val changed: Boolean
)

sealed interface ImeSwitchResult {
    data class Success(val session: ImeSwitchSession) : ImeSwitchResult
    data class Failure(
        val message: String,
        val originalImeId: String?
    ) : ImeSwitchResult
}

sealed interface ImeRestoreResult {
    data object Success : ImeRestoreResult
    data class Failure(
        val originalImeId: String,
        val currentImeId: String?
    ) : ImeRestoreResult
}

interface ImeSettings {
    fun getDefaultInputMethod(): String?
    fun setDefaultInputMethod(imeId: String): Boolean
    fun getEnabledInputMethods(): List<String> = emptyList()
}

