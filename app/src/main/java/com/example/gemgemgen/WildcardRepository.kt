package com.example.gemgemgen

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

class WildcardRepository(private val context: Context) {
    fun load(): List<WildcardSet> {
        val folderUri = WildcardFolderStore.getFolderUri(context) ?: return emptyList()
        return load(folderUri)
    }

    fun load(folderUri: Uri): List<WildcardSet> {
        val resolver = context.contentResolver
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
        )
        val result = mutableListOf<WildcardSet>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        resolver.query(childUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val fileName = cursor.getString(nameIndex) ?: continue
                val token = WildcardFileParser.tokenFromFileName(fileName) ?: continue
                val mimeType = cursor.getString(mimeIndex)
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) continue

                val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                    folderUri,
                    cursor.getString(idIndex)
                )
                val text = resolver.openInputStream(documentUri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }.orEmpty()

                result += WildcardSet(
                    token = token,
                    fileName = fileName,
                    items = WildcardFileParser.parseItems(text)
                )
            }
        }

        return result.sortedBy { it.fileName.lowercase() }
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
