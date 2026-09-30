// 역할: 기기 보안 저장소에 암호화된 Grok 인증 토큰을 저장하고 관리합니다.
package com.example.gemgemgen.analysis.android

import android.content.Context
import com.example.gemgemgen.analysis.usecase.GrokAuthRepository
import com.example.gemgemgen.analysis.usecase.GrokAuthSession
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AndroidEncryptedGrokAuthRepository(
    context: Context
) : GrokAuthRepository {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val cipher = AndroidKeyStoreCipher(KEY_ALIAS)

    override fun loadSession(): GrokAuthSession? {
        val encrypted = prefs.getString(KEY_SESSION, null) ?: return null
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
        prefs.edit().putString(KEY_SESSION, cipher.encrypt(payload)).apply()
    }

    override fun clearSession() {
        prefs.edit().remove(KEY_SESSION).apply()
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

    private companion object {
        const val PREFS_NAME = "gemgemgen_grok_auth"
        const val KEY_SESSION = "session"
        const val KEY_ALIAS = "gemgemgen_grok_auth"
    }
}
