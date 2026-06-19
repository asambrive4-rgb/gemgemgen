package com.example.gemgemgen.automation.android

import android.content.Context
import android.provider.Settings
import com.example.gemgemgen.automation.usecase.ImeSettings

class AndroidImeSettings(
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
