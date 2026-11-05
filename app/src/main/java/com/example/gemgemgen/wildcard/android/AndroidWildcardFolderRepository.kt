// 역할: Jetpack DataStore를 통해 와일드카드 폴더 URI 경로 및 상용구 연동 대상 파일 목록을 영구 저장하고 권한과 실시간 변경 Flow를 제공합니다.
package com.example.gemgemgen.wildcard.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.wildcard.usecase.FolderSelectionResult
import com.example.gemgemgen.wildcard.usecase.WildcardFolderRepository
import com.example.gemgemgen.wildcard.usecase.WildcardLinkedFilesRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.wildcardFolderDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AndroidWildcardFolderRepository.DATASTORE_NAME
)

class AndroidWildcardFolderRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : WildcardFolderRepository, WildcardLinkedFilesRepository {
    @Volatile
    private var cachedLinkedFiles: Set<String>? = null

    init {
        scope.launch {
            observeLinkedFileNames().collect { files ->
                cachedLinkedFiles = files
            }
        }
        initCachedFolderUri(context)
    }

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

    override fun observeLinkedFileNames(): Flow<Set<String>> {
        return context.applicationContext.wildcardFolderDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val files = preferences[KEY_LINKED_FILES].orEmpty()
                cachedLinkedFiles = files
                files
            }
    }

    override fun getLinkedFileNames(): Set<String> {
        return cachedLinkedFiles ?: emptySet()
    }

    override suspend fun saveLinkedFileNames(fileNames: Set<String>) {
        cachedLinkedFiles = fileNames
        context.applicationContext.wildcardFolderDataStore.edit { preferences ->
            preferences[KEY_LINKED_FILES] = fileNames
        }
    }

    companion object {
        const val DATASTORE_NAME = "wildcard_preferences"
        val KEY_FOLDER_URI = stringPreferencesKey("folder_uri")
        val KEY_LINKED_FILES = stringSetPreferencesKey("linked_autocomplete_files")

        @Volatile
        private var cachedFolderUri: Uri? = null
        @Volatile
        private var isObserverStarted: Boolean = false
        private val globalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        internal fun initCachedFolderUri(context: Context) {
            if (isObserverStarted) return
            synchronized(this) {
                if (isObserverStarted) return
                isObserverStarted = true
                globalScope.launch {
                    context.applicationContext.wildcardFolderDataStore.data
                        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                        .collect { preferences ->
                            cachedFolderUri = preferences[KEY_FOLDER_URI]?.let(Uri::parse)
                        }
                }
            }
        }

        fun saveFolderUri(context: Context, uri: Uri) {
            cachedFolderUri = uri
            initCachedFolderUri(context)
            globalScope.launch {
                context.applicationContext.wildcardFolderDataStore.edit { preferences ->
                    preferences[KEY_FOLDER_URI] = uri.toString()
                }
            }
        }

        fun getFolderUri(context: Context): Uri? {
            initCachedFolderUri(context)
            return cachedFolderUri
        }
    }
}
