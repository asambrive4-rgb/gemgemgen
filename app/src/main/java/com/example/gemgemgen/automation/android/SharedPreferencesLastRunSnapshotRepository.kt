// 역할: 마지막으로 실행했던 자동화 설정과 프롬프트 스냅샷을 인메모리 캐시 및 로컬에 영구 저장합니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.SharedPreferences
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.LastRunSnapshot
import com.example.gemgemgen.automation.usecase.LastRunSnapshotRepository
import com.example.gemgemgen.core.AppDefaults

class SharedPreferencesLastRunSnapshotRepository(
    context: Context
) : LastRunSnapshotRepository {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedSnapshot: LastRunSnapshot? = null
    @Volatile
    private var isLoaded: Boolean = false

    override fun load(): LastRunSnapshot? {
        if (isLoaded) return cachedSnapshot

        val promptTemplate = preferences.getString(KEY_PROMPT_TEMPLATE, "").orEmpty()
        val repeatCountText = preferences.getString(KEY_REPEAT_COUNT_TEXT, "").orEmpty()
        if (promptTemplate.isBlank() && repeatCountText.isBlank()) {
            isLoaded = true
            cachedSnapshot = null
            return null
        }

        val snapshot = LastRunSnapshot(
            promptTemplate = promptTemplate,
            repeatCountText = repeatCountText,
            targetApp = AutomationTargetApp.fromStorageValue(
                preferences.getString(KEY_TARGET_APP, "").orEmpty()
            ),
            flowImageCount = preferences.getInt(KEY_FLOW_IMAGE_COUNT, AppDefaults.DEFAULT_FLOW_IMAGE_COUNT)
        )
        cachedSnapshot = snapshot
        isLoaded = true
        return snapshot
    }

    override fun save(snapshot: LastRunSnapshot) {
        cachedSnapshot = snapshot
        isLoaded = true
        preferences.edit()
            .putString(KEY_PROMPT_TEMPLATE, snapshot.promptTemplate)
            .putString(KEY_REPEAT_COUNT_TEXT, snapshot.repeatCountText)
            .putString(KEY_TARGET_APP, snapshot.targetApp.storageValue)
            .putInt(KEY_FLOW_IMAGE_COUNT, snapshot.flowImageCount)
            .apply()
    }
}

private const val PREFERENCES_NAME = "last_run_snapshot"
private const val KEY_PROMPT_TEMPLATE = "prompt_template"
private const val KEY_REPEAT_COUNT_TEXT = "repeat_count_text"
private const val KEY_TARGET_APP = "target_app"
private const val KEY_FLOW_IMAGE_COUNT = "flow_image_count"