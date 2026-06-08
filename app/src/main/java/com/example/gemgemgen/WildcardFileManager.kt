package com.example.gemgemgen

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

interface WildcardFileManager {
    fun listFiles(): List<WildcardTextFile>
    fun readFile(file: WildcardTextFile): String
    fun createFile(fileName: String): WildcardTextFile
    fun renameFile(file: WildcardTextFile, newName: String): WildcardTextFile
    fun writeFile(file: WildcardTextFile, text: String)
    fun deleteFile(file: WildcardTextFile)
}

object WildcardFolderAccessChecker {
    fun canWriteFolder(context: Context, folderUri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == folderUri && it.isReadPermission && it.isWritePermission
        }
    }
}

class AndroidWildcardFileManager(
    private val context: Context
) : WildcardFileManager {
    override fun listFiles(): List<WildcardTextFile> {
        val folderUri = currentFolderUri()
        val resolver = context.contentResolver
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
        )
        val result = mutableListOf<WildcardTextFile>()
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
                if (WildcardFileParser.tokenFromFileName(fileName) == null) continue
                if (cursor.getString(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR) continue

                val documentId = cursor.getString(idIndex)
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId)
                result += WildcardTextFile(
                    id = documentId,
                    fileName = fileName,
                    documentUri = documentUri.toString()
                )
            }
        } ?: throw WildcardFileException("wildcard 폴더를 읽지 못했습니다. 폴더를 다시 선택해주세요.")

        return result.sortedBy { it.fileName.lowercase() }
    }

    override fun readFile(file: WildcardTextFile): String {
        val uri = Uri.parse(file.documentUri)
        return context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } ?: throw WildcardFileException("${file.fileName} 파일을 열지 못했습니다.")
    }

    override fun createFile(fileName: String): WildcardTextFile {
        val normalizedFileName = WildcardFileName.normalize(fileName)
            ?: throw WildcardFileException("파일명을 입력해주세요.")

        val exists = listFiles().any { it.fileName.equals(normalizedFileName, ignoreCase = true) }
        if (exists) throw WildcardFileException("이미 같은 이름의 파일이 있습니다.")

        val folderUri = currentFolderUri()
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
        )
        val documentUri = DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            "text/plain",
            normalizedFileName
        ) ?: throw WildcardFileException("새 파일을 만들지 못했습니다.")

        return WildcardTextFile(
            id = DocumentsContract.getDocumentId(documentUri),
            fileName = normalizedFileName,
            documentUri = documentUri.toString()
        )
    }

    override fun writeFile(file: WildcardTextFile, text: String) {
        val uri = Uri.parse(file.documentUri)
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw WildcardFileException("${file.fileName} 파일을 저장하지 못했습니다.")

        output.use {
            it.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(text)
            }
        }
    }

    override fun deleteFile(file: WildcardTextFile) {
        val uri = Uri.parse(file.documentUri)
        val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
        if (!deleted) throw WildcardFileException("${file.fileName} 파일을 삭제하지 못했습니다.")
    }

    override fun renameFile(file: WildcardTextFile, newName: String): WildcardTextFile {
        val normalizedFileName = WildcardFileName.normalize(newName)
            ?: throw WildcardFileException("파일 이름을 입력해주세요.")

        val exists = listFiles().any { it.id != file.id && it.fileName.equals(normalizedFileName, ignoreCase = true) }
        if (exists) throw WildcardFileException("이미 같은 이름의 파일이 있습니다.")

        val uri = Uri.parse(file.documentUri)
        val newUri = DocumentsContract.renameDocument(context.contentResolver, uri, normalizedFileName)
            ?: throw WildcardFileException("${file.fileName} 파일 이름을 수정하지 못했습니다.")

        return WildcardTextFile(
            id = DocumentsContract.getDocumentId(newUri),
            fileName = normalizedFileName,
            documentUri = newUri.toString()
        )
    }

    private fun currentFolderUri(): Uri {
        return WildcardFolderStore.getFolderUri(context)
            ?: throw WildcardFileException("wildcard 폴더를 먼저 선택해주세요.")
    }
}
