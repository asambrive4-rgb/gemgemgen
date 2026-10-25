// 역할: 4종 토스 스타일 테마 팔레트(토스 시그니처 기본값 포함)와 라이트/다크 모드(Level 1 마일드 토스 3단계 표면 대비) 색상 변환 로직을 검증합니다.
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
    fun defaultThemePalette_isTossSignature() {
        assertEquals(AppThemePalette.TOSS_SIGNATURE, AppThemePalette.DEFAULT)
        assertEquals("toss_signature", AppThemePalette.DEFAULT.id)
        assertEquals(4, AppThemePalette.entries.size)
    }

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
    fun darkMode_maintainsThreeLayerContrastAndBrandPrimary() {
        val darkCanvas = Color(0xFF0D1117)
        val darkCard = Color(0xFF1C222D)
        val darkInset = Color(0xFF131720)
        val brightText = Color(0xFFEDEDF0)

        AppThemePalette.entries.forEach { palette ->
            val darkColors = palette.toAppColors(isDark = true)
            // 3단계 표면 대비 (Canvas -> Card -> Inset) 검증
            assertEquals("다크모드 canvas는 딥 블랙이어야 합니다 ($palette)", darkCanvas, darkColors.canvas)
            assertEquals("다크모드 card는 배경과 구분되는 다크 슬레이트여야 합니다 ($palette)", darkCard, darkColors.card)
            assertEquals("다크모드 insetBed는 카드와 구분되는 인셋 표면이어야 합니다 ($palette)", darkInset, darkColors.insetBed)
            // 무테두리 3단계 버튼 위계를 위해 primary는 선명한 팔레트 포인트 컬러 유지
            assertNotEquals("다크모드 primary는 카드/인셋과 구분되는 포인트 컬러여야 합니다 ($palette)", darkCard, darkColors.primary)
            assertEquals("다크모드 onPrimary 텍스트는 화이트여야 합니다 ($palette)", Color.White, darkColors.onPrimary)
            assertEquals("다크모드 textPrimary는 선명한 화이트여야 합니다 ($palette)", brightText, darkColors.textPrimary)
            // 입력창 배경도 눈부시지 않은 다크 베드여야 합니다
            assertNotEquals("다크모드 inputBackground는 순백이 아니어야 합니다 ($palette)", Color.White, darkColors.inputBackground)
        }
    }

    @Test
    fun lightMode_retainsPureWhiteCardAndDistinctThreeLayerCanvas() {
        AppThemePalette.entries.forEach { palette ->
            val lightColors = palette.toAppColors(isDark = false)
            assertNotEquals(Color(0xFF0A0A0C), lightColors.canvas)
            assertEquals(Color.White, lightColors.card)
            assertEquals(Color.White, lightColors.inputBackground)
            assertNotEquals("배경과 카드 색상은 명확히 구분되어야 합니다 ($palette)", lightColors.canvas, lightColors.card)
            assertNotEquals("카드와 인셋 베드 색상은 명확히 구분되어야 합니다 ($palette)", lightColors.card, lightColors.insetBed)
        }
    }
}
