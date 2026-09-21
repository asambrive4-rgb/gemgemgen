// 역할: 프롬프트 상용구(텍스트 대치)를 추가, 편집, 삭제하고 클립보드와 연동하는 관리 다이얼로그를 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gemgemgen.automation.domain.PromptSnippet
import com.example.gemgemgen.ui.clearFocusOnOutsideTap
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuInsetBed
import com.example.gemgemgen.ui.theme.appTextFieldColors

@Composable
fun PromptSnippetDialog(
    showDialog: Boolean,
    snippets: List<PromptSnippet>,
    currentPromptText: String = "",
    onAddSnippet: (shortcut: String, content: String) -> Unit,
    onDeleteSnippet: (id: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!showDialog) return

    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val clearInputFocus = remember(focusManager) {
        { focusManager.clearFocus(force = true) }
    }

    var shortcutInput by remember(showDialog) { mutableStateOf("") }
    var contentInput by remember(showDialog) { mutableStateOf("") }
    var errorMessage by remember(showDialog) { mutableStateOf("") }

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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. 헤더 영역
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "프롬프트 상용구 (텍스트 대치)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "자주 쓰는 긴 문장 뭉치에 짧은 단축어를 붙여 저장합니다.\n입력창에서 단축어를 치면 추천 칩([📋 단축어])으로 즉시 대치됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary,
                            lineHeight = 16.sp
                        )
                    }

                    // 2. 등록된 상용구 목록 (스크롤 영역)
                    Text(
                        text = "보관 중인 상용구 (${snippets.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )

                    NeuInsetBed(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (snippets.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "등록된 상용구가 없습니다.\n아래에서 자주 쓰는 문구를 단축어로 등록해 보세요.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary,
                                    lineHeight = 18.sp
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                snippets.forEach { snippet ->
                                    SnippetListItem(
                                        snippet = snippet,
                                        onCopy = {
                                            clipboardManager.setText(AnnotatedString(snippet.content))
                                        },
                                        onDelete = { onDeleteSnippet(snippet.id) }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = AppTheme.colors.cardBorder.copy(alpha = 0.5f)
                    )

                    // 3. 새 상용구 추가 폼
                    Text(
                        text = "새 상용구 등록",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = shortcutInput,
                            onValueChange = {
                                shortcutInput = it.filterNot { char -> char.isWhitespace() }
                                errorMessage = ""
                            },
                            label = { Text("단축어 (예: 고화질)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = appTextFieldColors()
                        )

                        // 현재 프롬프트 본문 가져오기 버튼
                        if (currentPromptText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    contentInput = currentPromptText
                                    errorMessage = ""
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Input,
                                    contentDescription = "현재 프롬프트 본문 가져오기",
                                    tint = AppTheme.colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 클립보드 붙여넣기 버튼
                        IconButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text.orEmpty()
                                if (clip.isNotBlank()) {
                                    contentInput = clip
                                    errorMessage = ""
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "클립보드 붙여넣기",
                                tint = AppTheme.colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = contentInput,
                        onValueChange = {
                            contentInput = it
                            errorMessage = ""
                        },
                        label = { Text("치환될 실제 프롬프트 문구") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp, max = 130.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = appTextFieldColors(),
                        maxLines = 5
                    )

                    if (errorMessage.isNotBlank()) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // 4. 하단 버튼 영역
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NeuButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            isPrimary = false
                        ) {
                            Text("닫기")
                        }

                        NeuButton(
                            onClick = {
                                val shortcut = shortcutInput.trim()
                                val content = contentInput.trim()
                                when {
                                    shortcut.isEmpty() -> errorMessage = "단축어를 입력해주세요."
                                    content.isEmpty() -> errorMessage = "프롬프트 문구를 입력해주세요."
                                    snippets.any { it.shortcut.equals(shortcut, ignoreCase = true) } -> {
                                        errorMessage = "이미 등록된 단축어입니다."
                                    }
                                    else -> {
                                        onAddSnippet(shortcut, content)
                                        shortcutInput = ""
                                        contentInput = ""
                                        errorMessage = ""
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            isPrimary = true
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("추가하기")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnippetListItem(
    snippet: PromptSnippet,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AppTheme.colors.card,
        border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 단축어 뱃지 (accent 컬러)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppTheme.colors.accent.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AppTheme.colors.accent.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "📋 ${snippet.shortcut}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.accent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = snippet.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "복사",
                        tint = AppTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "삭제",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
