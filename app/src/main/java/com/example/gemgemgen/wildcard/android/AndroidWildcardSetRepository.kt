package com.example.gemgemgen.wildcard.android

import android.content.Context
import android.net.Uri
import com.example.gemgemgen.wildcard.domain.WildcardFileParser
import com.example.gemgemgen.wildcard.domain.WildcardSet
import com.example.gemgemgen.wildcard.usecase.WildcardSetRepository

class AndroidWildcardSetRepository(private val context: Context) : WildcardSetRepository {
    private val documentReader = AndroidWildcardDocumentReader(context)

    override fun load(): List<WildcardSet> {
        val folderUri = WildcardFolderStore.getFolderUri(context) ?: return emptyList()
        return load(folderUri)
    }

    override fun load(tokens: Set<String>): List<WildcardSet> {
        if (tokens.isEmpty()) return emptyList()
        val folderUri = WildcardFolderStore.getFolderUri(context) ?: return emptyList()
        return load(folderUri, tokens)
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

