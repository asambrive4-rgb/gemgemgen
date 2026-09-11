// 역할: 테마 색상 및 팔레트 변경 로직을 검증합니다.
package com.example.gemgemgen

import androidx.compose.ui.graphics.Color
import com.example.gemgemgen.ui.theme.AppThemeMode
import com.example.gemgemgen.ui.theme.AppThemePalette
import com.example.gemgemgen.ui.theme.toAppColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppThemeTest {

    @Test
    fun defaultThemeMode_isSystem() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.DEFAULT)
        assertEquals("system", AppThemeMode.DEFAULT.id)
    }

    @Test
    fun themeModeFromId_resolvesCorrectly() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromId("system"))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromId("light"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromId("dark"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromId("unknown"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromId(null))
    }

    @Test
    fun darkMode_maintainsBlackCanvasAndDarkButtons() {
        val darkCanvas = Color(0xFF0A0A0C)
        val darkButton = Color(0xFF24272B)
        val brightText = Color(0xFFEDEDF0)

        AppThemePalette.entries.forEach { palette ->
            val darkColors = palette.toAppColors(isDark = true)
            // 검은색 배경 유지 검증
            assertEquals("다크모드 canvas는 딥 블랙이어야 합니다 ($palette)", darkCanvas, darkColors.canvas)
            // 버튼도 어둡게 검증
            assertEquals("다크모드 primary 버튼은 어두운 차콜이어야 합니다 ($palette)", darkButton, darkColors.primary)
            // 버튼 텍스트 및 본문 텍스트는 밝은 고대비 화이트 유지 검증
            assertEquals("다크모드 onPrimary 텍스트는 선명한 화이트여야 합니다 ($palette)", brightText, darkColors.onPrimary)
            assertEquals("다크모드 textPrimary는 선명한 화이트여야 합니다 ($palette)", brightText, darkColors.textPrimary)
            // 입력창 배경도 눈부시지 않은 다크 베드여야 합니다
            assertNotEquals("다크모드 inputBackground는 순백이 아니어야 합니다 ($palette)", Color.White, darkColors.inputBackground)
        }
    }

    @Test
    fun lightMode_retainsOriginalLightPalette() {
        AppThemePalette.entries.forEach { palette ->
            val lightColors = palette.toAppColors(isDark = false)
            assertNotEquals(Color(0xFF0A0A0C), lightColors.canvas)
            assertEquals(Color.White, lightColors.inputBackground)
        }
    }
}
