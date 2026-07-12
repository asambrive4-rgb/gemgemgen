package com.example.gemgemgen.analysis.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.gemgemgen.analysis.usecase.GrokAuthRepository
import com.example.gemgemgen.analysis.usecase.GrokAuthSession
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.Json
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
    private val json = Json { ignoreUnknownKeys = true }

    override fun loadSession(): GrokAuthSession? {
        val encrypted = prefs.getString(KEY_SESSION, null) ?: return null
        val plain = decrypt(encrypted) ?: return null
        return runCatching {
            json.parseToJsonElement(plain).jsonObject.toSessionOrNull()
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
        prefs.edit().putString(KEY_SESSION, encrypt(payload)).apply()
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

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return "${cipher.iv.base64()}:${encrypted.base64()}"
    }

    private fun decrypt(encryptedValue: String): String? {
        return runCatching {
            val parts = encryptedValue.split(":")
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun ByteArray.base64(): String {
        return Base64.encodeToString(this, Base64.NO_WRAP)
    }

    private companion object {
        const val PREFS_NAME = "gemgemgen_grok_auth"
        const val KEY_SESSION = "session"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "gemgemgen_grok_auth"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
