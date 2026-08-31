package com.example.gemgemgen.wildcard.android

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.wildcard.domain.WildcardFileException
import com.example.gemgemgen.wildcard.domain.WildcardFileParser
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository
import java.io.File
import java.io.IOException

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
    private val directStorage = AndroidWildcardDirectStorage()

    override fun listFiles(): List<WildcardTextFile> {
        if (AndroidWildcardDirectStorage.hasAllFilesAccess()) {
            return directStorage.listFiles()
        }
        return documentReader.listDocuments().map { it.toTextFile() }
    }

    override fun readFile(file: WildcardTextFile): String {
        if (AndroidWildcardDirectStorage.hasAllFilesAccess()) {
            return directStorage.readFile(file)
        }
        return documentReader.readText(file.toDocument())
    }

    override fun createFile(fileName: String): WildcardTextFile {
        if (AndroidWildcardDirectStorage.hasAllFilesAccess()) {
            return directStorage.createFile(fileName)
        }
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
        if (AndroidWildcardDirectStorage.hasAllFilesAccess()) {
            directStorage.writeFile(file, text)
            return
        }
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
        if (AndroidWildcardDirectStorage.hasAllFilesAccess()) {
            directStorage.deleteFile(file)
            return
        }
        val uri = documentUriFor(file)
        val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
        if (!deleted) throw WildcardFileException("${file.fileName} 파일을 삭제하지 못했습니다.")
    }

    override fun renameFile(file: WildcardTextFile, newName: String): WildcardTextFile {
        if (AndroidWildcardDirectStorage.hasAllFilesAccess()) {
            return directStorage.renameFile(file, newName)
        }
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

/**
 * Direct shared-storage adapter used after the user grants all-files access.
 * SAF remains the default path when this permission is not granted.
 */
internal class AndroidWildcardDirectStorage {
    fun listFiles(): List<WildcardTextFile> {
        val folder = ensureFolder()
        return folder.listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.mapNotNull { file ->
                if (WildcardFileParser.tokenFromFileName(file.name) == null) return@mapNotNull null
                WildcardTextFile(id = file.name, fileName = file.name)
            }
            ?.sortedBy { it.fileName.lowercase() }
            ?.toList()
            .orEmpty()
    }

    fun readFile(file: WildcardTextFile): String {
        return try {
            fileOnDisk(file).inputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: IOException) {
            throw WildcardFileException("${file.fileName} 파일을 열지 못했습니다.")
        } catch (_: SecurityException) {
            throw WildcardFileException("${file.fileName} 파일을 열지 못했습니다.")
        }
    }

    fun createFile(fileName: String): WildcardTextFile {
        validateFileName(fileName)
        return try {
            val file = File(ensureFolder(), fileName)
            if (!file.createNewFile()) {
                throw WildcardFileException("새 파일을 만들지 못했습니다.")
            }
            WildcardTextFile(id = fileName, fileName = fileName)
        } catch (_: IOException) {
            throw WildcardFileException("새 파일을 만들지 못했습니다.")
        } catch (_: SecurityException) {
            throw WildcardFileException("새 파일을 만들지 못했습니다.")
        }
    }

    fun writeFile(file: WildcardTextFile, text: String) {
        try {
            fileOnDisk(file).writeText(text, Charsets.UTF_8)
        } catch (_: IOException) {
            throw WildcardFileException("${file.fileName} 파일을 저장하지 못했습니다.")
        } catch (_: SecurityException) {
            throw WildcardFileException("${file.fileName} 파일을 저장하지 못했습니다.")
        }
    }

    fun deleteFile(file: WildcardTextFile) {
        try {
            if (!fileOnDisk(file).delete()) {
                throw WildcardFileException("${file.fileName} 파일을 삭제하지 못했습니다.")
            }
        } catch (_: SecurityException) {
            throw WildcardFileException("${file.fileName} 파일을 삭제하지 못했습니다.")
        }
    }

    fun renameFile(file: WildcardTextFile, newName: String): WildcardTextFile {
        validateFileName(newName)
        return try {
            val renamed = fileOnDisk(file)
            val target = File(renamed.parentFile, newName)
            if (!renamed.renameTo(target)) {
                throw WildcardFileException("${file.fileName} 파일 이름을 수정하지 못했습니다.")
            }
            WildcardTextFile(id = newName, fileName = newName)
        } catch (_: SecurityException) {
            throw WildcardFileException("${file.fileName} 파일 이름을 수정하지 못했습니다.")
        }
    }

    fun folderPath(): String = directFolder().absolutePath

    fun ensureFolder(): File {
        val folder = directFolder()
        if (!folder.isDirectory && !folder.mkdirs() && !folder.isDirectory) {
            throw WildcardFileException("wildcard 폴더를 만들지 못했습니다.")
        }
        return folder
    }

    fun canReadFolder(): Boolean {
        val folder = directFolder()
        return folder.isDirectory && folder.canRead()
    }

    fun canWriteFolder(): Boolean {
        val folder = directFolder()
        return folder.isDirectory && folder.canWrite()
    }

    private fun fileOnDisk(file: WildcardTextFile): File {
        validateFileName(file.id)
        if (file.id != file.fileName) {
            throw WildcardFileException("wildcard 파일을 찾지 못했습니다.")
        }
        val diskFile = File(ensureFolder(), file.id)
        if (!diskFile.isFile) {
            throw WildcardFileException("wildcard 파일을 찾지 못했습니다.")
        }
        return diskFile
    }

    private fun validateFileName(fileName: String) {
        if (
            fileName.isBlank() ||
            fileName == "." ||
            fileName == ".." ||
            fileName.contains('/') ||
            fileName.contains('\\')
        ) {
            throw WildcardFileException("파일명이 올바르지 않습니다.")
        }
    }

    private fun directFolder(): File {
        @Suppress("DEPRECATION")
        val externalRoot = Environment.getExternalStorageDirectory()
        val candidates = AppDefaults.WILDCARD_DIRECTORY_CANDIDATES.map { relativePath ->
            File(externalRoot, relativePath)
        }

        return candidates.firstOrNull(::containsWildcardFile)
            ?: candidates.firstOrNull { it.isDirectory }
            ?: candidates.first()
    }

    private fun containsWildcardFile(folder: File): Boolean {
        return folder.listFiles()?.any { file ->
            file.isFile && WildcardFileParser.tokenFromFileName(file.name) != null
        } == true
    }

    companion object {
        fun hasAllFilesAccess(): Boolean {
            return try {
                Environment.isExternalStorageManager()
            } catch (_: RuntimeException) {
                false
            }
        }
    }
}

