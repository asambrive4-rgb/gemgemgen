// 역할: 앱에서 지원하는 테마 색상 팔레트 옵션들을 정의합니다.
package com.example.gemgemgen.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * GemGemGen 앱의 3가지 소프트 3D 뉴모피즘 테마 색상 팔레트.
 * 기본값은 SAGE_MATCHA (팔레트 2: 노르딕 세이지 & 말차)입니다.
 */
enum class AppThemePalette(
    val id: String,
    val title: String,
    val description: String,
    val previewColor: Color
) {
    CORAL_CREAM(
        id = "coral_cream",
        title = "팔레트 1: 웜 코랄 크림",
        description = "포근한 우유빛 아이보리와 생동감 넘치는 코랄 피치",
        previewColor = Color(0xFFD9532F)
    ),
    SAGE_MATCHA(
        id = "sage_matcha",
        title = "팔레트 2: 노르딕 세이지 (기본값)",
        description = "눈이 편안한 세이지 그린과 차분한 에메랄드",
        previewColor = Color(0xFF1B7A5A)
    ),
    PURE_INDIGO(
        id = "pure_indigo",
        title = "팔레트 3: 퓨어 인디고",
        description = "맑은 쿨 화이트와 청량한 일렉트릭 블루/인디고",
        previewColor = Color(0xFF2E5EB8)
    );

    companion object {
        val DEFAULT = SAGE_MATCHA

        fun fromId(id: String?): AppThemePalette {
            return entries.firstOrNull { it.id == id } ?: DEFAULT
        }
    }
}

/**
 * 앱의 테마 동작 모드 (시스템 설정 따름 / 라이트 / 다크).
 * 기본값은 시스템 설정을 따릅니다.
 */
enum class AppThemeMode(
    val id: String,
    val title: String
) {
    SYSTEM(
        id = "system",
        title = "시스템 설정 (기본값)"
    ),
    LIGHT(
        id = "light",
        title = "라이트 모드"
    ),
    DARK(
        id = "dark",
        title = "다크 모드"
    );

    companion object {
        val DEFAULT = SYSTEM

        fun fromId(id: String?): AppThemeMode {
            return entries.firstOrNull { it.id == id } ?: DEFAULT
        }
    }
}

/**
 * 소프트 3D 뉴모피즘 디자인 시스템을 위한 시맨틱 컬러 토큰.
 */
data class AppColors(
    val canvas: Color,
    val card: Color,
    val insetBed: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val cardBorder: Color,
    val shadowLight: Color,
    val shadowDark: Color,
    val insetBorder: Color,
    val inputBackground: Color = Color.White,
    val inputBorder: Color,
    val snippetPrimary: Color = accent,
    val snippetBorder: Color = accent.copy(alpha = 0.55f),
    val snippetBackground: Color = card,
    val snippetShadow: Color = accent.copy(alpha = 0.25f)
)

val CoralCreamColors = AppColors(
    canvas = Color(0xFFF6F2EA),
    card = Color(0xFFFAF6EE),
    insetBed = Color(0xFFF0EAE0),
    primary = Color(0xFFD9532F),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFFFF8A65),
    textPrimary = Color(0xFF2E2419),
    textSecondary = Color(0xFF7A6F62),
    cardBorder = Color(0xFFEDE4D5),
    shadowLight = Color(0xFFFFFFFF),
    shadowDark = Color(0xFFDCD4C7),
    insetBorder = Color(0xFFE4DAD0),
    inputBackground = Color(0xFFFFFFFF),
    inputBorder = Color(0xFFC4B6A3),
    snippetPrimary = Color(0xFF1B7A5A),
    snippetBorder = Color(0xFF1B7A5A).copy(alpha = 0.45f),
    snippetShadow = Color(0xFF1B7A5A).copy(alpha = 0.20f)
)

val SageMatchaColors = AppColors(
    canvas = Color(0xFFEBF0E9),
    card = Color(0xFFF2F6F0),
    insetBed = Color(0xFFE2EBE0),
    primary = Color(0xFF1B7A5A),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFF34D399),
    textPrimary = Color(0xFF14261C),
    textSecondary = Color(0xFF52665B),
    cardBorder = Color(0xFFDEE7DD),
    shadowLight = Color(0xFFFFFFFF),
    shadowDark = Color(0xFFC5D3C2),
    insetBorder = Color(0xFFD3DFD1),
    inputBackground = Color(0xFFFFFFFF),
    inputBorder = Color(0xFF9AB197),
    snippetPrimary = Color(0xFF2E5EB8),
    snippetBorder = Color(0xFF2E5EB8).copy(alpha = 0.45f),
    snippetShadow = Color(0xFF2E5EB8).copy(alpha = 0.20f)
)

