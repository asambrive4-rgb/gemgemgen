package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.SharedPreferences
import com.example.gemgemgen.automation.domain.AutomationRunLog
import com.example.gemgemgen.automation.usecase.RunLogRepository

class SharedPreferencesRunLogRepository(
    context: Context
) : RunLogRepository {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): List<AutomationRunLog> {
        return RunLogCodec.decode(preferences.getString(KEY_LOGS, "").orEmpty())
    }

    override fun save(logs: List<AutomationRunLog>) {
        preferences.edit().putString(KEY_LOGS, RunLogCodec.encode(logs)).apply()
    }
}

private const val PREFERENCES_NAME = "automation_run_logs"
private const val KEY_LOGS = "logs"
