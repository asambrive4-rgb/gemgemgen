package com.example.gemgemgen

import com.example.gemgemgen.wildcard.domain.WildcardFileException
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.usecase.ManageWildcardFilesUseCase
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ManageWildcardFilesUseCaseTest {
    @Test
    fun createFile_normalizesNameBeforeCallingRepository() {
        val repository = RecordingWildcardFileRepository()
        val useCase = ManageWildcardFilesUseCase(repository)

        val file = useCase.createFile("hair")

        assertEquals("hair.txt", repository.createdFileName)
        assertEquals("hair.txt", file.fileName)
    }

    @Test
    fun createFile_rejectsDuplicateNameBeforeCallingRepository() {
        val repository = RecordingWildcardFileRepository(
            WildcardTextFile("hair", "hair.txt", "content://hair")
        )
        val useCase = ManageWildcardFilesUseCase(repository)

        assertWildcardFileException("이미 같은 이름의 파일이 있습니다.") {
            useCase.createFile("HAIR")
        }

        assertNull(repository.createdFileName)
    }

    @Test
    fun renameFile_normalizesNameBeforeCallingRepository() {
        val original = WildcardTextFile("hair", "hair.txt", "content://hair")
        val repository = RecordingWildcardFileRepository(original)
        val useCase = ManageWildcardFilesUseCase(repository)

        val renamed = useCase.renameFile(original, "new_hair")

        assertEquals("new_hair.txt", repository.renamedFileName)
        assertEquals("new_hair.txt", renamed.fileName)
    }

    @Test
    fun renameFile_rejectsDuplicateNameBeforeCallingRepository() {
        val original = WildcardTextFile("color", "color.txt", "content://color")
        val repository = RecordingWildcardFileRepository(
            WildcardTextFile("hair", "hair.txt", "content://hair"),
            original
        )
        val useCase = ManageWildcardFilesUseCase(repository)

        assertWildcardFileException("이미 같은 이름의 파일이 있습니다.") {
            useCase.renameFile(original, "hair")
        }

        assertNull(repository.renamedFileName)
    }

    private fun assertWildcardFileException(
        expectedMessage: String,
        block: () -> Unit
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
            return files.values.toList()
        }

        override fun readFile(file: WildcardTextFile): String {
            return ""
        }

        override fun createFile(fileName: String): WildcardTextFile {
            createdFileName = fileName
            return WildcardTextFile(fileName, fileName, "content://$fileName")
        }

        override fun renameFile(
            file: WildcardTextFile,
            newName: String
        ): WildcardTextFile {
            renamedFileName = newName
            return file.copy(fileName = newName)
        }

        override fun writeFile(file: WildcardTextFile, text: String) = Unit

        override fun deleteFile(file: WildcardTextFile) = Unit
    }
}
