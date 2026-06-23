package com.example.gemgemgen

import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.wildcard.domain.WildcardFileException
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.usecase.ManageWildcardFilesUseCase
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ManageWildcardFilesUseCaseTest {
    @Test
    fun createFile_normalizesNameBeforeCallingRepository() = runBlocking {
        val repository = RecordingWildcardFileRepository()
        val useCase = useCase(repository)

        val workspace = useCase.createFile("hair")

        assertEquals("hair.txt", repository.createdFileName)
        assertEquals("hair.txt", workspace.selectedFile?.fileName)
    }

    @Test
    fun createFile_rejectsDuplicateNameBeforeCallingRepository() = runBlocking {
        val repository = RecordingWildcardFileRepository(
            WildcardTextFile("hair", "hair.txt")
        )
        val useCase = useCase(repository)

        assertWildcardFileException("이미 같은 이름의 파일이 있습니다.") {
            useCase.createFile("HAIR")
        }

        assertNull(repository.createdFileName)
    }

    @Test
    fun renameFile_normalizesNameBeforeCallingRepository() = runBlocking {
        val original = WildcardTextFile("hair", "hair.txt")
        val repository = RecordingWildcardFileRepository(original)
        val useCase = useCase(repository)

        val workspace = useCase.renameFile(original, "new_hair")

        assertEquals("new_hair.txt", repository.renamedFileName)
        assertEquals("new_hair.txt", workspace.selectedFile?.fileName)
    }

    @Test
    fun renameFile_rejectsDuplicateNameBeforeCallingRepository() = runBlocking {
        val original = WildcardTextFile("color", "color.txt")
        val repository = RecordingWildcardFileRepository(
            WildcardTextFile("hair", "hair.txt"),
            original
        )
        val useCase = useCase(repository)

        assertWildcardFileException("이미 같은 이름의 파일이 있습니다.") {
            useCase.renameFile(original, "hair")
        }

        assertNull(repository.renamedFileName)
    }

    @Test
    fun deleteFile_selectsFileAtDeletedIndexOrLastFile() = runBlocking {
        val first = WildcardTextFile("a", "a.txt")
        val middle = WildcardTextFile("b", "b.txt")
        val last = WildcardTextFile("c", "c.txt")
        val repository = RecordingWildcardFileRepository(first, middle, last)

        val workspace = useCase(repository).deleteFile(
            file = middle,
            previousFiles = listOf(first, middle, last)
        )

        assertEquals(last, workspace.selectedFile)
    }

    private fun useCase(
        repository: WildcardFileRepository
    ): ManageWildcardFilesUseCase {
        return ManageWildcardFilesUseCase(
            repository = repository,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        )
    }

    private suspend fun assertWildcardFileException(
        expectedMessage: String,
        block: suspend () -> Unit
    ) {
        try {
            block()
            fail("Expected WildcardFileException")
        } catch (error: WildcardFileException) {
            assertEquals(expectedMessage, error.message)
        }
    }

    private class RecordingWildcardFileRepository(
        vararg initialFiles: WildcardTextFile
    ) : WildcardFileRepository {
        private val files = initialFiles.associateBy { it.id }.toMutableMap()
        var createdFileName: String? = null
        var renamedFileName: String? = null

        override fun listFiles(): List<WildcardTextFile> {
            return files.values.sortedBy { it.fileName }
        }

        override fun readFile(file: WildcardTextFile): String = file.fileName

        override fun createFile(fileName: String): WildcardTextFile {
            createdFileName = fileName
            return WildcardTextFile(fileName, fileName).also { files[it.id] = it }
        }

        override fun renameFile(
            file: WildcardTextFile,
            newName: String
        ): WildcardTextFile {
            renamedFileName = newName
            files.remove(file.id)
            return file.copy(fileName = newName).also { files[it.id] = it }
        }

        override fun writeFile(file: WildcardTextFile, text: String) = Unit

        override fun deleteFile(file: WildcardTextFile) {
            files.remove(file.id)
        }
    }
}
