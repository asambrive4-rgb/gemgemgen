package com.example.gemgemgen.wildcard.android

import android.content.Context
import android.net.Uri
import com.example.gemgemgen.wildcard.domain.WildcardFileParser
import com.example.gemgemgen.wildcard.domain.WildcardSet
import com.example.gemgemgen.wildcard.usecase.WildcardSetRepository

class AndroidWildcardSetRepository(private val context: Context) : WildcardSetRepository {
    private val documentReader = AndroidWildcardDocumentReader(context)
    private val directStorage = AndroidWildcardDirectStorage()

    override fun load(): List<WildcardSet> {
        if (AndroidWildcardDirectStorage.hasAllFilesAccess()) {
            return loadDirectStorage()
        }
        val folderUri = WildcardFolderStore.getFolderUri(context) ?: return emptyList()
        return load(folderUri)
    }

    override fun load(tokens: Set<String>): List<WildcardSet> {
        if (tokens.isEmpty()) return emptyList()
        if (AndroidWildcardDirectStorage.hasAllFilesAccess()) {
            return loadDirectStorage(tokens)
        }
        val folderUri = WildcardFolderStore.getFolderUri(context) ?: return emptyList()
        return load(folderUri, tokens)
    }

    private fun loadDirectStorage(tokens: Set<String>? = null): List<WildcardSet> {
        return directStorage.listFiles()
            .mapNotNull { file ->
                val token = WildcardFileParser.tokenFromFileName(file.fileName)
                    ?: return@mapNotNull null
                if (tokens != null && token !in tokens) return@mapNotNull null

                val text = runCatching { directStorage.readFile(file) }.getOrDefault("")
                WildcardSet(
                    token = token,
                    fileName = file.fileName,
                    items = WildcardFileParser.parseItems(text)
                )
            }
    }

    fun load(folderUri: Uri): List<WildcardSet> {
        return load(folderUri, tokens = null)
    }

    private fun load(folderUri: Uri, tokens: Set<String>?): List<WildcardSet> {
        return documentReader.listDocumentsOrEmpty(folderUri)
            .mapNotNull { document ->
                val token = WildcardFileParser.tokenFromFileName(document.fileName)
                    ?: return@mapNotNull null
                if (tokens != null && token !in tokens) return@mapNotNull null

                val text = documentReader.readTextOrEmpty(document)

                WildcardSet(
                    token = token,
                    fileName = document.fileName,
                    items = WildcardFileParser.parseItems(text)
                )
            }
    }
}

