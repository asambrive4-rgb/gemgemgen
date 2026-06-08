package com.example.gemgemgen

import android.content.Context
import android.content.SharedPreferences

data class LastRunSnapshot(
    val promptTemplate: String,
    val repeatCountText: String
)

class LastRunSnapshotStore(
    private val storage: LastRunSnapshotStorage
) {
    fun load(): LastRunSnapshot? {
        val promptTemplate = storage.readPromptTemplate()
        val repeatCountText = storage.readRepeatCountText()
        if (promptTemplate.isBlank() && repeatCountText.isBlank()) return null

        return LastRunSnapshot(
            promptTemplate = promptTemplate,
            repeatCountText = RepeatCountParser.normalizeInput(repeatCountText)
        )
    }

    fun save(snapshot: LastRunSnapshot) {
        storage.write(
            promptTemplate = snapshot.promptTemplate,
            repeatCountText = RepeatCountParser.normalizeInput(snapshot.repeatCountText)
        )
    }

    companion object {
        fun android(context: Context): LastRunSnapshotStore {
            return LastRunSnapshotStore(
                SharedPreferencesLastRunSnapshotStorage(
                    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                )
            )
        }
    }
}

interface LastRunSnapshotStorage {
    fun readPromptTemplate(): String
    fun readRepeatCountText(): String
    fun write(promptTemplate: String, repeatCountText: String)
}

private class SharedPreferencesLastRunSnapshotStorage(
    private val preferences: SharedPreferences
) : LastRunSnapshotStorage {
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
