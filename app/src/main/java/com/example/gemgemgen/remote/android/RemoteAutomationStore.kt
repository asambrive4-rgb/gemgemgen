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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.remoteAutomationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = RemoteAutomationStore.DATASTORE_NAME
)

internal class RemoteAutomationStore(
    private val dataStore: DataStore<Preferences>
) {
    constructor(context: Context) : this(context.applicationContext.remoteAutomationDataStore)

    fun mode(): AutomationMode {
        val raw = runBlocking {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { it[KEY_MODE] }
                .first()
        }
        return AutomationMode.fromStorageValue(raw)
    }

    fun saveMode(mode: AutomationMode) {
        runBlocking {
            dataStore.edit { preferences ->
                preferences[KEY_MODE] = mode.storageValue
            }
        }
    }

    fun installationId(): String {
        val saved = runBlocking {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { it[KEY_INSTALLATION_ID] }
                .first()
        }
        if (!saved.isNullOrBlank()) return saved
        val generated = UUID.randomUUID().toString()
        runBlocking {
            dataStore.edit { preferences ->
                preferences[KEY_INSTALLATION_ID] = generated
            }
        }
        return generated
    }

    fun pairedSender(): PairedSender? {
        val (senderId, token) = runBlocking {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { Pair(it[KEY_PAIRED_SENDER_ID].orEmpty(), it[KEY_PAIRED_SENDER_TOKEN].orEmpty()) }
                .first()
        }
        return if (senderId.isBlank() || token.isBlank()) null else PairedSender(senderId, token)
    }

    fun savePairedSender(senderId: String, token: String) {
        runBlocking {
            dataStore.edit { preferences ->
                preferences[KEY_PAIRED_SENDER_ID] = senderId
                preferences[KEY_PAIRED_SENDER_TOKEN] = token
            }
        }
    }

    fun pairedReceiver(): PairedReceiver? {
        val (receiverId, receiverName, token) = runBlocking {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map {
                    Triple(
                        it[KEY_PAIRED_RECEIVER_ID].orEmpty(),
                        it[KEY_PAIRED_RECEIVER_NAME].orEmpty(),
                        it[KEY_PAIRED_RECEIVER_TOKEN].orEmpty()
                    )
                }
                .first()
        }
        return if (receiverId.isBlank() || token.isBlank()) {
            null
        } else {
            PairedReceiver(receiverId, receiverName, token)
        }
    }

    fun savePairedReceiver(receiverId: String, receiverName: String, token: String) {
        runBlocking {
            dataStore.edit { preferences ->
                preferences[KEY_PAIRED_RECEIVER_ID] = receiverId
                preferences[KEY_PAIRED_RECEIVER_NAME] = receiverName
                preferences[KEY_PAIRED_RECEIVER_TOKEN] = token
            }
        }
    }

    fun isUserDisconnected(): Boolean {
        return runBlocking {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { it[KEY_USER_DISCONNECTED] ?: false }
                .first()
        }
    }

    fun saveUserDisconnected(disconnected: Boolean) {
        runBlocking {
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
