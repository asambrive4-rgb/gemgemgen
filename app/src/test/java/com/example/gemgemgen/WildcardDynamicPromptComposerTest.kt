package com.example.gemgemgen

import com.example.gemgemgen.wildcard.domain.WildcardDynamicPromptComposer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WildcardDynamicPromptComposerTest {
    @Test
    fun selectableLines_trimsAndDropsEmptyLines() {
        val lines = WildcardDynamicPromptComposer.selectableLines(
            "  red  \n\n blue \n\ngreen"
        )

        assertEquals(listOf("red", "blue", "green"), lines)
    }

    @Test
    fun compose_joinsSelectedLinesInOrder() {
        val result = WildcardDynamicPromptComposer.compose(
            listOf("red", "blue", "green")
        )

        assertEquals(
            WildcardDynamicPromptComposer.ComposeResult.Success("<red|blue|green>"),
            result
        )
    }

    @Test
    fun compose_allowsSingleLine() {
        val result = WildcardDynamicPromptComposer.compose(listOf("only"))

        assertEquals(
            WildcardDynamicPromptComposer.ComposeResult.Success("<only>"),
            result
        )
    }

    @Test
    fun compose_rejectsEmptySelection() {
        assertEquals(
            WildcardDynamicPromptComposer.ComposeResult.NoSelection,
            WildcardDynamicPromptComposer.compose(emptyList())
        )
    }

    @Test
    fun compose_rejectsPipeOrAngleBrackets() {
        val result = WildcardDynamicPromptComposer.compose(listOf("a|b", "ok"))

        assertTrue(result is WildcardDynamicPromptComposer.ComposeResult.InvalidCharacters)
        val invalid = result as WildcardDynamicPromptComposer.ComposeResult.InvalidCharacters
        assertEquals(listOf("a|b"), invalid.lines)
    }

    @Test
    fun composeFromIndices_keepsFileOrderNotClickOrder() {
        val all = listOf("first", "second", "third")
        val result = WildcardDynamicPromptComposer.composeFromIndices(
            allLines = all,
            selectedIndices = setOf(2, 0)
        )

        assertEquals(
            WildcardDynamicPromptComposer.ComposeResult.Success("<first|third>"),
            result
        )
    }
}
