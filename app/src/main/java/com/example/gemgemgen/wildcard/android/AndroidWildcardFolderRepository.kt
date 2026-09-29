// 역할: 사용자가 지정한 와일드카드 폴더 URI 경로를 SharedPreferences에 저장하고 권한 및 유효성을 관리합니다.
package com.example.gemgemgen.wildcard.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.gemgemgen.wildcard.usecase.FolderSelectionResult
import com.example.gemgemgen.wildcard.usecase.WildcardFolderRepository

class AndroidWildcardFolderRepository(
    private val context: Context
) : WildcardFolderRepository {
    override fun save(folderUri: String): FolderSelectionResult {
        val normalized = folderUri.trim()
        if (normalized.isBlank()) {
            return FolderSelectionResult.Failure("폴더 경로가 비어 있습니다.")
        }
        return try {
            val uri = Uri.parse(normalized)
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            saveFolderUri(context, uri)
            FolderSelectionResult.Success
        } catch (error: SecurityException) {
            FolderSelectionResult.Failure(error.message)
        }
    }

    override fun getFolderUri(): String? = getFolderUri(context)?.toString()

    companion object {
        private const val PREF_NAME = "wildcard_preferences"
        private const val KEY_FOLDER_URI = "folder_uri"

        fun saveFolderUri(context: Context, uri: Uri) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FOLDER_URI, uri.toString())
                .apply()
        }

        fun getFolderUri(context: Context): Uri? {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_FOLDER_URI, null)
                ?.let(Uri::parse)
        }
    }
}
