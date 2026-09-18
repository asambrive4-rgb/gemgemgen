// 역할: 와일드카드 파일 저장소로부터 토큰 후보 목록을 조회 및 변환하는 유스케이스를 검증합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetWildcardTokenCandidatesUseCaseTest {

    private class FakeWildcardFileRepository(
        private val files: List<WildcardTextFile> = emptyList(),
        private val shouldThrow: Boolean = false
    ) : WildcardFileRepository {
        override fun listFiles(): List<WildcardTextFile> {
            if (shouldThrow) throw RuntimeException("Disk error")
            return files
        }
        override fun readFile(file: WildcardTextFile): String = ""
        override fun createFile(fileName: String): WildcardTextFile = WildcardTextFile(id = fileName, fileName = fileName)
        override fun renameFile(file: WildcardTextFile, newName: String): WildcardTextFile = file.copy(fileName = newName)
        override fun writeFile(file: WildcardTextFile, text: String) = Unit
        override fun deleteFile(file: WildcardTextFile) = Unit
    }

    @Test
    fun invoke_whenRepositoryHasFiles_returnsCandidates() {
        val files = listOf(
            WildcardTextFile(id = "colors.txt", fileName = "colors.txt"),
            WildcardTextFile(id = "animals.txt", fileName = "animals.txt")
        )
        val repository = FakeWildcardFileRepository(files = files)
        val useCase = GetWildcardTokenCandidatesUseCase(repository)

        val candidates = useCase()

        assertEquals(2, candidates.size)
        assertTrue(candidates.any { it.token == "__colors__" })
        assertTrue(candidates.any { it.token == "__animals__" })
    }

    @Test
    fun invoke_whenRepositoryIsNull_returnsEmptyList() {
        val useCase = GetWildcardTokenCandidatesUseCase(wildcardFileRepository = null)

        val candidates = useCase()

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun invoke_whenRepositoryThrowsException_returnsEmptyList() {
        val repository = FakeWildcardFileRepository(shouldThrow = true)
        val useCase = GetWildcardTokenCandidatesUseCase(repository)

        val candidates = useCase()

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun invoke_whenRepositoryReturnsEmpty_returnsEmptyList() {
        val repository = FakeWildcardFileRepository(files = emptyList())
        val useCase = GetWildcardTokenCandidatesUseCase(repository)

        val candidates = useCase()

        assertTrue(candidates.isEmpty())
    }
}
