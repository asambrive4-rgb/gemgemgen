// 역할: 앱에서 지원하는 4종 토스 스타일 테마 색상 팔레트(토스 시그니처 기본 포함)와 시맨틱 컬러 토큰을 정의합니다.
package com.example.gemgemgen.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * GemGemGen 앱의 4가지 토스 스타일(Clean & Borderless) 테마 색상 팔레트.
 * 기본값은 TOSS_SIGNATURE (팔레트 0: 토스 시그니처)입니다.
 */
enum class AppThemePalette(
    val id: String,
    val title: String,
    val description: String,
    val previewColor: Color
) {
    TOSS_SIGNATURE(
        id = "toss_signature",
        title = "팔레트 0: 토스 시그니처 (기본값)",
        description = "차분한 쿨 그레이 배경과 순백색 카드, 선명한 토스 블루",
        previewColor = Color(0xFF3182F6)
    ),
    CORAL_CREAM(
        id = "coral_cream",
        title = "팔레트 1: 웜 코랄 크림",
        description = "포근한 웜 스톤 배경과 순백색 카드, 생동감 있는 테라코타 코랄",
        previewColor = Color(0xFFE55A34)
    ),
    SAGE_MATCHA(
        id = "sage_matcha",
        title = "팔레트 2: 노르딕 세이지",
        description = "산뜻한 미스트 그린 배경과 순백색 카드, 차분한 딥 세이지",
        previewColor = Color(0xFF15805D)
    ),
    PURE_INDIGO(
        id = "pure_indigo",
        title = "팔레트 3: 퓨어 인디고",
        description = "맑은 아이스 블루 배경과 순백색 카드, 세련된 로얄 인디고",
        previewColor = Color(0xFF4F46E5)
    );

    companion object {
        val DEFAULT = TOSS_SIGNATURE

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
 * 토스 스타일(Clean & Borderless) 디자인 시스템을 위한 시맨틱 컬러 토큰.
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
    val shadowDark: Color,
    val insetBorder: Color,
    val inputBackground: Color = Color.White,
    val inputBorder: Color,
    val snippetPrimary: Color = accent,
    val snippetBorder: Color = accent.copy(alpha = 0.55f),
    val snippetShadow: Color = accent.copy(alpha = 0.25f),
    val wildcardItemPrimary: Color = Color(0xFF7C3AED),
    val wildcardItemBorder: Color = Color(0xFF7C3AED).copy(alpha = 0.45f)
)

val TossSignatureColors = AppColors(
    canvas = Color(0xFFE9EDF2),
    card = Color(0xFFFFFFFF),
    insetBed = Color(0xFFF2F5F8),
    primary = Color(0xFF3182F6),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFF00C471),
    textPrimary = Color(0xFF191F28),
    textSecondary = Color(0xFF8B95A1),
    cardBorder = Color(0xFFE5E8EB),
    shadowDark = Color(0xFFCBD5E1),
    insetBorder = Color(0xFFE8ECEF),
    inputBackground = Color(0xFFFFFFFF),
    inputBorder = Color(0xFFD1D6DB),
    snippetPrimary = Color(0xFF00C471),
    snippetBorder = Color(0xFF00C471).copy(alpha = 0.45f),
    snippetShadow = Color(0xFF00C471).copy(alpha = 0.20f),
    wildcardItemPrimary = Color(0xFF7C3AED),
    wildcardItemBorder = Color(0xFF7C3AED).copy(alpha = 0.45f)
)

val CoralCreamColors = AppColors(
    canvas = Color(0xFFEAE3DC),
    card = Color(0xFFFFFFFF),
    insetBed = Color(0xFFF4EFEA),
    primary = Color(0xFFE55A34),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFFFF8A65),
    textPrimary = Color(0xFF221C16),
    textSecondary = Color(0xFF7E7266),
    cardBorder = Color(0xFFE8E1D7),
    shadowDark = Color(0xFFDCD4C7),
    insetBorder = Color(0xFFEDE7DF),
    inputBackground = Color(0xFFFFFFFF),
    inputBorder = Color(0xFFD5CBBF),
    snippetPrimary = Color(0xFF15805D),
    snippetBorder = Color(0xFF15805D).copy(alpha = 0.45f),
    snippetShadow = Color(0xFF15805D).copy(alpha = 0.20f),
    wildcardItemPrimary = Color(0xFF4338CA),
    wildcardItemBorder = Color(0xFF4338CA).copy(alpha = 0.45f)
)

val SageMatchaColors = AppColors(
    canvas = Color(0xFFE2EBE4),
    card = Color(0xFFFFFFFF),
    insetBed = Color(0xFFEDF3EF),
    primary = Color(0xFF15805D),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFF2563EB),
    textPrimary = Color(0xFF14221B),
    textSecondary = Color(0xFF64786D),
    cardBorder = Color(0xFFE0E8E2),
    shadowDark = Color(0xFFC5D3C2),
    insetBorder = Color(0xFFE4EDE6),
    inputBackground = Color(0xFFFFFFFF),
    inputBorder = Color(0xFFC4D4C8),
    snippetPrimary = Color(0xFF2563EB),
    snippetBorder = Color(0xFF2563EB).copy(alpha = 0.45f),
    snippetShadow = Color(0xFF2563EB).copy(alpha = 0.20f),
    wildcardItemPrimary = Color(0xFFD97706),
    wildcardItemBorder = Color(0xFFD97706).copy(alpha = 0.45f)
)

