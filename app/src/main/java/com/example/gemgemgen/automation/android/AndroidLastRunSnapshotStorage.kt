package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.SharedPreferences
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStorage

class SharedPreferencesLastRunSnapshotStorage(
    context: Context
) : LastRunSnapshotStorage {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun readPromptTemplate(): String {
        return preferences.getString(KEY_PROMPT_TEMPLATE, "").orEmpty()
    }

    override fun readRepeatCountText(): String {
        return preferences.getString(KEY_REPEAT_COUNT_TEXT, "").orEmpty()
    }

    override fun write(promptTemplate: String, repeatCountText: String) {
        preferences.edit()
            .putString(KEY_PROMPT_TEMPLATE, promptTemplate)
            .putString(KEY_REPEAT_COUNT_TEXT, repeatCountText)
            .apply()
    }
}

private const val PREFERENCES_NAME = "last_run_snapshot"
private const val KEY_PROMPT_TEMPLATE = "prompt_template"
private const val KEY_REPEAT_COUNT_TEXT = "repeat_count_text"
