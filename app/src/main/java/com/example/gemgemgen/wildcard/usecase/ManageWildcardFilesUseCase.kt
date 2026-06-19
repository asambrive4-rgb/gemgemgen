package com.example.gemgemgen.wildcard.usecase

import com.example.gemgemgen.wildcard.domain.WildcardFileException
import com.example.gemgemgen.wildcard.domain.WildcardFileName
import com.example.gemgemgen.wildcard.domain.WildcardTextFile

class ManageWildcardFilesUseCase(
    private val repository: WildcardFileRepository
) {
    fun listFiles(): List<WildcardTextFile> {
        return repository.listFiles()
    }

    fun readFile(file: WildcardTextFile): String {
        return repository.readFile(file)
    }

    fun writeFile(file: WildcardTextFile, text: String) {
        repository.writeFile(file, text)
    }

    fun createFile(fileName: String): WildcardTextFile {
        val normalizedFileName = WildcardFileName.normalize(fileName)
            ?: throw WildcardFileException("파일명을 입력해주세요.")

        ensureFileNameAvailable(normalizedFileName)
        return repository.createFile(normalizedFileName)
    }

    fun renameFile(
        file: WildcardTextFile,
        newName: String
    ): WildcardTextFile {
        val normalizedFileName = WildcardFileName.normalize(newName)
            ?: throw WildcardFileException("파일 이름을 입력해주세요.")

        ensureFileNameAvailable(normalizedFileName, excludingFileId = file.id)
        return repository.renameFile(file, normalizedFileName)
    }

    fun deleteFile(file: WildcardTextFile) {
        repository.deleteFile(file)
    }

    private fun ensureFileNameAvailable(
        fileName: String,
        excludingFileId: String? = null
    ) {
        val exists = repository.listFiles().any { file ->
            file.id != excludingFileId &&
                file.fileName.equals(fileName, ignoreCase = true)
        }
        if (exists) throw WildcardFileException("이미 같은 이름의 파일이 있습니다.")
    }
}
