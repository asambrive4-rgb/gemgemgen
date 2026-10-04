// 역할: 볼드 테두리와 섀도우를 적용한 뉴모피즘 카드, 버튼, 인셋 베드, 칩, 세그먼트 탭바 및 공통 모달·확인 다이얼로그 셸 UI 요소를 제공합니다.
package com.example.gemgemgen.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gemgemgen.ui.clearFocusOnOutsideTap

/**
 * 3D 조약돌 카드 (Extruded Pebble Card)
 * 부드러운 엠보싱 섀도우와 선명한 볼드 테두리로 바닥에서 솟아오른 듯한 깊이감을 줍니다.
 */
@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
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
            .border(BorderStroke(2.dp, borderColor), shape),
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
    shape: Shape = RoundedCornerShape(14.dp),
    backgroundColor: Color = AppTheme.colors.insetBed,
    borderColor: Color = AppTheme.colors.insetBorder,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(BorderStroke(1.5.dp, borderColor), shape)
    ) {
        content()
    }
}

/**
 * 3D 쫀득한 조약돌 칩 (Pebble Pill Chip)
 */
/**
 * 3D 쫀득한 조약돌 칩 (Pebble Pill Chip)
 */
@Composable
fun NeuPillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    enabled: Boolean = true,
    height: Dp? = null,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium
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
    val borderStroke = if (selected) {
        BorderStroke(1.5.dp, AppTheme.colors.primary)
    } else {
        null
    }

    Box(
        modifier = modifier
            .then(if (height != null) Modifier.height(height) else Modifier)
            .shadow(
                elevation = if (selected) 3.dp else 0.dp,
                shape = shape,
                ambientColor = if (selected) AppTheme.colors.primary.copy(alpha = 0.35f) else AppTheme.colors.shadowDark.copy(alpha = 0.2f),
                spotColor = if (selected) AppTheme.colors.primary.copy(alpha = 0.25f) else AppTheme.colors.shadowDark.copy(alpha = 0.15f)
            )
            .clip(shape)
            .background(containerColor)
            .then(if (borderStroke != null) Modifier.border(borderStroke, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (height != null) 3.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            style = textStyle,
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
    contentModifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
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
            .border(BorderStroke(1.5.dp, borderColor), shape)
    ) {
        Row(
            modifier = contentModifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * 3D 뉴모피즘 텍스트 입력창 전용 컬러 체계.
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

/**
 * 단일 다이얼로그 호스트(Single Dialog Host) 공통 외곽 셸.
 * 크로스페이드 전환 애니메이션, 키보드 인셋, 외부 탭 시 포커스 해제를 일관되게 제공합니다.
 */
@Composable
fun <T : Any> AppDialogHostShell(
    activeDialog: T,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    isDismissible: Boolean = true,
    contentKey: (T) -> Any = { it::class },
    label: String = "AppDialogHostCrossfade",
    content: @Composable (T) -> Unit
) {
    val focusManager = LocalFocusManager.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = isDismissible,
            dismissOnClickOutside = isDismissible,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = modifier
                .padding(24.dp)
                .imePadding()
                .widthIn(min = 280.dp, max = 560.dp)
                .fillMaxWidth()
                .wrapContentHeight()
                .clearFocusOnOutsideTap { focusManager.clearFocus(force = true) },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            AnimatedContent(
                targetState = activeDialog,
                contentKey = contentKey,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
                },
                contentAlignment = Alignment.Center,
                label = label
            ) { targetDialog ->
                content(targetDialog)
            }
        }
    }
}

/**
 * 제목, 안내 본문, 취소/확인 버튼으로 구성된 공통 확인 다이얼로그 컨텐츠.
 */
@Composable
fun AppConfirmDialogContent(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String = "취소",
    useFilledConfirmButton: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (useFilledConfirmButton) {
                Button(onClick = onConfirm) {
                    Text(confirmLabel)
                }
            } else {
                TextButton(onClick = onConfirm) {
                    Text(confirmLabel)
                }
            }
        }
    }
}

/**
 * 3D 뉴모피즘 음각 베드 기반 세그먼트 탭바 (Segmented Tab Bar).
 * 메인 상단 탭, 모드 선택, 다이얼로그 내 세그먼트 전환 등에서 일관된 조약돌 탭 UI를 제공합니다.
 */
@Composable
fun <T> NeuSegmentedTabBar(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemLabel: (T) -> String = { it.toString() },
    enabled: Boolean = true,
    height: Dp = 38.dp,
    shape: Shape = RoundedCornerShape(14.dp),
    pillShape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(3.dp),
    tabSpacing: Dp = 0.dp
) {
    NeuInsetBed(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = shape,
        backgroundColor = AppTheme.colors.insetBed,
        borderColor = AppTheme.colors.insetBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (tabSpacing > 0.dp) Arrangement.spacedBy(tabSpacing) else Arrangement.Start
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (isSelected) {
                                Modifier.shadow(
                                    elevation = 2.dp,
                                    shape = pillShape,
                                    ambientColor = AppTheme.colors.primary.copy(alpha = 0.35f),
                                    spotColor = AppTheme.colors.primary.copy(alpha = 0.25f)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clip(pillShape)
                        .background(
                            if (isSelected) AppTheme.colors.primary else Color.Transparent
                        )
                        .clickable(enabled = enabled) {
                            if (item != selectedItem) {
                                onItemSelected(item)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = itemLabel(item),
                        color = if (isSelected) AppTheme.colors.onPrimary else AppTheme.colors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 3D 뉴모피즘 모달 다이얼로그 공통 외곽 프레임.
 * 크기 비율, 키보드 인셋(imePadding), 외부 탭 포커스 해제, 3D 조약돌 카드 컨테이너를 일관되게 제공합니다.
 */
@Composable
fun NeuModalDialogFrame(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.95f,
    content: @Composable ColumnScope.() -> Unit
) {
    val focusManager = LocalFocusManager.current
    val clearInputFocus = remember(focusManager) {
        { focusManager.clearFocus(force = true) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth(widthFraction)
                .wrapContentHeight()
                .imePadding()
                .clearFocusOnOutsideTap(clearInputFocus),
            contentAlignment = Alignment.Center
        ) {
            NeuCard(
                shape = RoundedCornerShape(22.dp),
                elevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

/**
 * 상단 제목/설명과 하단 취소/저장 액션 버튼을 갖춘 공통 설정 다이얼로그 프레임.
 */
@Composable
fun NeuConfigDialogFrame(
    title: String,
    subtitle: String,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    saveLabel: String = "저장",
    dismissLabel: String = "취소",
    content: @Composable ColumnScope.() -> Unit
) {
    NeuModalDialogFrame(
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                lineHeight = 16.sp
            )
        }

        content()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NeuButton(
                onClick = onDismiss,
                isPrimary = false,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
                contentModifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = dismissLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            NeuButton(
                onClick = onSave,
                isPrimary = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
                contentModifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = saveLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 다이얼로그 내부나 툴바에서 빠른 동작(가져오기, 기본값, 복사 등)을 수행하는 컴팩트 조약돌 액션 칩.
 */
@Composable
fun NeuActionChip(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeuCard(
        shape = RoundedCornerShape(8.dp),
        elevation = 2.dp,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = text }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = AppTheme.colors.primary
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
        }
    }
}

