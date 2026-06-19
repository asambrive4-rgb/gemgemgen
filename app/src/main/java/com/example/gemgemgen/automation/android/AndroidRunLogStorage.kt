package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.SharedPreferences
import com.example.gemgemgen.automation.usecase.RunLogStorage

class SharedPreferencesRunLogStorage(
    context: Context
) : RunLogStorage {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): String {
        return preferences.getString(KEY_LOGS, "").orEmpty()
    }

    override fun write(value: String) {
        preferences.edit().putString(KEY_LOGS, value).apply()
    }
}

private const val PREFERENCES_NAME = "automation_run_logs"
private const val KEY_LOGS = "logs"
