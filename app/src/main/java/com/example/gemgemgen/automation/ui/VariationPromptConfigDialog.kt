// 역할: 변주 버튼 클릭 시 Gemini 채팅방에 붙여넣을 프롬프트를 영구 편집하는 다이얼로그를 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.RestartAlt
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gemgemgen.automation.domain.VariationPromptConfig
import com.example.gemgemgen.ui.clearFocusOnOutsideTap
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuInsetBed
import com.example.gemgemgen.ui.theme.appTextFieldColors

@Composable
fun VariationPromptConfigDialog(
    showDialog: Boolean,
    config: VariationPromptConfig,
    onSave: (VariationPromptConfig) -> Unit,
    onDismiss: () -> Unit
) {
    if (!showDialog) return

    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val clearInputFocus = remember(focusManager) {
        { focusManager.clearFocus(force = true) }
    }

    var text by remember(config.prompt, showDialog) { mutableStateOf(config.prompt) }

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
                    // 1. 헤더
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "변주 프롬프트 설정",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "변주 버튼을 누를 때 Gemini 채팅방에 붙여넣을 프롬프트를 설정합니다. (영구 보관)",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary,
                            lineHeight = 16.sp
                        )
                    }

                    // 2. 에디터 영역
                    NeuInsetBed(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 유틸리티 바
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    VariationActionChip(
                                        text = "기본값",
                                        icon = Icons.Default.RestartAlt,
                                        onClick = {
                                            text = VariationPromptConfig.DEFAULT_VARIATION_PROMPT
                                        }
                                    )
                                    VariationActionChip(
                                        text = "가져오기",
                                        icon = Icons.Default.ContentPaste,
                                        onClick = { text = clipboardManager.getText()?.text.orEmpty() }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                placeholder = {
                                    Text(
                                        text = "Gemini에 입력될 변주 생성 프롬프트를 입력하세요.",
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

                    // 3. 하단 버튼 바 (취소 / 저장)
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
                                text = "취소",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        NeuButton(
                            onClick = {
                                onSave(VariationPromptConfig(prompt = text))
                            },
                            isPrimary = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            contentModifier = Modifier.fillMaxWidth()
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
private fun VariationActionChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    NeuButton(
        onClick = onClick,
        isPrimary = false,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = AppTheme.colors.textSecondary
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = AppTheme.colors.textSecondary
            )
        }
    }
}