val PureIndigoColors = AppColors(
    canvas = Color(0xFFE4EAF3),
    card = Color(0xFFFFFFFF),
    insetBed = Color(0xFFF0F4FA),
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFFEC4899),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF64748B),
    cardBorder = Color(0xFFE2E8F0),
    shadowDark = Color(0xFFCCD9E8),
    insetBorder = Color(0xFFE6ECF5),
    inputBackground = Color(0xFFFFFFFF),
    inputBorder = Color(0xFFCBD5E1),
    snippetPrimary = Color(0xFFE55A34),
    snippetBorder = Color(0xFFE55A34).copy(alpha = 0.45f),
    snippetShadow = Color(0xFFE55A34).copy(alpha = 0.20f),
    wildcardItemPrimary = Color(0xFF0D9488),
    wildcardItemBorder = Color(0xFF0D9488).copy(alpha = 0.45f)
)

// --- 다크모드 팔레트 (선명한 3단계 표면 대비: Level 1 마일드 토스 다크: 캔버스 #0D1117 -> 부유 카드 #1C222D -> 인셋 베드 #131720) ---

private fun createDarkAppColors(
    primary: Color,
    accent: Color,
    cardBorder: Color,
    inputBorder: Color,
    snippetPrimary: Color,
    snippetBorder: Color,
    wildcardItemPrimary: Color = Color(0xFFA78BFA),
    wildcardItemBorder: Color = Color(0xFF5B21B6)
): AppColors = AppColors(
    canvas = Color(0xFF0D1117),
    card = Color(0xFF1C222D),
    insetBed = Color(0xFF131720),
    primary = primary,
    onPrimary = Color(0xFFFFFFFF),
    accent = accent,
    textPrimary = Color(0xFFEDEDF0),
    textSecondary = Color(0xFF9EA6B2),
    cardBorder = cardBorder,
    shadowDark = Color(0x80000000),
    insetBorder = Color(0xFF1E2430),
    inputBackground = Color(0xFF131720),
    inputBorder = inputBorder,
    snippetPrimary = snippetPrimary,
    snippetBorder = snippetBorder,
    snippetShadow = Color(0x40000000),
    wildcardItemPrimary = wildcardItemPrimary,
    wildcardItemBorder = wildcardItemBorder
)

val TossSignatureDarkColors = createDarkAppColors(
    primary = Color(0xFF3182F6),
    accent = Color(0xFF00C471),
    cardBorder = Color(0xFF252830),
    inputBorder = Color(0xFF2E323C),
    snippetPrimary = Color(0xFF00C471),
    snippetBorder = Color(0xFF1A5C40),
    wildcardItemPrimary = Color(0xFFA78BFA),
    wildcardItemBorder = Color(0xFF5B21B6)
)

val CoralCreamDarkColors = createDarkAppColors(
    primary = Color(0xFFE55A34),
    accent = Color(0xFFFF8A65),
    cardBorder = Color(0xFF332928),
    inputBorder = Color(0xFF3D3230),
    snippetPrimary = Color(0xFF34D399),
    snippetBorder = Color(0xFF205E44),
    wildcardItemPrimary = Color(0xFF818CF8),
    wildcardItemBorder = Color(0xFF3730A3)
)

val SageMatchaDarkColors = createDarkAppColors(
    primary = Color(0xFF15805D),
    accent = Color(0xFF34D399),
    cardBorder = Color(0xFF26302A),
    inputBorder = Color(0xFF2D3830),
    snippetPrimary = Color(0xFF82AAFF),
    snippetBorder = Color(0xFF435B88),
    wildcardItemPrimary = Color(0xFFFBBF24),
    wildcardItemBorder = Color(0xFF92400E)
)

val PureIndigoDarkColors = createDarkAppColors(
    primary = Color(0xFF6366F1),
    accent = Color(0xFFEC4899),
    cardBorder = Color(0xFF262C36),
    inputBorder = Color(0xFF2D3545),
    snippetPrimary = Color(0xFFFF8A65),
    snippetBorder = Color(0xFF7A3B2B),
    wildcardItemPrimary = Color(0xFF2DD4BF),
    wildcardItemBorder = Color(0xFF115E59)
)

fun AppThemePalette.toAppColors(isDark: Boolean = false): AppColors {
    return if (isDark) {
        when (this) {
            AppThemePalette.TOSS_SIGNATURE -> TossSignatureDarkColors
            AppThemePalette.CORAL_CREAM -> CoralCreamDarkColors
            AppThemePalette.SAGE_MATCHA -> SageMatchaDarkColors
            AppThemePalette.PURE_INDIGO -> PureIndigoDarkColors
        }
    } else {
        when (this) {
            AppThemePalette.TOSS_SIGNATURE -> TossSignatureColors
            AppThemePalette.CORAL_CREAM -> CoralCreamColors
            AppThemePalette.SAGE_MATCHA -> SageMatchaColors
            AppThemePalette.PURE_INDIGO -> PureIndigoColors
        }
    }
}

val LocalAppColors = staticCompositionLocalOf { TossSignatureColors }

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}
