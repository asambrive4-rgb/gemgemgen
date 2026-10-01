// 역할: 기기 저장소의 와일드카드 텍스트 파일들을 읽고 쓰는 파일 입출력을 처리합니다.
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
    private val directStorage = AndroidWildcardDirectStorage()

    override fun listFiles(): List<WildcardTextFile> {
        if (AndroidWildcardDirectStorage.hasAllFilesAccess()) {
            return directStorage.listFiles()
        }
        return listSafFiles()
    }

    override fun readFile(file: WildcardTextFile): String {
        if (AndroidWildcardDirectStorage.hasAllFilesAccess()) {
            return directStorage.readFile(file)
        }
        val uri = documentUriFor(file)
        return context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } ?: throw WildcardFileException("${file.fileName} 파일을 열지 못했습니다.")
    }

    override fun createFile(fileName: String): WildcardTextFile {
        validateWildcardFileName(fileName)
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
        validateWildcardFileName(newName)
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

    private fun listSafFiles(): List<WildcardTextFile> {
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

        val cursor = resolver.query(childUri, projection, null, null, null)
            ?: throw WildcardFileException("wildcard 폴더를 읽지 못했습니다. 폴더를 다시 선택해주세요.")

        cursor.use {
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val fileName = cursor.getString(nameIndex) ?: continue
                if (WildcardFileParser.tokenFromFileName(fileName) == null) continue
                if (cursor.getString(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR) continue

                val documentId = cursor.getString(idIndex)
                result += WildcardTextFile(
                    id = documentId,
                    fileName = fileName
                )
            }
        }

        return result.sortedBy { it.fileName.lowercase() }
    }

    private fun currentFolderUri(): Uri {
        return AndroidWildcardFolderRepository.getFolderUri(context)
            ?: throw WildcardFileException("wildcard 폴더를 먼저 선택해주세요.")
    }

    private fun documentUriFor(file: WildcardTextFile): Uri {
        val folderUri = currentFolderUri()
        return DocumentsContract.buildDocumentUriUsingTree(folderUri, file.id)
    }
}

internal fun validateWildcardFileName(fileName: String) {
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

/**
 * Direct shared-storage adapter used after the user grants all-files access.
 * SAF remains the default path when this permission is not granted.
 */
internal class AndroidWildcardDirectStorage {
    fun listFiles(): List<WildcardTextFile> {
        val now = System.currentTimeMillis()
        val cached = cachedFiles
        val folder = cachedFolder
        if (cached != null && folder != null && now - cachedFilesAtMs < FOLDER_CACHE_TTL_MS && folder.isDirectory) {
            return cached
        }
        val targetFolder = directFolder()
        if (!targetFolder.isDirectory && !targetFolder.mkdirs() && !targetFolder.isDirectory) {
            throw WildcardFileException("wildcard 폴더를 만들지 못했습니다.")
        }
        val parsed = parseWildcardFiles(targetFolder)
        cachedFolder = targetFolder
        cachedFolderAtMs = now
        cachedFiles = parsed
        cachedFilesAtMs = now
        return parsed
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
        validateWildcardFileName(fileName)
        return try {
            val file = File(ensureFolder(), fileName)
            if (!file.createNewFile()) {
                throw WildcardFileException("새 파일을 만들지 못했습니다.")
            }
            clearCache()
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
            clearCache()
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
            clearCache()
        } catch (_: SecurityException) {
            throw WildcardFileException("${file.fileName} 파일을 삭제하지 못했습니다.")
        }
    }

    fun renameFile(file: WildcardTextFile, newName: String): WildcardTextFile {
        validateWildcardFileName(newName)
        return try {
            val renamed = fileOnDisk(file)
            val target = File(renamed.parentFile, newName)
            if (!renamed.renameTo(target)) {
                throw WildcardFileException("${file.fileName} 파일 이름을 수정하지 못했습니다.")
            }
            clearCache()
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

    private fun fileOnDisk(file: WildcardTextFile): File {
        validateWildcardFileName(file.id)
        if (file.id != file.fileName) {
            throw WildcardFileException("wildcard 파일을 찾지 못했습니다.")
        }
        val diskFile = File(ensureFolder(), file.id)
        if (!diskFile.isFile) {
            throw WildcardFileException("wildcard 파일을 찾지 못했습니다.")
        }
        return diskFile
    }

    private fun directFolder(): File {
        val now = System.currentTimeMillis()
        cachedFolder?.let { cached ->
            if (now - cachedFolderAtMs < FOLDER_CACHE_TTL_MS && cached.isDirectory) {
                return cached
            }
        }
        @Suppress("DEPRECATION")
        val externalRoot = Environment.getExternalStorageDirectory()
        val candidates = AppDefaults.WILDCARD_DIRECTORY_CANDIDATES.map { relativePath ->
            File(externalRoot, relativePath)
        }
        val matched = candidates.firstOrNull { candidate ->
            candidate.isDirectory && candidate.listFiles { f ->
                f.isFile && f.name.endsWith(".txt", ignoreCase = true)
            }?.isNotEmpty() == true
        } ?: candidates.firstOrNull { it.isDirectory } ?: candidates.first()

        cachedFolder = matched
        cachedFolderAtMs = now
        return matched
    }

    private fun parseWildcardFiles(folder: File): List<WildcardTextFile> {
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

    companion object {
        private const val FOLDER_CACHE_TTL_MS = 2_000L
        @Volatile private var cachedFolder: File? = null
        @Volatile private var cachedFolderAtMs: Long = 0L
        @Volatile private var cachedFiles: List<WildcardTextFile>? = null
        @Volatile private var cachedFilesAtMs: Long = 0L

        fun clearCache() {
            cachedFiles = null
            cachedFilesAtMs = 0L
        }

        fun hasAllFilesAccess(): Boolean {
            return try {
                Environment.isExternalStorageManager()
            } catch (_: RuntimeException) {
                false
            }
        }
    }
}
