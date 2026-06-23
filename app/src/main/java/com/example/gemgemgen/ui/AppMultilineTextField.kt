package com.example.gemgemgen.ui

import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppMultilineTextField(
    state: TextFieldState,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    minLines: Int,
    maxLines: Int = 18
) {
    LaunchedEffect(state, onValueChange) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .collect { onValueChange(it) }
    }

    OutlinedTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        placeholder = {
            if (placeholder.isNotBlank()) {
                Text(placeholder)
            }
        },
        lineLimits = TextFieldLineLimits.MultiLine(
            minHeightInLines = minLines,
            maxHeightInLines = maxLines
        ),
        textStyle = TextStyle(fontFamily = FontFamily.Monospace)
    )
}

