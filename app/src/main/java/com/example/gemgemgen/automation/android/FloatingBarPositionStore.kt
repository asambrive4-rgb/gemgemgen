// 역할: Jetpack DataStore와 메모리 캐시를 활용하여 플로팅 바의 화면 위치 좌표를 관리하고 비동기/동기 저장을 제공합니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

internal data class FloatingBarPosition(
    val x: Int,
    val y: Int
)

private val Context.floatingBarDataStore: DataStore<Preferences> by preferencesDataStore(
    name = FloatingBarPositionStore.DATASTORE_NAME
)

internal class FloatingBarPositionStore(
    private val dataStore: DataStore<Preferences>,
    private val appContext: Context
) {
    constructor(context: Context) : this(
        context.applicationContext.floatingBarDataStore,
        context.applicationContext
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

    fun load(): FloatingBarPosition? {
        memoryCache?.let { return it }
        return runBlocking {
            positionFlow.first()
        }
    }

    fun save(position: FloatingBarPosition) {
        memoryCache = position
        runBlocking {
            saveAsync(position)
        }
    }

    suspend fun saveAsync(position: FloatingBarPosition) {
        memoryCache = position
        dataStore.edit { preferences ->
            preferences[KEY_X] = position.x
            preferences[KEY_Y] = position.y
        }
    }

    fun defaultPosition(): FloatingBarPosition {
        val screenWidth = appContext.resources.displayMetrics.widthPixels
        val screenHeight = appContext.resources.displayMetrics.heightPixels
        val x = max((screenWidth - barWidthPx()) / 2, 0)
        val y = max(screenHeight - minVisibleHeightPx() - edgeMarginPx(), 0)
        return FloatingBarPosition(x, y)
    }

    fun barWidthPx(): Int {
        return dpToPx(MAX_BAR_WIDTH_DP)
    }

    fun minVisibleHeightPx(): Int {
        return dpToPx(MIN_VISIBLE_HEIGHT_DP)
    }

    private fun edgeMarginPx(): Int {
        return dpToPx(EDGE_MARGIN_DP)
    }

    private fun dpToPx(value: Int): Int {
        return (value * appContext.resources.displayMetrics.density).roundToInt()
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
