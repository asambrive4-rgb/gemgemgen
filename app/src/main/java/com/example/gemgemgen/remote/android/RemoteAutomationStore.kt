// 역할: Jetpack DataStore를 통해 원격 자동화 수신 기록 및 세션 연결 정보를 로컬에 보관하는 저장소입니다.
package com.example.gemgemgen.remote.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.remote.domain.AutomationMode
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
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
    private var cachedPairedSender: PairedSender? = null
    @Volatile
    private var cachedPairedReceiver: PairedReceiver? = null
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
                    val senderId = prefs[KEY_PAIRED_SENDER_ID].orEmpty()
                    val senderToken = prefs[KEY_PAIRED_SENDER_TOKEN].orEmpty()
                    cachedPairedSender = if (senderId.isBlank() || senderToken.isBlank()) null else PairedSender(senderId, senderToken)

                    val receiverId = prefs[KEY_PAIRED_RECEIVER_ID].orEmpty()
                    val receiverName = prefs[KEY_PAIRED_RECEIVER_NAME].orEmpty()
                    val receiverToken = prefs[KEY_PAIRED_RECEIVER_TOKEN].orEmpty()
                    cachedPairedReceiver = if (receiverId.isBlank() || receiverToken.isBlank()) null else PairedReceiver(receiverId, receiverName, receiverToken)

                    cachedUserDisconnected = prefs[KEY_USER_DISCONNECTED] ?: false
                }
        }
    }

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

    fun pairedSender(): PairedSender? = cachedPairedSender

    fun savePairedSender(senderId: String, token: String) {
        cachedPairedSender = PairedSender(senderId, token)
        scope.launch {
            dataStore.edit { preferences ->
                preferences[KEY_PAIRED_SENDER_ID] = senderId
                preferences[KEY_PAIRED_SENDER_TOKEN] = token
            }
        }
    }

    fun pairedReceiver(): PairedReceiver? = cachedPairedReceiver

    fun savePairedReceiver(receiverId: String, receiverName: String, token: String) {
        cachedPairedReceiver = PairedReceiver(receiverId, receiverName, token)
        scope.launch {
            dataStore.edit { preferences ->
                preferences[KEY_PAIRED_RECEIVER_ID] = receiverId
                preferences[KEY_PAIRED_RECEIVER_NAME] = receiverName
                preferences[KEY_PAIRED_RECEIVER_TOKEN] = token
            }
        }
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

    data class PairedSender(val senderId: String, val token: String)
    data class PairedReceiver(
        val receiverId: String,
        val receiverName: String,
        val token: String
    )

    companion object {
        const val DATASTORE_NAME = "remote_automation"
        val KEY_MODE = stringPreferencesKey("mode")
        val KEY_INSTALLATION_ID = stringPreferencesKey("installation_id")
        val KEY_PAIRED_SENDER_ID = stringPreferencesKey("paired_sender_id")
        val KEY_PAIRED_SENDER_TOKEN = stringPreferencesKey("paired_sender_token")
        val KEY_PAIRED_RECEIVER_ID = stringPreferencesKey("paired_receiver_id")
        val KEY_PAIRED_RECEIVER_NAME = stringPreferencesKey("paired_receiver_name")
        val KEY_PAIRED_RECEIVER_TOKEN = stringPreferencesKey("paired_receiver_token")
        val KEY_USER_DISCONNECTED = booleanPreferencesKey("user_disconnected")
    }
}
