// 역할: 와일드카드 텍스트 파일 파싱, 토큰 생성 및 파일명/토큰 기반 세트 로드 로직을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.wildcard.usecase.loadWildcardSets

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

    @Test
    fun loadWildcardSets_matchesByFileNameOrTokenOrBaseName() {
        val repo = object : com.example.gemgemgen.wildcard.usecase.WildcardFileRepository {
            override fun listFiles(): List<com.example.gemgemgen.wildcard.domain.WildcardTextFile> {
                return listOf(
                    com.example.gemgemgen.wildcard.domain.WildcardTextFile(id = "1", fileName = "20대중반.txt"),
                    com.example.gemgemgen.wildcard.domain.WildcardTextFile(id = "2", fileName = "헤어.txt")
                )
            }
            override fun readFile(file: com.example.gemgemgen.wildcard.domain.WildcardTextFile): String {
                return if (file.fileName == "20대중반.txt") "카리나\n윈터" else "포니테일"
            }
            override fun createFile(fileName: String) = throw UnsupportedOperationException()
            override fun renameFile(file: com.example.gemgemgen.wildcard.domain.WildcardTextFile, newName: String) = throw UnsupportedOperationException()
            override fun writeFile(file: com.example.gemgemgen.wildcard.domain.WildcardTextFile, text: String) {}
            override fun deleteFile(file: com.example.gemgemgen.wildcard.domain.WildcardTextFile) {}
        }

        // 1. 파일명("20대중반.txt")으로 필터링
        val byFileName = repo.loadWildcardSets( setOf("20대중반.txt"))
        assertEquals(1, byFileName.size)
        assertEquals("20대중반.txt", byFileName[0].fileName)
        assertEquals(listOf("카리나", "윈터"), byFileName[0].items)

        // 2. 토큰("__20대중반__")으로 필터링
        val byToken = repo.loadWildcardSets( setOf("__20대중반__"))
        assertEquals(1, byToken.size)
        assertEquals("20대중반.txt", byToken[0].fileName)

        // 3. 확장자 없는 이름("20대중반")으로 필터링
        val byBaseName = repo.loadWildcardSets( setOf("20대중반"))
        assertEquals(1, byBaseName.size)
        assertEquals("20대중반.txt", byBaseName[0].fileName)
    }
}
