// 역할: 머티리얼 디자인 시스템과 커스텀 색상을 갓한 앱의 기본 테마를 적용합니다.
package com.example.gemgemgen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * GemGemGen 앱의 전역 소프트 3D 뉴모피즘 테마.
 * 선택된 [AppThemePalette] 및 [AppThemeMode]에 따라 Material 3 색상 체계 및 [LocalAppColors]를 공급합니다.
 */
@Composable
fun GemgemgenTheme(
    palette: AppThemePalette = AppThemePalette.DEFAULT,
    themeMode: AppThemeMode = AppThemeMode.DEFAULT,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemInDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val appColors = palette.toAppColors(isDark = isDark)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = appColors.primary,
            onPrimary = appColors.onPrimary,
            primaryContainer = appColors.card,
            onPrimaryContainer = appColors.textPrimary,
            secondary = appColors.accent,
            onSecondary = Color.White,
            secondaryContainer = appColors.insetBed,
            onSecondaryContainer = appColors.textPrimary,
            background = appColors.canvas,
            onBackground = appColors.textPrimary,
            surface = appColors.card,
            onSurface = appColors.textPrimary,
            surfaceVariant = appColors.insetBed,
            onSurfaceVariant = appColors.textSecondary,
            outline = appColors.inputBorder,
            outlineVariant = appColors.cardBorder
        )
    } else {
        lightColorScheme(
            primary = appColors.primary,
            onPrimary = appColors.onPrimary,
            primaryContainer = appColors.primary.copy(alpha = 0.15f),
            onPrimaryContainer = appColors.primary,
            secondary = appColors.accent,
            onSecondary = Color.White,
            secondaryContainer = appColors.insetBed,
            onSecondaryContainer = appColors.textPrimary,
            background = appColors.canvas,
            onBackground = appColors.textPrimary,
            surface = appColors.card,
            onSurface = appColors.textPrimary,
            surfaceVariant = appColors.insetBed,
            onSurfaceVariant = appColors.textSecondary,
            outline = appColors.inputBorder,
            outlineVariant = appColors.cardBorder
        )
    }

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalAppThemePalette provides palette,
        LocalAppThemeMode provides themeMode
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
