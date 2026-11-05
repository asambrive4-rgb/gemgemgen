// 역할: 와일드카드 화면의 본문 텍스트 에디터, 줄 선택 체크리스트 및 하단 액션 버튼 바 컴포넌트를 제공합니다.
package com.example.gemgemgen.wildcard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.ui.rememberSyncedTextFieldState
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.appTextFieldColors

@Composable
internal fun LineSelectionList(
    lines: List<String>,
    selectedIndices: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lines.isEmpty()) {
        Box(
            modifier = modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "선택할 줄이 없습니다.",
                color = AppTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(
            items = lines,
            key = { index, line -> "$index|$line" }
        ) { index, line ->
            val checked = index in selectedIndices
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggle(index) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onToggle(index) }
                )
                Text(
                    text = line,
                    color = AppTheme.colors.textPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun LineSelectionActionBar(
    uiState: WildcardUiState,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onCompose: () -> Unit,
    onDeriveFile: () -> Unit,
    onExit: () -> Unit
) {
    val barShape = RoundedCornerShape(14.dp)
    val btnPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeuButton(
            onClick = onSelectAll,
            enabled = uiState.canSelectAllLines,
            isPrimary = false,
            shape = barShape,
            contentPadding = btnPadding,
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
        ) {
            Text(text = "전체", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        NeuButton(
            onClick = onDeselectAll,
            enabled = uiState.canDeselectAllLines,
            isPrimary = false,
            shape = barShape,
            contentPadding = btnPadding,
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
        ) {
            Text(text = "해제", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        NeuButton(
            onClick = onCompose,
            enabled = uiState.canComposeDynamicPrompt,
            isPrimary = false,
            shape = barShape,
            contentPadding = btnPadding,
            modifier = Modifier
                .weight(1.6f)
                .height(42.dp)
        ) {
            Text(
                text = "다이나믹",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        NeuButton(
            onClick = onDeriveFile,
            enabled = uiState.canDeriveFile,
            isPrimary = true,
            shape = barShape,
            contentPadding = btnPadding,
            modifier = Modifier
                .weight(1.8f)
                .height(42.dp)
        ) {
            Text(
                text = "새 파일로",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        NeuButton(
            onClick = onExit,
            enabled = uiState.canExitLineSelectionMode,
            isPrimary = false,
            shape = barShape,
            contentPadding = btnPadding,
            modifier = Modifier
                .weight(1.1f)
                .height(42.dp)
        ) {
            Text(text = "완료", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
internal fun ActionButtonsBar(
    uiState: WildcardUiState,
    onSave: () -> Unit,
    onPaste: () -> Unit,
    onPasteBelow: () -> Unit,
    onCopy: () -> Unit,
    onUndo: () -> Unit
) {
    val barShape = RoundedCornerShape(14.dp)
    val btnPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
    val iconOnlyPadding = PaddingValues(0.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeuButton(
            onClick = onPasteBelow,
            enabled = uiState.canPaste,
            isPrimary = false,
            shape = barShape,
            contentPadding = btnPadding,
            modifier = Modifier
                .weight(1.15f)
                .height(42.dp)
        ) {
            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "아래 붙여넣기",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        NeuButton(
            onClick = onPaste,
            enabled = uiState.canPaste,
            isPrimary = false,
            shape = barShape,
            contentPadding = btnPadding,
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
        ) {
            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "붙여넣기",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        NeuButton(
            onClick = onSave,
            enabled = uiState.canSave,
            isPrimary = true,
            shape = barShape,
            contentPadding = btnPadding,
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
        ) {
            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "저장",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        NeuButton(
            onClick = onCopy,
            enabled = uiState.canCopy,
            isPrimary = false,
            shape = barShape,
            contentPadding = iconOnlyPadding,
            modifier = Modifier
                .width(42.dp)
                .height(42.dp)
        ) {
            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "복사", modifier = Modifier.size(18.dp))
        }

        NeuButton(
            onClick = onUndo,
            enabled = uiState.canUndo,
            isPrimary = false,
            shape = barShape,
            contentPadding = iconOnlyPadding,
            modifier = Modifier
                .width(42.dp)
                .height(42.dp)
        ) {
            Icon(imageVector = Icons.Default.Undo, contentDescription = "실행 취소", modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * 와일드카드 텍스트 에디터 영역을 TextFieldState 기반 국소 컴포저블로 분리하여
 * 한글 조합(IME) 중 커서 튐과 상위 전체 화면의 불필요한 리컴포지션을 방지합니다.
 */
@Composable
internal fun WildcardEditorTextField(
    fileKey: String?,
    editingText: String,
    canEditText: Boolean,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val editorState = rememberSyncedTextFieldState(
        value = editingText,
        onValueChange = onTextChange,
        resetKey = fileKey
    )

    OutlinedTextField(
        state = editorState,
        modifier = modifier,
        enabled = canEditText,
        placeholder = { Text(text = "파일을 선택하거나 새 txt 파일을 만들어주세요.") },
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 8),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            color = AppTheme.colors.textPrimary,
            fontSize = 15.sp
        ),
        colors = appTextFieldColors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        )
    )
}
