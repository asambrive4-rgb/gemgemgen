package com.example.gemgemgen.wildcard.android

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.gemgemgen.wildcard.domain.WildcardFileException
import com.example.gemgemgen.wildcard.domain.WildcardFileParser

internal data class WildcardDocument(
    val id: String,
    val fileName: String,
    val documentUri: Uri
)

internal class AndroidWildcardDocumentReader(
    private val context: Context
) {
    fun listDocuments(): List<WildcardDocument> {
        val folderUri = WildcardFolderStore.getFolderUri(context)
            ?: throw WildcardFileException("wildcard 폴더를 먼저 선택해주세요.")
        return listDocuments(folderUri)
    }

    fun listDocuments(folderUri: Uri): List<WildcardDocument> {
        return listDocuments(folderUri, throwOnReadFailure = true)
    }

    fun listDocumentsOrEmpty(folderUri: Uri): List<WildcardDocument> {
        return listDocuments(folderUri, throwOnReadFailure = false)
    }

    private fun listDocuments(folderUri: Uri, throwOnReadFailure: Boolean): List<WildcardDocument> {
        val resolver = context.contentResolver
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
        )
        val result = mutableListOf<WildcardDocument>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val cursor = resolver.query(childUri, projection, null, null, null)
        if (cursor == null) {
            if (throwOnReadFailure) {
                throw WildcardFileException("wildcard 폴더를 읽지 못했습니다. 폴더를 다시 선택해주세요.")
            }
            return emptyList()
        }

        cursor.use {
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val fileName = cursor.getString(nameIndex) ?: continue
                if (WildcardFileParser.tokenFromFileName(fileName) == null) continue
                if (cursor.getString(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR) continue

                val documentId = cursor.getString(idIndex)
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId)
                result += WildcardDocument(
                    id = documentId,
                    fileName = fileName,
                    documentUri = documentUri
                )
            }
        }

        return result.sortedBy { it.fileName.lowercase() }
    }

    fun readText(document: WildcardDocument): String {
        return readText(document.documentUri) ?: throw WildcardFileException("${document.fileName} 파일을 열지 못했습니다.")
    }

    fun readTextOrEmpty(document: WildcardDocument): String {
        return readText(document.documentUri).orEmpty()
    }

    private fun readText(documentUri: Uri): String? {
        return context.contentResolver.openInputStream(documentUri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }
}

