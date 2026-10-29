// 역할: 토스 스타일의 무테두리(Borderless) 고대비 카드, 버튼, 인셋 베드, 칩, 드롭다운 메뉴(NeuDropdownMenu), 세그먼트 탭바 및 공통 모달·확인 다이얼로그 UI 요소를 제공합니다.
package com.example.gemgemgen.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import com.example.gemgemgen.ui.clearFocusOnOutsideTap

/**
 * 토스 스타일 무테두리 클린 카드 (Borderless Clean Card)
 * 외곽선 없이 배경과 카드의 명도 대비 및 은은한 소프트 그림자로 깔끔한 단계별 분리감을 제공합니다.
 */
@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = AppTheme.colors.card,
    @Suppress("UNUSED_PARAMETER") borderColor: Color = Color.Transparent,
    elevation: Dp = 2.dp,
    content: @Composable () -> Unit
) {
    val shadowModifier = if (elevation > 0.dp) {
        modifier.shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.22f),
            spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.16f)
        )
    } else {
        modifier
    }
    Surface(
        modifier = shadowModifier,
        shape = shape,
        color = backgroundColor,
        content = content
    )
}

/**
 * 토스 스타일 무테두리 인셋 베드 (Borderless Inset Bed)
 * 테두리 선 없이 부드러운 톤온톤 면(Surface) 색상 차이만으로 입력 영역과 트랙을 분리합니다.
 */
@Composable
fun NeuInsetBed(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    backgroundColor: Color = AppTheme.colors.insetBed,
    @Suppress("UNUSED_PARAMETER") borderColor: Color = Color.Transparent,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
    ) {
        content()
    }
}

/**
 * 토스 스타일 무테두리 알약 칩 (Clean Pill Chip)
 */
@Composable
fun NeuPillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    enabled: Boolean = true,
    height: Dp? = null,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium
) {
    val containerColor = if (selected) {
        AppTheme.colors.primary
    } else {
        AppTheme.colors.insetBed
    }
    val contentColor = if (selected) {
        AppTheme.colors.onPrimary
    } else {
        AppTheme.colors.textSecondary
    }

    Box(
        modifier = modifier
            .then(if (height != null) Modifier.height(height) else Modifier)
            .clip(shape)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (height != null) 3.dp else 7.dp),
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
 * 토스 스타일 무테두리 솔리드 버튼 (Clean Solid Button)
 */
@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color? = null,
    @Suppress("UNUSED_PARAMETER") borderColor: Color? = null,
    elevation: Dp? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val defaultBg = if (isPrimary) AppTheme.colors.primary else AppTheme.colors.insetBed
    val defaultContent = if (isPrimary) AppTheme.colors.onPrimary else AppTheme.colors.textPrimary
    val defaultElevation = if (isPrimary) 2.dp else 0.dp

    val resolvedBg = containerColor ?: defaultBg
    val resolvedContent = contentColor ?: defaultContent
    val resolvedElevation = elevation ?: defaultElevation

    val shadowBaseColor = AppTheme.colors.shadowDark

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = if (enabled) resolvedBg else resolvedBg.copy(alpha = 0.45f),
        contentColor = if (enabled) resolvedContent else resolvedContent.copy(alpha = 0.45f),
        modifier = modifier.shadow(
            elevation = if (enabled) resolvedElevation else 0.dp,
            shape = shape,
            ambientColor = shadowBaseColor.copy(alpha = 0.25f),
            spotColor = shadowBaseColor.copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = contentModifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * 토스 스타일 무테두리 인셋 텍스트 입력창 전용 컬러 체계.
 * 비포커스 시에는 테두리를 투명하게 숨기고 부드러운 인셋 면(`insetBed`)으로 표시하며,
 * 포커스 시에만 얇은 포인트 컬러로 활성 상태를 안내합니다.
 */
@Composable
fun appTextFieldColors(
    containerColor: Color = AppTheme.colors.insetBed,
    unfocusedBorderColor: Color = Color.Transparent,
    focusedBorderColor: Color = AppTheme.colors.primary.copy(alpha = 0.55f),
    textColor: Color = AppTheme.colors.textPrimary,
    cursorColor: Color = AppTheme.colors.primary,
    placeholderColor: Color = AppTheme.colors.textSecondary.copy(alpha = 0.65f)
): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = containerColor,
    unfocusedContainerColor = containerColor,
    disabledContainerColor = containerColor.copy(alpha = 0.6f),
    focusedBorderColor = focusedBorderColor,
    unfocusedBorderColor = unfocusedBorderColor,
    disabledBorderColor = Color.Transparent,
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
            tonalElevation = 4.dp
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
    @Suppress("UNUSED_PARAMETER") useFilledConfirmButton: Boolean = false,
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
            color = AppTheme.colors.textPrimary
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NeuButton(
                onClick = onDismiss,
                isPrimary = false,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
                contentModifier = Modifier.fillMaxWidth()
            ) {
                Text(dismissLabel)
            }
            NeuButton(
                onClick = onConfirm,
                isPrimary = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
                contentModifier = Modifier.fillMaxWidth()
            ) {
                Text(confirmLabel)
            }
        }
    }
}

