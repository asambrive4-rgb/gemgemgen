package com.example.gemgemgen

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.ui.AppMultilineTextField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppMultilineTextFieldTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun paragraphSelectionMode_tapReportsTouchedTextOffset() {
        val text = "첫째 문단\n둘째 문단\n"
        val state = TextFieldState(
            initialText = text,
            initialSelection = TextRange(text.length)
        )
        var selectedOffset = -1

        composeRule.setContent {
            MaterialTheme {
                AppMultilineTextField(
                    state = state,
                    onValueChange = {},
                    modifier = Modifier
                        .width(320.dp)
                        .testTag("prompt"),
                    minLines = 4,
                    paragraphSelectionEnabled = true,
                    onParagraphOffsetSelected = { selectedOffset = it }
                )
            }
        }

        composeRule.onNodeWithTag("prompt").performTouchInput {
            click(Offset(x = 60f, y = 32f))
        }
        composeRule.waitUntil { selectedOffset >= 0 }

        assertTrue(selectedOffset in 0 until text.indexOf('\n'))
    }

    @Test
    fun paragraphSelectionMode_textInputIsRejected() {
        val text = "첫째 문단\n둘째 문단"
        val state = TextFieldState(
            initialText = text,
            initialSelection = TextRange.Zero
        )

        composeRule.setContent {
            MaterialTheme {
                AppMultilineTextField(
                    state = state,
                    onValueChange = {},
                    modifier = Modifier
                        .width(320.dp)
                        .testTag("prompt"),
                    minLines = 4,
                    paragraphSelectionEnabled = true
                )
            }
        }

        composeRule.onNodeWithTag("prompt")
            .performClick()
            .performTextInput("추가")

        assertEquals(text, state.text.toString())
    }
}
