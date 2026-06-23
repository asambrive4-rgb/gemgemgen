package com.example.gemgemgen.wildcard.android

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.gemgemgen.wildcard.domain.WildcardFileException
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository

object AndroidWildcardFolderAccessChecker {
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

    fun canWriteFolder(context: Context, folderUri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == folderUri && it.isReadPermission && it.isWritePermission
        }
    }
}

class AndroidWildcardFileRepository(
    private val context: Context
) : WildcardFileRepository {
    private val documentReader = AndroidWildcardDocumentReader(context)

    override fun listFiles(): List<WildcardTextFile> {
        return documentReader.listDocuments().map { it.toTextFile() }
    }

    override fun readFile(file: WildcardTextFile): String {
        return documentReader.readText(file.toDocument())
    }

    override fun createFile(fileName: String): WildcardTextFile {
        val folderUri = currentFolderUri()
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
        )
        val documentUri = DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            "text/plain",
            fileName
        ) ?: throw WildcardFileException("새 파일을 만들지 못했습니다.")

        return WildcardTextFile(
            id = DocumentsContract.getDocumentId(documentUri),
            fileName = fileName
        )
    }

    override fun writeFile(file: WildcardTextFile, text: String) {
        val uri = documentUriFor(file)
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw WildcardFileException("${file.fileName} 파일을 저장하지 못했습니다.")

        output.use {
            it.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(text)
            }
        }
    }

    override fun deleteFile(file: WildcardTextFile) {
        val uri = documentUriFor(file)
        val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
        if (!deleted) throw WildcardFileException("${file.fileName} 파일을 삭제하지 못했습니다.")
    }

    override fun renameFile(file: WildcardTextFile, newName: String): WildcardTextFile {
        val uri = documentUriFor(file)
        val newUri = DocumentsContract.renameDocument(
            context.contentResolver,
            uri,
            newName
        ) ?: throw WildcardFileException("${file.fileName} 파일 이름을 수정하지 못했습니다.")

        return WildcardTextFile(
            id = DocumentsContract.getDocumentId(newUri),
            fileName = newName
        )
    }

    private fun currentFolderUri(): Uri {
        return WildcardFolderStore.getFolderUri(context)
            ?: throw WildcardFileException("wildcard 폴더를 먼저 선택해주세요.")
    }

    private fun WildcardDocument.toTextFile(): WildcardTextFile {
        return WildcardTextFile(
            id = id,
            fileName = fileName
        )
    }

    private fun WildcardTextFile.toDocument(): WildcardDocument {
        return WildcardDocument(
            id = id,
            fileName = fileName,
            documentUri = documentUriFor(this)
        )
    }

    private fun documentUriFor(file: WildcardTextFile): Uri {
        val folderUri = currentFolderUri()
        return DocumentsContract.buildDocumentUriUsingTree(folderUri, file.id)
    }
}

