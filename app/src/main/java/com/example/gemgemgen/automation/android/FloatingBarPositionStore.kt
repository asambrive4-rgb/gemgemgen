package com.example.gemgemgen.automation.android

import android.content.Context
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class FloatingBarPosition(
    val x: Int,
    val y: Int
)

internal class FloatingBarPositionStore(
    context: Context
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): FloatingBarPosition? {
        if (!preferences.contains(KEY_X) || !preferences.contains(KEY_Y)) {
            return null
        }
        return FloatingBarPosition(
            x = preferences.getInt(KEY_X, 0),
            y = preferences.getInt(KEY_Y, 0)
        )
    }

    fun save(position: FloatingBarPosition) {
        preferences.edit()
            .putInt(KEY_X, position.x)
            .putInt(KEY_Y, position.y)
            .apply()
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

    private companion object {
        const val PREFERENCES_NAME = "floating_automation_bar"
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val EDGE_MARGIN_DP = 16
        const val MAX_BAR_WIDTH_DP = 470
        const val MIN_VISIBLE_HEIGHT_DP = 96
    }
}
