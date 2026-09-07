package com.example.gemgemgen.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val RemoteModePurple = Color(0xFF7E57C2)
val RemoteModePurpleDark = Color(0xFFB39DDB)
val RemoteStartGreen = Color(0xFF2E7D32)
val RemoteStartGreenDark = Color(0xFF66BB6A)
val OnRemoteStartGreenDark = Color(0xFF102510)

/**
 * 와일드카드 화면의 색상을 전역 소프트 3D 뉴모피즘 테마 팔레트(AppTheme.colors)로 동적 매핑합니다.
 */
val MemoPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.primary

val MemoPrimaryDark: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.accent

val MemoPaste: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.card

val MemoSave: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.primary

val MemoCopy: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.card

val MemoCopyBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.cardBorder

val MemoSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.card

val MemoBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.canvas

val MemoTabBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.cardBorder

val MemoTabSelected: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.primary

val MemoTabSelectedBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.primary

val MemoStripBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.cardBorder

val MemoEditorBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.cardBorder

val MemoUndoBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.cardBorder

val MemoDanger = Color(0xFFD9544D)
val MemoDangerBorder = Color(0xFFF0A8A1)

val MemoText: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.textPrimary

val MemoSubtle: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.textSecondary
