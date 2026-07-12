package com.example.gemgemgen.analysis.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.usecase.GeminiApiKeyRecord
import com.example.gemgemgen.analysis.usecase.GeminiApiKeyRepository
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AndroidEncryptedGeminiApiKeyRepository(
    context: Context
) : GeminiApiKeyRepository {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val json = Json { ignoreUnknownKeys = true }

    override fun listKeys(): List<GeminiApiKeyRecord> {
        return readRecords()
    }

    override fun addKey(
        label: String,
        rawKey: String,
        createdAtMillis: Long
    ): GeminiApiKeyRecord {
        val existing = readRecords()
        val record = GeminiApiKeyRecord(
            id = UUID.randomUUID().toString(),
            label = label,
            encryptedValue = encrypt(rawKey),
            preview = preview(rawKey),
            createdAtMillis = createdAtMillis,
            isActive = existing.none { it.isActive }
        )
        writeRecords(existing + record)
        return record
    }

    override fun deleteKey(id: String) {
        writeRecords(readRecords().filterNot { it.id == id })
    }

    override fun activateKey(id: String) {
        writeRecords(
            readRecords().map { record ->
                record.copy(isActive = record.id == id)
            }
        )
    }

    override fun activeKeyValue(): String? {
        val activeRecord = readRecords().firstOrNull { it.isActive } ?: return null
        return decrypt(activeRecord.encryptedValue)
    }

    override fun updateKeyLabel(id: String, newLabel: String) {
        writeRecords(
            readRecords().map { record ->
                if (record.id == id) {
                    record.copy(label = newLabel)
                } else {
                    record
                }
            }
        )
    }

    override fun getRoleProvider(role: String): String {
        val analysisRole = AnalysisModelRole.fromStorage(role)
        val key = roleProviderKey(analysisRole.storageValue)
        val stored = prefs.getString(key, null)
        if (!stored.isNullOrBlank()) {
            return AnalysisProvider.fromStorage(stored).storageValue
        }
        // 구버전 단일 프로바이더 마이그레이션
        val legacy = prefs.getString(KEY_SELECTED_PROVIDER, null)
        if (!legacy.isNullOrBlank()) {
            val migrated = AnalysisProvider.fromStorage(legacy).storageValue
            prefs.edit().putString(key, migrated).apply()
            return migrated
        }
        return AnalysisModelRole.defaultProvider(analysisRole).storageValue
    }

    override fun setRoleProvider(role: String, providerId: String) {
        val analysisRole = AnalysisModelRole.fromStorage(role)
        val provider = AnalysisProvider.fromStorage(providerId)
        val modelKey = roleModelKey(analysisRole.storageValue)
        val currentModel = prefs.getString(modelKey, null)
        val editor = prefs.edit()
            .putString(roleProviderKey(analysisRole.storageValue), provider.storageValue)
        if (currentModel.isNullOrBlank() ||
            !AnalysisProvider.isModelForProvider(currentModel, provider)
        ) {
            editor.putString(
                modelKey,
                if (provider == AnalysisModelRole.defaultProvider(analysisRole)) {
                    AnalysisModelRole.defaultModel(analysisRole)
                } else {
                    AnalysisProvider.defaultModel(provider)
                }
            )
        }
        editor.apply()
    }

    override fun getRoleModel(role: String): String {
        val analysisRole = AnalysisModelRole.fromStorage(role)
        val provider = AnalysisProvider.fromStorage(getRoleProvider(role))
        val modelKey = roleModelKey(analysisRole.storageValue)
        val stored = prefs.getString(modelKey, null)
        if (!stored.isNullOrBlank() && AnalysisProvider.isModelForProvider(stored, provider)) {
            return stored
        }
        // 구버전 단일 모델 마이그레이션
        val legacy = prefs.getString(KEY_SELECTED_MODEL, null)
        if (!legacy.isNullOrBlank() && AnalysisProvider.isModelForProvider(legacy, provider)) {
            prefs.edit().putString(modelKey, legacy).apply()
            return legacy
        }
        return if (provider == AnalysisModelRole.defaultProvider(analysisRole)) {
            AnalysisModelRole.defaultModel(analysisRole)
        } else {
            AnalysisProvider.defaultModel(provider)
        }
    }

    override fun setRoleModel(role: String, modelId: String) {
        val analysisRole = AnalysisModelRole.fromStorage(role)
        val provider = AnalysisProvider.fromStorage(getRoleProvider(role))
        val normalized = if (AnalysisProvider.isModelForProvider(modelId, provider)) {
            modelId
        } else if (provider == AnalysisModelRole.defaultProvider(analysisRole)) {
            AnalysisModelRole.defaultModel(analysisRole)
        } else {
            AnalysisProvider.defaultModel(provider)
        }
        prefs.edit().putString(roleModelKey(analysisRole.storageValue), normalized).apply()
    }

    private fun roleProviderKey(role: String): String = "${KEY_ROLE_PROVIDER_PREFIX}$role"
    private fun roleModelKey(role: String): String = "${KEY_ROLE_MODEL_PREFIX}$role"

    private fun readRecords(): List<GeminiApiKeyRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(raw)
                .jsonArray
                .mapNotNull { element -> element.jsonObject.toRecordOrNull() }
        }.getOrDefault(emptyList())
    }

    private fun writeRecords(records: List<GeminiApiKeyRecord>) {
        val array = buildJsonArray {
            records.forEach { record ->
                add(
                    buildJsonObject {
                        put("id", JsonPrimitive(record.id))
                        put("label", JsonPrimitive(record.label))
                        put("encryptedValue", JsonPrimitive(record.encryptedValue))
                        put("preview", JsonPrimitive(record.preview))
                        put("createdAtMillis", JsonPrimitive(record.createdAtMillis))
                        put("isActive", JsonPrimitive(record.isActive))
                    }
                )
            }
        }
        prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private fun JsonObject.toRecordOrNull(): GeminiApiKeyRecord? {
        val id = this["id"]?.jsonPrimitive?.content ?: return null
        val label = this["label"]?.jsonPrimitive?.content ?: return null
        val encryptedValue = this["encryptedValue"]?.jsonPrimitive?.content ?: return null
        val preview = this["preview"]?.jsonPrimitive?.content ?: return null
        val createdAtMillis = this["createdAtMillis"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: return null
        val isActive = this["isActive"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
            ?: false
        return GeminiApiKeyRecord(
            id = id,
            label = label,
            encryptedValue = encryptedValue,
            preview = preview,
            createdAtMillis = createdAtMillis,
            isActive = isActive
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

    private fun preview(rawKey: String): String {
        return "****${rawKey.takeLast(4)}"
    }

    private companion object {
        const val PREFS_NAME = "gemgemgen_analysis_api_keys"
        const val KEY_RECORDS = "records"
        const val KEY_SELECTED_MODEL = "selected_model_id"
        const val KEY_SELECTED_PROVIDER = "selected_provider_id"
        const val KEY_ROLE_PROVIDER_PREFIX = "role_provider_"
        const val KEY_ROLE_MODEL_PREFIX = "role_model_"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "gemgemgen_analysis_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
