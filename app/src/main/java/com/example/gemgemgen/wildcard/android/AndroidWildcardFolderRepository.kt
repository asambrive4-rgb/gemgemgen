// 역할: Jetpack DataStore를 통해 사용자가 지정한 와일드카드 폴더 URI 경로를 저장하고 권한 및 유효성을 관리합니다.
package com.example.gemgemgen.wildcard.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.wildcard.usecase.FolderSelectionResult
import com.example.gemgemgen.wildcard.usecase.WildcardFolderRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.wildcardFolderDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AndroidWildcardFolderRepository.DATASTORE_NAME
)

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
        const val DATASTORE_NAME = "wildcard_preferences"
        val KEY_FOLDER_URI = stringPreferencesKey("folder_uri")

        fun saveFolderUri(context: Context, uri: Uri) {
            runBlocking {
                context.applicationContext.wildcardFolderDataStore.edit { preferences ->
                    preferences[KEY_FOLDER_URI] = uri.toString()
                }
            }
        }

        fun getFolderUri(context: Context): Uri? {
            val raw = runBlocking {
                context.applicationContext.wildcardFolderDataStore.data
                    .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                    .map { it[KEY_FOLDER_URI] }
                    .first()
            }
            return raw?.let(Uri::parse)
        }
    }
}
