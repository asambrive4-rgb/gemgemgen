// 역할: 변주 버튼 클릭 시 Gemini 채팅방에 붙여넣을 프롬프트를 영구 편집하는 다이얼로그를 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.RestartAlt
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.automation.domain.VariationPromptConfig
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuInsetBed
import com.example.gemgemgen.ui.theme.appTextFieldColors

@Composable
fun VariationPromptDialog(
    showDialog: Boolean,
    config: VariationPromptConfig,
    onSave: (VariationPromptConfig) -> Unit,
    onDismiss: () -> Unit
) {
    if (!showDialog) return

    val clipboardManager = LocalClipboardManager.current
    var text by remember(config.prompt, showDialog) { mutableStateOf(config.prompt) }

    PromptConfigDialogFrame(
        title = "변주 프롬프트 설정",
        subtitle = "변주 버튼을 누를 때 Gemini 채팅방에 붙여넣을 프롬프트를 설정합니다. (영구 보관)",
        onSave = { onSave(VariationPromptConfig(prompt = text)) },
        onDismiss = onDismiss
    ) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PromptConfigActionChip(
                            text = "기본값",
                            icon = Icons.Default.RestartAlt,
                            onClick = {
                                text = VariationPromptConfig.DEFAULT_VARIATION_PROMPT
                            }
                        )
                        PromptConfigActionChip(
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
    }
}
