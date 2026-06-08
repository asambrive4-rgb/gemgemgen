package com.example.gemgemgen

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

@Composable
internal fun AppMultilineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    minLines: Int,
    maxLines: Int = 18
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        placeholder = {
            if (placeholder.isNotBlank()) {
                Text(placeholder)
            }
        },
        minLines = minLines,
        maxLines = maxLines,
        textStyle = TextStyle(fontFamily = FontFamily.Monospace)
    )
}
