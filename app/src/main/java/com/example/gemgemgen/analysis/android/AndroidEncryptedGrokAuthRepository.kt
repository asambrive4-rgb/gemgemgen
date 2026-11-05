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
import com.example.gemgemgen.core.credential.android.AndroidKeyStoreCipher
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

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
                                analysisJson.decodeFromString<StoredGrokAuthSession>(plain).toDomain()
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
        val payload = analysisJson.encodeToString(StoredGrokAuthSession.fromDomain(session))
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

    @Serializable
    private data class StoredGrokAuthSession(
        val accessToken: String = "",
        val refreshToken: String? = null,
        val expiresAtMillis: Long? = null,
        val tokenEndpoint: String? = null,
        val accountPreview: String = ""
    ) {
        fun toDomain(): GrokAuthSession? {
            if (accessToken.isBlank()) return null
            return GrokAuthSession(
                accessToken = accessToken,
                refreshToken = refreshToken?.ifBlank { null },
                expiresAtMillis = expiresAtMillis?.takeIf { it > 0L },
                tokenEndpoint = tokenEndpoint?.ifBlank { null },
                accountPreview = accountPreview
            )
        }

        companion object {
            fun fromDomain(session: GrokAuthSession): StoredGrokAuthSession = StoredGrokAuthSession(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken.orEmpty(),
                expiresAtMillis = session.expiresAtMillis ?: -1L,
                tokenEndpoint = session.tokenEndpoint.orEmpty(),
                accountPreview = session.accountPreview
            )
        }
    }

    companion object {
        const val DATASTORE_NAME = "gemgemgen_grok_auth"
        val KEY_SESSION = stringPreferencesKey("session")
        const val KEY_ALIAS = "gemgemgen_grok_auth"
    }
}
