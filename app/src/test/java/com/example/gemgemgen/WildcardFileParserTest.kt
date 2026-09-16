// 역할: 와일드카드 텍스트 파일 파싱, 토큰 생성 및 검증 로직을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.wildcard.domain.WildcardFileParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun isValidToken_validatesTokenFormat() {
        assertTrue(WildcardFileParser.isValidToken("__hair__"))
        assertTrue(WildcardFileParser.isValidToken("__여성의상__"))
        assertFalse(WildcardFileParser.isValidToken("____"))
        assertFalse(WildcardFileParser.isValidToken("__hair color__"))
        assertFalse(WildcardFileParser.isValidToken("hair"))
        assertFalse(WildcardFileParser.isValidToken("__hair"))
        assertFalse(WildcardFileParser.isValidToken("hair__"))
        assertFalse(WildcardFileParser.isValidToken(""))
        assertFalse(WildcardFileParser.isValidToken("__  __"))
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
