// 역할: 와일드카드 파일 읽기·쓰기 및 폴더 URI 저장소 인터페이스를 정의합니다.
package com.example.gemgemgen.wildcard.usecase

import com.example.gemgemgen.wildcard.domain.WildcardFileParser
import com.example.gemgemgen.wildcard.domain.WildcardSet
import com.example.gemgemgen.wildcard.domain.WildcardTextFile

interface WildcardFileRepository {
    fun listFiles(): List<WildcardTextFile>
    fun readFile(file: WildcardTextFile): String
    fun createFile(fileName: String): WildcardTextFile
    fun renameFile(file: WildcardTextFile, newName: String): WildcardTextFile
    fun writeFile(file: WildcardTextFile, text: String)
    fun deleteFile(file: WildcardTextFile)
}

fun WildcardFileRepository.loadWildcardSets(tokens: Set<String>? = null): List<WildcardSet> {
    return runCatching { listFiles() }
        .getOrDefault(emptyList())
        .mapNotNull { file ->
            val token = WildcardFileParser.tokenFromFileName(file.fileName)
                ?: return@mapNotNull null
            if (tokens != null && token !in tokens) return@mapNotNull null
            val text = runCatching { readFile(file) }.getOrDefault("")
            WildcardSet(
                token = token,
                fileName = file.fileName,
                items = WildcardFileParser.parseItems(text)
            )
        }
}

sealed interface FolderSelectionResult {
    data object Success : FolderSelectionResult
    data class Failure(val reason: String? = null) : FolderSelectionResult
}

interface WildcardFolderRepository {
    fun save(folderUri: String): FolderSelectionResult
    fun getFolderUri(): String?
}
