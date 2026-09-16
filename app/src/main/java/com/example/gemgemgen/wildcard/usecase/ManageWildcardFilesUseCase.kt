// 역할: 와일드카드 텍스트 파일의 생성, 수정, 이름 변경, 삭제 작업을 수행합니다.
package com.example.gemgemgen.wildcard.usecase

import com.example.gemgemgen.wildcard.domain.WildcardFileException
import com.example.gemgemgen.wildcard.domain.WildcardFileName
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.withContext

data class WildcardWorkspace(
    val files: List<WildcardTextFile>,
    val selectedFile: WildcardTextFile? = null,
    val selectedText: String? = null,
    val previousSelectionMissing: Boolean = false
)

class ManageWildcardFilesUseCase(
    private val repository: WildcardFileRepository,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun refreshWorkspace(
        selectedFile: WildcardTextFile?,
        openFirstFile: Boolean
    ): WildcardWorkspace = withContext(dispatchers.io) {
        val files = repository.listFiles()
        val currentFile = selectedFile?.let { selected -> files.firstOrNull { it.id == selected.id } }
        val fileToOpen = currentFile ?: if (openFirstFile) files.firstOrNull() else null

        WildcardWorkspace(
            files = files,
            selectedFile = fileToOpen,
            selectedText = if (fileToOpen != null && currentFile == null) repository.readFile(fileToOpen) else null,
            previousSelectionMissing = selectedFile != null && currentFile == null
        )
    }

    suspend fun openFile(file: WildcardTextFile): String = withContext(dispatchers.io) { repository.readFile(file) }

    suspend fun saveFile(file: WildcardTextFile, text: String) = withContext(dispatchers.io) { repository.writeFile(file, text) }

    suspend fun createFile(fileName: String): WildcardWorkspace = withContext(dispatchers.io) {
        val normalized = WildcardFileName.normalize(fileName) ?: throw WildcardFileException("파일명을 입력해주세요.")
        ensureFileNameAvailable(normalized)
        WildcardWorkspace(files = repository.listFiles(), selectedFile = repository.createFile(normalized), selectedText = "")
    }

    suspend fun renameFile(file: WildcardTextFile, newName: String): WildcardWorkspace = withContext(dispatchers.io) {
        val normalized = WildcardFileName.normalize(newName) ?: throw WildcardFileException("파일 이름을 입력해주세요.")
        ensureFileNameAvailable(normalized, excludingFileId = file.id)
        WildcardWorkspace(files = repository.listFiles(), selectedFile = repository.renameFile(file, normalized))
    }

    suspend fun deleteFile(
        file: WildcardTextFile,
        previousFiles: List<WildcardTextFile>
    ): WildcardWorkspace = withContext(dispatchers.io) {
        val oldIndex = previousFiles.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
        repository.deleteFile(file)
        val files = repository.listFiles()
        val nextFile = files.getOrNull(oldIndex) ?: files.lastOrNull()
        WildcardWorkspace(files = files, selectedFile = nextFile, selectedText = nextFile?.let(repository::readFile))
    }

    private fun ensureFileNameAvailable(fileName: String, excludingFileId: String? = null) {
        val exists = repository.listFiles().any { it.id != excludingFileId && it.fileName.equals(fileName, ignoreCase = true) }
        if (exists) throw WildcardFileException("이미 같은 이름의 파일이 있습니다.")
    }
}
