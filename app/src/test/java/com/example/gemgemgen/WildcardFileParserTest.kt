// 역할: 와일드카드 텍스트 파일 파싱 및 토큰 생성 로직을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.wildcard.domain.WildcardFileParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WildcardFileParserTest {
    @Test
    fun tokenFromFileName_mapsTxtFileNameToToken() {
        assertEquals("__hair__", WildcardFileParser.tokenFromFileName("hair.txt"))
    }

    @Test
    fun tokenFromFileName_removesWhitespaceFromToken() {
        assertEquals("__여성의상__", WildcardFileParser.tokenFromFileName("여성 의상.txt"))
        assertEquals("__haircolor__", WildcardFileParser.tokenFromFileName("  hair color .txt"))
        assertNull(WildcardFileParser.tokenFromFileName("   .txt"))
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

    @Test
    fun hasAnyItem_returnsTrueWhenNonBlankLineExists() {
        org.junit.Assert.assertTrue(WildcardFileParser.hasAnyItem("   \n  item  \n   "))
        org.junit.Assert.assertFalse(WildcardFileParser.hasAnyItem("   \n\n  \t  "))
        org.junit.Assert.assertFalse(WildcardFileParser.hasAnyItem(""))
    }
}
