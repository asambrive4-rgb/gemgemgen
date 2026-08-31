package com.example.gemgemgen.automation.android

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.example.gemgemgen.automation.usecase.ImeSettings

class AndroidImeSettings(
    private val context: Context
) : ImeSettings {
    override fun getDefaultInputMethod(): String? {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
        } catch (_: Throwable) {
            null
        }
    }

    override fun setDefaultInputMethod(imeId: String): Boolean {
        return try {
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
                imeId
            )
        } catch (_: Throwable) {
            false
        }
    }

    override fun getEnabledInputMethods(): List<String> {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.map { it.id }.orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

