package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.SharedPreferences
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.LastRunSnapshot
import com.example.gemgemgen.automation.usecase.LastRunSnapshotRepository

class SharedPreferencesLastRunSnapshotRepository(
    context: Context
) : LastRunSnapshotRepository {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): LastRunSnapshot? {
        val promptTemplate = preferences.getString(KEY_PROMPT_TEMPLATE, "").orEmpty()
        val repeatCountText = preferences.getString(KEY_REPEAT_COUNT_TEXT, "").orEmpty()
        if (promptTemplate.isBlank() && repeatCountText.isBlank()) return null

        return LastRunSnapshot(
            promptTemplate = promptTemplate,
            repeatCountText = repeatCountText,
            targetApp = AutomationTargetApp.fromStorageValue(
                preferences.getString(KEY_TARGET_APP, "").orEmpty()
            )
        )
    }

    override fun save(snapshot: LastRunSnapshot) {
        preferences.edit()
            .putString(KEY_PROMPT_TEMPLATE, snapshot.promptTemplate)
            .putString(KEY_REPEAT_COUNT_TEXT, snapshot.repeatCountText)
            .putString(KEY_TARGET_APP, snapshot.targetApp.storageValue)
            .apply()
    }
}

private const val PREFERENCES_NAME = "last_run_snapshot"
private const val KEY_PROMPT_TEMPLATE = "prompt_template"
private const val KEY_REPEAT_COUNT_TEXT = "repeat_count_text"
private const val KEY_TARGET_APP = "target_app"
