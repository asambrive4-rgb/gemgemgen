// 역할: 부드러운 입체 그림자 효과를 가진 뉴모피즘 카드 및 버튼 UI 요소를 제공합니다.
package com.example.gemgemgen.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 3D 조약돌 카드 (Extruded Pebble Card)
 * 부드러운 엠보싱 섀도우와 미세 테두리로 바닥에서 솟아오른 듯한 깊이감을 줍니다.
 */
@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    backgroundColor: Color = AppTheme.colors.card,
    borderColor: Color = AppTheme.colors.cardBorder,
    elevation: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.5f),
                spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.4f)
            )
            .border(BorderStroke(1.dp, borderColor), shape),
        shape = shape,
        color = backgroundColor,
        content = content
    )
}

/**
 * 3D 음각 인셋 베드 (Inset Bed)
 * 텍스트 입력창이나 탭바 트랙처럼 바닥으로 오목하게 들어간 느낌을 줍니다.
 */
@Composable
fun NeuInsetBed(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = AppTheme.colors.insetBed,
    borderColor: Color = AppTheme.colors.insetBorder,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(BorderStroke(1.dp, borderColor), shape)
    ) {
        content()
    }
}

/**
 * 3D 쫀득한 조약돌 칩 (Pebble Pill Chip)
 */
@Composable
fun NeuPillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    enabled: Boolean = true
) {
    val containerColor = if (selected) {
        AppTheme.colors.primary
    } else {
        AppTheme.colors.card
    }
    val contentColor = if (selected) {
        AppTheme.colors.onPrimary
    } else {
        AppTheme.colors.textSecondary
    }
    val borderColor = if (selected) {
        AppTheme.colors.primary
    } else {
        AppTheme.colors.cardBorder
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (selected) 4.dp else 2.dp,
                shape = shape,
                ambientColor = if (selected) AppTheme.colors.primary.copy(alpha = 0.4f) else AppTheme.colors.shadowDark.copy(alpha = 0.3f),
                spotColor = if (selected) AppTheme.colors.primary.copy(alpha = 0.3f) else AppTheme.colors.shadowDark.copy(alpha = 0.2f)
            )
            .clip(shape)
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

/**
 * 3D 쫀득한 클레이 버튼 (Clay Pill Button)
 */
@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable RowScope.() -> Unit
) {
    val backgroundColor = if (isPrimary) AppTheme.colors.primary else AppTheme.colors.card
    val contentColor = if (isPrimary) AppTheme.colors.onPrimary else AppTheme.colors.textPrimary
    val borderColor = if (isPrimary) AppTheme.colors.primary else AppTheme.colors.cardBorder
    val elevation = if (isPrimary) 6.dp else 3.dp

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
        modifier = modifier
            .shadow(
                elevation = if (enabled) elevation else 0.dp,
                shape = shape,
                ambientColor = if (isPrimary) AppTheme.colors.primary.copy(alpha = 0.4f) else AppTheme.colors.shadowDark.copy(alpha = 0.3f),
                spotColor = if (isPrimary) AppTheme.colors.primary.copy(alpha = 0.3f) else AppTheme.colors.shadowDark.copy(alpha = 0.2f)
            )
            .border(BorderStroke(1.dp, borderColor), shape)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * 3D 뉴모피즘 텍스트 입력창 전용 컬러 체계.
 * 배경은 캔버스와 뚜렷하게 대비되는 순백의 클린 베드(inputBackground),
 * 테두리는 흐리지 않고 선명한 팔레트 테두리(inputBorder)와 활성 포커스(primary)를 적용합니다.
 */
@Composable
fun appTextFieldColors(
    containerColor: Color = AppTheme.colors.inputBackground,
    unfocusedBorderColor: Color = AppTheme.colors.inputBorder,
    focusedBorderColor: Color = AppTheme.colors.primary,
    textColor: Color = AppTheme.colors.textPrimary,
    cursorColor: Color = AppTheme.colors.primary,
    placeholderColor: Color = AppTheme.colors.textSecondary.copy(alpha = 0.6f)
): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = containerColor,
    unfocusedContainerColor = containerColor,
    disabledContainerColor = containerColor.copy(alpha = 0.6f),
    focusedBorderColor = focusedBorderColor,
    unfocusedBorderColor = unfocusedBorderColor,
    disabledBorderColor = unfocusedBorderColor.copy(alpha = 0.5f),
    focusedTextColor = textColor,
    unfocusedTextColor = textColor,
    disabledTextColor = textColor.copy(alpha = 0.5f),
    cursorColor = cursorColor,
    focusedPlaceholderColor = placeholderColor,
    unfocusedPlaceholderColor = placeholderColor,
    focusedSupportingTextColor = AppTheme.colors.textSecondary,
    unfocusedSupportingTextColor = AppTheme.colors.textSecondary,
)

