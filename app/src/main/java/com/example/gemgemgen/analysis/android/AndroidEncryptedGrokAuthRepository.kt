// 역할: Jetpack DataStore와 기기 보안 키체인을 활용하여 암호화된 Grok 인증 토큰을 저장하고 관리합니다.
package com.example.gemgemgen.analysis.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.analysis.usecase.GrokAuthRepository
import com.example.gemgemgen.analysis.usecase.GrokAuthSession
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val Context.grokAuthDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AndroidEncryptedGrokAuthRepository.DATASTORE_NAME
)

class AndroidEncryptedGrokAuthRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: AndroidKeyStoreCipher = AndroidKeyStoreCipher(KEY_ALIAS)
) : GrokAuthRepository {
    constructor(context: Context) : this(
        context.applicationContext.grokAuthDataStore,
        AndroidKeyStoreCipher(KEY_ALIAS)
    )

    override fun loadSession(): GrokAuthSession? {
        val encrypted = runBlocking {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { it[KEY_SESSION] }
                .first()
        } ?: return null
        val plain = cipher.decrypt(encrypted) ?: return null
        return runCatching {
            analysisJson.parseToJsonElement(plain).jsonObject.toSessionOrNull()
        }.getOrNull()
    }

    override fun saveSession(session: GrokAuthSession) {
        val payload = buildJsonObject {
            put("accessToken", JsonPrimitive(session.accessToken))
            put("refreshToken", JsonPrimitive(session.refreshToken.orEmpty()))
            put(
                "expiresAtMillis",
                JsonPrimitive(session.expiresAtMillis ?: -1L)
            )
            put("tokenEndpoint", JsonPrimitive(session.tokenEndpoint.orEmpty()))
            put("accountPreview", JsonPrimitive(session.accountPreview))
        }.toString()
        runBlocking {
            dataStore.edit { preferences ->
                preferences[KEY_SESSION] = cipher.encrypt(payload)
            }
        }
    }

    override fun clearSession() {
        runBlocking {
            dataStore.edit { preferences ->
                preferences.remove(KEY_SESSION)
            }
        }
    }

    private fun JsonObject.toSessionOrNull(): GrokAuthSession? {
        val accessToken = this["accessToken"]?.jsonPrimitive?.content.orEmpty()
        if (accessToken.isBlank()) return null
        val refreshRaw = this["refreshToken"]?.jsonPrimitive?.content.orEmpty()
        val expiresRaw = this["expiresAtMillis"]?.jsonPrimitive?.content?.toLongOrNull()
        val tokenEndpointRaw = this["tokenEndpoint"]?.jsonPrimitive?.content.orEmpty()
        val preview = this["accountPreview"]?.jsonPrimitive?.content.orEmpty()
        return GrokAuthSession(
            accessToken = accessToken,
            refreshToken = refreshRaw.ifBlank { null },
            expiresAtMillis = expiresRaw?.takeIf { it > 0L },
            tokenEndpoint = tokenEndpointRaw.ifBlank { null },
            accountPreview = preview
        )
    }

    companion object {
        const val DATASTORE_NAME = "gemgemgen_grok_auth"
        val KEY_SESSION = stringPreferencesKey("session")
        const val KEY_ALIAS = "gemgemgen_grok_auth"
    }
}
