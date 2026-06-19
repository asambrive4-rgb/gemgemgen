package com.example.gemgemgen

import com.example.gemgemgen.automation.android.*
import com.example.gemgemgen.automation.domain.*
import com.example.gemgemgen.automation.usecase.*
import com.example.gemgemgen.core.*
import com.example.gemgemgen.environment.android.*
import com.example.gemgemgen.environment.domain.*
import com.example.gemgemgen.environment.usecase.*
import com.example.gemgemgen.ui.*
import com.example.gemgemgen.wildcard.domain.*
import com.example.gemgemgen.wildcard.usecase.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WildcardFileParserTest {
    @Test
    fun tokenFromFileName_mapsTxtFileNameToToken() {
        assertEquals("__hair__", WildcardFileParser.tokenFromFileName("hair.txt"))
    }

    @Test
    fun tokenFromFileName_ignoresNonTxtFile() {
        assertNull(WildcardFileParser.tokenFromFileName("hair.csv"))
    }

    @Test
    fun parseItems_trimsLinesAndSkipsBlankLines() {
        val text = """
            short black hair

              long blonde hair
            
            silver twin tails
        """.trimIndent()

        assertEquals(
            listOf("short black hair", "long blonde hair", "silver twin tails"),
            WildcardFileParser.parseItems(text)
        )
    }
}
