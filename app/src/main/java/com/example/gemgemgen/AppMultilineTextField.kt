package com.example.gemgemgen

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue

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
    var textFieldValueState by remember {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        )
    }

    if (textFieldValueState.text != value) {
        textFieldValueState = textFieldValueState.copy(
            text = value,
            selection = TextRange(value.length)
        )
    }

    OutlinedTextField(
        value = textFieldValueState,
        onValueChange = { newVal ->
            textFieldValueState = newVal
            onValueChange(newVal.text)
        },
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
