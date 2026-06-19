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

    fun load(folderUri: Uri): List<WildcardSet> {
        return documentReader.listDocumentsOrEmpty(folderUri)
            .mapNotNull { document ->
                val token = WildcardFileParser.tokenFromFileName(document.fileName)
                    ?: return@mapNotNull null
                val text = documentReader.readTextOrEmpty(document)

                WildcardSet(
                    token = token,
                    fileName = document.fileName,
                    items = WildcardFileParser.parseItems(text)
                )
            }
    }
}

