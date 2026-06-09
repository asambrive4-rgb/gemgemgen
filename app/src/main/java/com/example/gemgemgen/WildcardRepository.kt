package com.example.gemgemgen

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

class WildcardRepository(private val context: Context) {
    private val documentReader = AndroidWildcardDocumentReader(context)

    fun load(): List<WildcardSet> {
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

    companion object {
        fun canReadFolder(context: Context, folderUri: Uri): Boolean {
            val hasPersistedPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == folderUri && it.isReadPermission
            }
            if (!hasPersistedPermission) return false

            return try {
                val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    folderUri,
                    DocumentsContract.getTreeDocumentId(folderUri)
                )
                context.contentResolver.query(
                    childUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                    null,
                    null,
                    null
                )?.use { true } ?: false
            } catch (_: RuntimeException) {
                false
            }
        }
    }
}