/**
 * 토스 스타일 세그먼트 탭바 (Clean Segmented Tab Bar).
 * 무테두리 인셋 트랙 내부에 선택된 탭을 깨끗한 카드 서피스로 띄워 표시합니다.
 */
@Composable
fun <T> NeuSegmentedTabBar(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemLabel: (T) -> String = { it.toString() },
    enabled: Boolean = true,
    height: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    pillShape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(4.dp),
    tabSpacing: Dp = 0.dp
) {
    NeuInsetBed(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = shape,
        backgroundColor = AppTheme.colors.insetBed
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
                                    ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.25f),
                                    spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.18f)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clip(pillShape)
                        .background(
                            if (isSelected) AppTheme.colors.card else Color.Transparent
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
                        color = if (isSelected) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 모달 다이얼로그 공통 외곽 프레임.
 * 크기 비율, 키보드 인셋(imePadding), 외부 탭 포커스 해제, 무테두리 카드 컨테이너를 일관되게 제공합니다.
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
                shape = RoundedCornerShape(24.dp),
                elevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

/**
 * 상단 제목/설명과 하단 취소/확인 액션 버튼을 갖춘 공통 설정 다이얼로그 컨텐츠.
 */
@Composable
fun NeuConfigDialogContent(
    title: String,
    subtitle: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = "저장",
    dismissLabel: String = "취소",
    confirmEnabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    useNeuButtons: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val titleStyle = if (useNeuButtons) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall
    val titleColor = if (useNeuButtons) AppTheme.colors.textPrimary else MaterialTheme.colorScheme.onSurface
    val subtitleStyle = if (useNeuButtons) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    val subtitleColor = if (useNeuButtons) AppTheme.colors.textSecondary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(if (useNeuButtons) 12.dp else 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = subtitleStyle,
                    color = subtitleColor,
                    lineHeight = if (useNeuButtons) 16.sp else 20.sp
                )
            }
        }

        content()

        if (useNeuButtons) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NeuButton(
                    onClick = onDismiss,
                    isPrimary = false,
                    shape = RoundedCornerShape(14.dp),
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
                    onClick = onConfirm,
                    isPrimary = true,
                    enabled = confirmEnabled,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                    contentModifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = confirmLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(dismissLabel)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onConfirm,
                    enabled = confirmEnabled
                ) {
                    Text(confirmLabel)
                }
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
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    NeuModalDialogFrame(
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        NeuConfigDialogContent(
            title = title,
            subtitle = subtitle.ifBlank { null },
            onConfirm = onSave,
            onDismiss = onDismiss,
            confirmLabel = saveLabel,
            dismissLabel = dismissLabel,
            confirmEnabled = confirmEnabled,
            contentPadding = PaddingValues(0.dp),
            useNeuButtons = true,
            content = content
        )
    }
}

/**
 * 다이얼로그 내부나 툴바에서 빠른 동작(가져오기, 기본값, 복사 등)을 수행하는 컴팩트 무테두리 액션 칩.
 */
@Composable
fun NeuActionChip(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppTheme.colors.insetBed)
            .clickable(onClick = onClick)
            .semantics { contentDescription = text }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
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
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * 토스 스타일 무테두리 클린 드롭다운 메뉴 (Clean Dropdown Menu).
 * 계정 사용량 더보기 양식(16dp 라운딩, AppTheme.colors.card 배경, 8dp 그림자)을 기준으로 앱 전역에 일관된 팝업 메뉴를 제공합니다.
 */
@Composable
fun NeuDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = AppTheme.colors.card,
    shadowElevation: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        properties = properties,
        shape = shape,
        containerColor = containerColor,
        shadowElevation = shadowElevation,
        content = content
    )
}

/**
 * 드롭다운 메뉴 내부 항목 간의 부드러운 토스 스타일 구분선.
 */
@Composable
fun NeuDropdownMenuDivider(
    modifier: Modifier = Modifier,
    paddingHorizontal: Dp = 12.dp,
    color: Color = AppTheme.colors.insetBorder.copy(alpha = 0.65f),
    thickness: Dp = 0.8.dp
) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = paddingHorizontal),
        color = color,
        thickness = thickness
    )
}

/**
 * 토스 스타일 드롭다운 메뉴 아이템 (Clean Dropdown Menu Item).
 * 텍스트, 좌측 아이콘, 선택 시 우측 체크 아이콘 표시를 일관된 typography 및 색상으로 제공합니다.
 */
@Composable
fun NeuDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    textColor: Color = if (isSelected) AppTheme.colors.primary else AppTheme.colors.textPrimary,
    iconTint: Color = if (isSelected) AppTheme.colors.primary else AppTheme.colors.textSecondary,
    enabled: Boolean = true
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        },
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        trailingIcon = (trailingIcon ?: if (isSelected) Icons.Default.Check else null)?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (isSelected) AppTheme.colors.primary else AppTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    )
}
