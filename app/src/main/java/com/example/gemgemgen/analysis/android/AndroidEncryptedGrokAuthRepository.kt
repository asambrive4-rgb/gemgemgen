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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
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
    private val cipher: AndroidKeyStoreCipher = AndroidKeyStoreCipher(KEY_ALIAS),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : GrokAuthRepository {
    constructor(context: Context) : this(
        context.applicationContext.grokAuthDataStore,
        AndroidKeyStoreCipher(KEY_ALIAS)
    )

    @Volatile
    private var cachedSession: GrokAuthSession? = null

    init {
        scope.launch {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .collect { preferences ->
                    val encrypted = preferences[KEY_SESSION]
                    if (!encrypted.isNullOrBlank()) {
                        val plain = cipher.decrypt(encrypted)
                        if (!plain.isNullOrBlank()) {
                            cachedSession = runCatching {
                                analysisJson.parseToJsonElement(plain).jsonObject.toSessionOrNull()
                            }.getOrNull()
                        }
                    } else {
                        cachedSession = null
                    }
                }
        }
    }

    override fun loadSession(): GrokAuthSession? {
        return cachedSession
    }

    override fun saveSession(session: GrokAuthSession) {
        cachedSession = session
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
        scope.launch {
            val encrypted = cipher.encrypt(payload)
            dataStore.edit { preferences ->
                preferences[KEY_SESSION] = encrypted
            }
        }
    }

    override fun clearSession() {
        cachedSession = null
        scope.launch {
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
