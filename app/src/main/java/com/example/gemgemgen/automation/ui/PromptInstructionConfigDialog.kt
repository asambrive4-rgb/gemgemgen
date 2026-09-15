// 역할: 상단 및 하단 프롬프트 인스트럭션 문구를 편집, 복원, 클립보드에서 가져와 영구 저장하는 모달 다이얼로그를 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gemgemgen.automation.domain.InstructionTab
import com.example.gemgemgen.automation.domain.PromptInstructionConfig
import com.example.gemgemgen.ui.clearFocusOnOutsideTap
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuInsetBed
import com.example.gemgemgen.ui.theme.NeuPillChip
import com.example.gemgemgen.ui.theme.appTextFieldColors

@Composable
fun PromptInstructionConfigDialog(
    showDialog: Boolean,
    config: PromptInstructionConfig,
    initialTab: InstructionTab = InstructionTab.TOP,
    onSave: (PromptInstructionConfig) -> Unit,
    onDismiss: () -> Unit
) {
    if (!showDialog) return

    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val clearInputFocus = remember(focusManager) {
        { focusManager.clearFocus(force = true) }
    }

    var activeTab by remember(initialTab, showDialog) { mutableStateOf(initialTab) }
    var topText by remember(config.topInstruction, showDialog) { mutableStateOf(config.topInstruction) }
    var bottomText by remember(config.bottomInstruction, showDialog) { mutableStateOf(config.bottomInstruction.orEmpty()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
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
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. 헤더 (타이틀 및 부제)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "인스트럭션 문구 관리",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "상단/하단 삽입 버튼에 적용될 문구를 설정합니다. (영구 보관)",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary
                        )
                    }

                    // 2. 탭 선택 (상단 삽입 / 하단 삽입)
                    NeuInsetBed(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                NeuPillChip(
                                    text = "상단 삽입 문구",
                                    selected = activeTab == InstructionTab.TOP,
                                    onClick = { activeTab = InstructionTab.TOP },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                NeuPillChip(
                                    text = "하단 삽입 문구",
                                    selected = activeTab == InstructionTab.BOTTOM,
                                    onClick = { activeTab = InstructionTab.BOTTOM },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // 3. 간편 기능 툴바 (클립보드 가져오기, 기본값 복원, 복사)
                    val currentText = if (activeTab == InstructionTab.TOP) topText else bottomText
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activeTab == InstructionTab.TOP) "상단 인스트럭션" else "하단 인스트럭션",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.primary
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 클립보드에서 가져오기
                            PebbleSmallActionChip(
                                text = "가져오기",
                                icon = Icons.Default.ContentPaste,
                                onClick = {
                                    val clip = clipboardManager.getText()?.text.orEmpty()
                                    if (clip.isNotBlank()) {
                                        if (activeTab == InstructionTab.TOP) {
                                            topText = clip
                                        } else {
                                            bottomText = clip
                                        }
                                    }
                                }
                            )

                            // 기본값 복원
                            PebbleSmallActionChip(
                                text = "기본값",
                                icon = Icons.Default.Restore,
                                onClick = {
                                    if (activeTab == InstructionTab.TOP) {
                                        topText = PromptInstructionConfig.DEFAULT_TOP_INSTRUCTION
                                    } else {
                                        bottomText = ""
                                    }
                                }
                            )

                            // 복사
                            PebbleSmallActionChip(
                                text = "복사",
                                icon = Icons.Default.ContentCopy,
                                onClick = {
                                    if (currentText.isNotBlank()) {
                                        clipboardManager.setText(AnnotatedString(currentText))
                                    }
                                }
                            )
                        }
                    }

                    // 4. 에디터 본문 (AnimatedContent)
                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "InstructionEditorCrossfade"
                    ) { tab ->
                        val textValue = if (tab == InstructionTab.TOP) topText else bottomText
                        val placeholderText = if (tab == InstructionTab.TOP) {
                            "프롬프트 맨 위에 삽입될 시스템 인스트럭션을 입력하세요."
                        } else {
                            "프롬프트 맨 아래에 삽입될 문구를 입력하세요. (기본값: 비어있음)"
                        }

                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { newText ->
                                if (tab == InstructionTab.TOP) {
                                    topText = newText
                                } else {
                                    bottomText = newText
                                }
                            },
                            placeholder = {
                                Text(
                                    text = placeholderText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary.copy(alpha = 0.6f)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 250.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = appTextFieldColors(),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = AppTheme.colors.textPrimary,
                                lineHeight = 18.sp
                            )
                        )
                    }

                    // 5. 하단 액션 버튼 바 (취소 / 저장)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NeuButton(
                            onClick = onDismiss,
                            isPrimary = false,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "취소",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        NeuButton(
                            onClick = {
                                onSave(
                                    PromptInstructionConfig(
                                        topInstruction = topText,
                                        bottomInstruction = bottomText.ifBlank { null }
                                    )
                                )
                            },
                            isPrimary = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "저장",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PebbleSmallActionChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    NeuCard(
        shape = RoundedCornerShape(8.dp),
        elevation = 2.dp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = text }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp),
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
