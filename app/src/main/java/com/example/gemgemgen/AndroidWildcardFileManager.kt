package com.example.gemgemgen

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

object AndroidWildcardFolderAccessChecker {
    fun canWriteFolder(context: Context, folderUri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == folderUri && it.isReadPermission && it.isWritePermission
        }
    }
}

class AndroidWildcardFileManager(
    private val context: Context
) : WildcardFileManager {
    private val documentReader = AndroidWildcardDocumentReader(context)

    override fun listFiles(): List<WildcardTextFile> {
        return documentReader.listDocuments().map { it.toTextFile() }
    }

    override fun readFile(file: WildcardTextFile): String {
        return documentReader.readText(file.toDocument())
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

        val exists = listFiles().any {
            it.id != file.id && it.fileName.equals(normalizedFileName, ignoreCase = true)
        }
        if (exists) throw WildcardFileException("이미 같은 이름의 파일이 있습니다.")

        val uri = Uri.parse(file.documentUri)
        val newUri = DocumentsContract.renameDocument(
            context.contentResolver,
            uri,
            normalizedFileName
        ) ?: throw WildcardFileException("${file.fileName} 파일 이름을 수정하지 못했습니다.")

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

    private fun WildcardDocument.toTextFile(): WildcardTextFile {
        return WildcardTextFile(
            id = id,
            fileName = fileName,
            documentUri = documentUri.toString()
        )
    }

    private fun WildcardTextFile.toDocument(): WildcardDocument {
        return WildcardDocument(
            id = id,
            fileName = fileName,
            documentUri = Uri.parse(documentUri)
        )
    }
}
