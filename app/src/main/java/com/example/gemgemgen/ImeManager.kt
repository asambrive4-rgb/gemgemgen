package com.example.gemgemgen

import android.content.Context
import android.provider.Settings

class ImeManager(
    private val settings: ImeSettings,
    private val nullKeyboardImeId: String = AppDefaults.NULL_KEYBOARD_IME_ID
) {
    fun switchToNullKeyboard(): ImeSwitchResult {
        val originalImeId = settings.getDefaultInputMethod()
        if (originalImeId.isNullOrBlank()) {
            return ImeSwitchResult.Failure(
                message = "현재 입력기 ID를 읽지 못했습니다.",
                originalImeId = originalImeId
            )
        }

        if (originalImeId == nullKeyboardImeId) {
            return ImeSwitchResult.Success(
                session = ImeSwitchSession(
                    originalImeId = originalImeId,
                    targetImeId = nullKeyboardImeId,
                    changed = false
                )
            )
        }

        if (!settings.setDefaultInputMethod(nullKeyboardImeId)) {
            return ImeSwitchResult.Failure(
                message = "Null Keyboard로 전환하지 못했습니다.",
                originalImeId = originalImeId
            )
        }

        val currentImeId = settings.getDefaultInputMethod()
        return if (currentImeId == nullKeyboardImeId) {
            ImeSwitchResult.Success(
                session = ImeSwitchSession(
                    originalImeId = originalImeId,
                    targetImeId = nullKeyboardImeId,
                    changed = true
                )
            )
        } else {
            if (currentImeId != originalImeId) {
                settings.setDefaultInputMethod(originalImeId)
            }
            ImeSwitchResult.Failure(
                message = "Null Keyboard로 전환하지 못했습니다.",
                originalImeId = originalImeId
            )
        }
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

    companion object {
        fun android(context: Context): ImeManager {
            return ImeManager(AndroidImeSettings(context))
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
}

private class AndroidImeSettings(
    private val context: Context
) : ImeSettings {
    override fun getDefaultInputMethod(): String? {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
    }

    override fun setDefaultInputMethod(imeId: String): Boolean {
        return Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
            imeId
        )
    }
}
