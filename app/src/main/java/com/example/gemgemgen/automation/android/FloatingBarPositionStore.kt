// 역할: WindowMetrics 기반 동적 가용 너비 계산과 Jetpack DataStore를 통해 반응형 플로팅 바 위치를 불러오고 저장합니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import android.view.WindowManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal data class FloatingBarPosition(
    val x: Int,
    val y: Int
)

internal interface FloatingBarMetricsProvider {
    fun screenWidthPx(): Int
    fun screenHeightPx(): Int
    fun density(): Float
}

internal class DefaultFloatingBarMetricsProvider(
    private val context: Context
) : FloatingBarMetricsProvider {
    override fun screenWidthPx(): Int {
        val windowManager = context.getSystemService(WindowManager::class.java)
        return windowManager?.currentWindowMetrics?.bounds?.width()
            ?: context.resources.displayMetrics.widthPixels
    }

    override fun screenHeightPx(): Int {
        val windowManager = context.getSystemService(WindowManager::class.java)
        return windowManager?.currentWindowMetrics?.bounds?.height()
            ?: context.resources.displayMetrics.heightPixels
    }

    override fun density(): Float {
        return context.resources.displayMetrics.density
    }
}

private val Context.floatingBarDataStore: DataStore<Preferences> by preferencesDataStore(
    name = FloatingBarPositionStore.DATASTORE_NAME
)

internal class FloatingBarPositionStore(
    private val dataStore: DataStore<Preferences>,
    private val metricsProvider: FloatingBarMetricsProvider
) {
    constructor(context: Context) : this(
        context.applicationContext.floatingBarDataStore,
        DefaultFloatingBarMetricsProvider(context.applicationContext)
    )

    @Volatile
    private var memoryCache: FloatingBarPosition? = null

    val positionFlow: Flow<FloatingBarPosition?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            val x = preferences[KEY_X]
            val y = preferences[KEY_Y]
            if (x != null && y != null) {
                FloatingBarPosition(x, y).also { memoryCache = it }
            } else {
                null
            }
        }

    suspend fun load(): FloatingBarPosition? {
        return memoryCache ?: positionFlow.first()
    }

    suspend fun save(position: FloatingBarPosition) {
        memoryCache = position
        dataStore.edit { preferences ->
            preferences[KEY_X] = position.x
            preferences[KEY_Y] = position.y
        }
    }

    fun screenWidthPx(): Int {
        return metricsProvider.screenWidthPx()
    }

    fun screenHeightPx(): Int {
        return metricsProvider.screenHeightPx()
    }

    fun defaultPosition(): FloatingBarPosition {
        val screenWidth = screenWidthPx()
        val screenHeight = screenHeightPx()
        val x = max((screenWidth - barWidthPx()) / 2, 0)
        val y = max(screenHeight - minVisibleHeightPx() - edgeMarginPx(), 0)
        return FloatingBarPosition(x, y)
    }

    fun barWidthPx(): Int {
        val screenWidth = screenWidthPx()
        val maxWidth = dpToPx(MAX_BAR_WIDTH_DP)
        return min(screenWidth, maxWidth)
    }

    fun minVisibleHeightPx(): Int {
        return dpToPx(MIN_VISIBLE_HEIGHT_DP)
    }

    private fun edgeMarginPx(): Int {
        return dpToPx(EDGE_MARGIN_DP)
    }

    private fun dpToPx(value: Int): Int {
        return (value * metricsProvider.density()).roundToInt()
    }

    companion object {
        const val DATASTORE_NAME = "floating_automation_bar"
        val KEY_X = intPreferencesKey("x")
        val KEY_Y = intPreferencesKey("y")
        const val EDGE_MARGIN_DP = 16
        const val MAX_BAR_WIDTH_DP = 470
        const val MIN_VISIBLE_HEIGHT_DP = 96
    }
}