val PureIndigoColors = AppColors(
    canvas = Color(0xFFEEF4FA),
    card = Color(0xFFF8FAFD),
    insetBed = Color(0xFFE6EEF8),
    primary = Color(0xFF2E5EB8),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFF6366F1),
    textPrimary = Color(0xFF0B1A30),
    textSecondary = Color(0xFF587494),
    cardBorder = Color(0xFFDEE9F5),
    shadowLight = Color(0xFFFFFFFF),
    shadowDark = Color(0xFFCCD9E8),
    insetBorder = Color(0xFFD5E3F2),
    inputBackground = Color(0xFFFFFFFF),
    inputBorder = Color(0xFF9EB7CF),
    snippetPrimary = Color(0xFFD9532F),
    snippetBorder = Color(0xFFD9532F).copy(alpha = 0.45f),
    snippetShadow = Color(0xFFD9532F).copy(alpha = 0.20f)
)

// --- 다크모드 팔레트 (검은색 배경 유지 + 어두운 버튼) ---

val CoralCreamDarkColors = AppColors(
    canvas = Color(0xFF0A0A0C),
    card = Color(0xFF16171A),
    insetBed = Color(0xFF101113),
    primary = Color(0xFF24272B),
    onPrimary = Color(0xFFEDEDF0),
    accent = Color(0xFFFF8A65),
    textPrimary = Color(0xFFEDEDF0),
    textSecondary = Color(0xFF8E929B),
    cardBorder = Color(0xFF332928),
    shadowLight = Color.Transparent,
    shadowDark = Color(0x66000000),
    insetBorder = Color(0xFF202227),
    inputBackground = Color(0xFF18191D),
    inputBorder = Color(0xFF3D3230),
    snippetPrimary = Color(0xFF34D399),
    snippetBorder = Color(0xFF205E44),
    snippetShadow = Color(0x40000000)
)

val SageMatchaDarkColors = AppColors(
    canvas = Color(0xFF0A0A0C),
    card = Color(0xFF16171A),
    insetBed = Color(0xFF101113),
    primary = Color(0xFF24272B),
    onPrimary = Color(0xFFEDEDF0),
    accent = Color(0xFF34D399),
    textPrimary = Color(0xFFEDEDF0),
    textSecondary = Color(0xFF8E929B),
    cardBorder = Color(0xFF26302A),
    shadowLight = Color.Transparent,
    shadowDark = Color(0x66000000),
    insetBorder = Color(0xFF202227),
    inputBackground = Color(0xFF18191D),
    inputBorder = Color(0xFF2D3830),
    snippetPrimary = Color(0xFF82AAFF),
    snippetBorder = Color(0xFF435B88),
    snippetShadow = Color(0x40000000)
)

val PureIndigoDarkColors = AppColors(
    canvas = Color(0xFF0A0A0C),
    card = Color(0xFF16171A),
    insetBed = Color(0xFF101113),
    primary = Color(0xFF24272B),
    onPrimary = Color(0xFFEDEDF0),
    accent = Color(0xFF6366F1),
    textPrimary = Color(0xFFEDEDF0),
    textSecondary = Color(0xFF8E929B),
    cardBorder = Color(0xFF262C36),
    shadowLight = Color.Transparent,
    shadowDark = Color(0x66000000),
    insetBorder = Color(0xFF202227),
    inputBackground = Color(0xFF18191D),
    inputBorder = Color(0xFF2D3545),
    snippetPrimary = Color(0xFFFF8A65),
    snippetBorder = Color(0xFF7A3B2B),
    snippetShadow = Color(0x40000000)
)

fun AppThemePalette.toAppColors(isDark: Boolean = false): AppColors {
    return if (isDark) {
        when (this) {
            AppThemePalette.CORAL_CREAM -> CoralCreamDarkColors
            AppThemePalette.SAGE_MATCHA -> SageMatchaDarkColors
            AppThemePalette.PURE_INDIGO -> PureIndigoDarkColors
        }
    } else {
        when (this) {
            AppThemePalette.CORAL_CREAM -> CoralCreamColors
            AppThemePalette.SAGE_MATCHA -> SageMatchaColors
            AppThemePalette.PURE_INDIGO -> PureIndigoColors
        }
    }
}

val LocalAppColors = staticCompositionLocalOf { SageMatchaColors }
val LocalAppThemePalette = staticCompositionLocalOf { AppThemePalette.DEFAULT }
val LocalAppThemeMode = staticCompositionLocalOf { AppThemeMode.DEFAULT }

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current

    val palette: AppThemePalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAppThemePalette.current

    val mode: AppThemeMode
        @Composable
        @ReadOnlyComposable
        get() = LocalAppThemeMode.current
}
