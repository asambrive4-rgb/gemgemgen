// 역할: 상단·하단 프롬프트 인스트럭션 편집 모달 다이얼로그를 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.automation.domain.InstructionTab
import com.example.gemgemgen.automation.domain.PromptInstructionConfig
import com.example.gemgemgen.core.android.rememberClipboardGateway
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuActionChip
import com.example.gemgemgen.ui.theme.NeuConfigDialogFrame
import com.example.gemgemgen.ui.theme.NeuSegmentedTabBar
import com.example.gemgemgen.ui.theme.appTextFieldColors

@Composable
fun PromptInstructionDialog(
    config: PromptInstructionConfig,
    initialTab: InstructionTab = InstructionTab.TOP,
    onSave: (PromptInstructionConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardGateway = rememberClipboardGateway()
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }
    var topText by remember(config.topInstruction) { mutableStateOf(config.topInstruction) }
    var bottomText by remember(config.bottomInstruction) { mutableStateOf(config.bottomInstruction.orEmpty()) }

    NeuConfigDialogFrame(
        title = "인스트럭션 문구 관리",
        subtitle = "상단/하단 삽입 버튼에 적용될 문구를 설정합니다. (영구 보관)",
        onSave = {
            onSave(
                PromptInstructionConfig(
                    topInstruction = topText,
                    bottomInstruction = bottomText.ifBlank { null }
                )
            )
        },
        onDismiss = onDismiss
    ) {
        // 탭 선택 (상단 삽입 / 하단 삽입)
        NeuSegmentedTabBar(
            items = listOf(InstructionTab.TOP, InstructionTab.BOTTOM),
            selectedItem = activeTab,
            onItemSelected = { activeTab = it },
            itemLabel = { tab ->
                when (tab) {
                    InstructionTab.TOP -> "상단 삽입 문구"
                    InstructionTab.BOTTOM -> "하단 삽입 문구"
                }
            },
            height = 36.dp,
            shape = RoundedCornerShape(12.dp),
            pillShape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(4.dp),
            tabSpacing = 6.dp
        )

        // 간편 기능 툴바 (클립보드 가져오기, 기본값 복원, 복사)
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
                NeuActionChip(
                    text = "가져오기",
                    icon = Icons.Default.ContentPaste,
                    onClick = {
                        val clip = clipboardGateway.readText()
                        if (clip.isNotBlank()) {
                            if (activeTab == InstructionTab.TOP) {
                                topText = clip
                            } else {
                                bottomText = clip
                            }
                        }
                    }
                )

                NeuActionChip(
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

                NeuActionChip(
                    text = "복사",
                    icon = Icons.Default.ContentCopy,
                    onClick = {
                        if (currentText.isNotBlank()) {
                            clipboardGateway.writeText(currentText)
                        }
                    }
                )
            }
        }

        // 에디터 본문 (AnimatedContent)
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
    }
}
