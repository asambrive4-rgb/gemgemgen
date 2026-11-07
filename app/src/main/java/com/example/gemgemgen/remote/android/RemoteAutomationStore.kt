// 역할: Jetpack DataStore를 통해 원격 자동화 동작 모드 및 기기 설치 식별자 정보를 로컬에 보관하는 저장소입니다.
package com.example.gemgemgen.remote.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.automation.domain.AutomationMode
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.remoteAutomationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = RemoteAutomationStore.DATASTORE_NAME
)

internal class RemoteAutomationStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    constructor(context: Context) : this(context.applicationContext.remoteAutomationDataStore)

    @Volatile
    private var cachedMode: AutomationMode = AutomationMode.NORMAL
    @Volatile
    private var cachedInstallationId: String? = null
    @Volatile
    private var cachedUserDisconnected: Boolean = false

    init {
        scope.launch {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .collect { prefs ->
                    cachedMode = AutomationMode.fromStorageValue(prefs[KEY_MODE])
                    val savedInstId = prefs[KEY_INSTALLATION_ID]
                    if (!savedInstId.isNullOrBlank()) {
                        cachedInstallationId = savedInstId
                    }
                    cachedUserDisconnected = prefs[KEY_USER_DISCONNECTED] ?: false
                }
        }
    }

    val modeFlow: Flow<AutomationMode> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { AutomationMode.fromStorageValue(it[KEY_MODE]) }

    fun mode(): AutomationMode = cachedMode

    fun saveMode(mode: AutomationMode) {
        cachedMode = mode
        scope.launch {
            dataStore.edit { preferences ->
                preferences[KEY_MODE] = mode.storageValue
            }
        }
    }

    fun installationId(): String {
        cachedInstallationId?.let { return it }
        val generated = UUID.randomUUID().toString()
        cachedInstallationId = generated
        scope.launch {
            dataStore.edit { preferences ->
                val current = preferences[KEY_INSTALLATION_ID]
                if (current.isNullOrBlank()) {
                    preferences[KEY_INSTALLATION_ID] = generated
                } else {
                    cachedInstallationId = current
                }
            }
        }
        return generated
    }

    fun isUserDisconnected(): Boolean = cachedUserDisconnected

    fun saveUserDisconnected(disconnected: Boolean) {
        cachedUserDisconnected = disconnected
        scope.launch {
            dataStore.edit { preferences ->
                preferences[KEY_USER_DISCONNECTED] = disconnected
            }
        }
    }

    companion object {
        const val DATASTORE_NAME = "remote_automation"
        val KEY_MODE = stringPreferencesKey("mode")
        val KEY_INSTALLATION_ID = stringPreferencesKey("installation_id")
        val KEY_USER_DISCONNECTED = booleanPreferencesKey("user_disconnected")
    }
}
