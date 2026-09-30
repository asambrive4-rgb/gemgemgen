// 역할: 와일드카드 파일명 목록으로부터 자동완성 토큰 후보 목록을 변환하는 도메인 규칙을 검증합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetWildcardTokenCandidatesUseCaseTest {

    @Test
    fun candidatesFromFileNames_whenFilesExist_returnsCandidates() {
        val files = listOf(
            WildcardTextFile(id = "colors.txt", fileName = "colors.txt"),
            WildcardTextFile(id = "animals.txt", fileName = "animals.txt")
        )

        val candidates = WildcardTokenAutocomplete.candidatesFromFileNames(files.map { it.fileName })

        assertEquals(2, candidates.size)
        assertTrue(candidates.any { it.token == "__colors__" })
        assertTrue(candidates.any { it.token == "__animals__" })
    }

    @Test
    fun candidatesFromFileNames_whenEmpty_returnsEmptyList() {
        val candidates = WildcardTokenAutocomplete.candidatesFromFileNames(emptyList())

        assertTrue(candidates.isEmpty())
    }
}
