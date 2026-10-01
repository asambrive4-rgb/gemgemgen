// 역할: 기기 보안 저장소에 암호화된 Gemini API 키와 역할별 모델 설정을 저장하고 불러옵니다.
package com.example.gemgemgen.analysis.android

import android.content.Context
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.usecase.GeminiApiKeyRecord
import com.example.gemgemgen.analysis.usecase.GeminiApiKeyRepository
import java.util.UUID
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
    private val cipher = AndroidKeyStoreCipher(KEY_ALIAS)
    private var cachedRecords: List<StoredGeminiApiKey>? = null

    override fun listKeys(): List<GeminiApiKeyRecord> {
        return readRecords().map { it.toRecord() }
    }

    override fun addKey(
        label: String,
        rawKey: String
    ): GeminiApiKeyRecord {
        val existing = readRecords()
        val record = StoredGeminiApiKey(
            id = UUID.randomUUID().toString(),
            label = label,
            encryptedValue = cipher.encrypt(rawKey),
            preview = preview(rawKey),
            isActive = existing.none { it.isActive }
        )
        writeRecords(existing + record)
        return record.toRecord()
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
        return cipher.decrypt(activeRecord.encryptedValue)
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
            editor.putString(modelKey, defaultModelFor(analysisRole, provider))
        }
        editor.apply()
    }

    override fun getRoleModel(role: String): String {
        val analysisRole = AnalysisModelRole.fromStorage(role)
        val provider = AnalysisProvider.fromStorage(getRoleProvider(role))
        val modelKey = roleModelKey(analysisRole.storageValue)
        val stored = prefs.getString(modelKey, null)
        if (!stored.isNullOrBlank() &&
            AnalysisProvider.isModelForProvider(stored, provider)
        ) {
            return stored
        }
        return defaultModelFor(analysisRole, provider)
    }

    override fun setRoleModel(role: String, modelId: String) {
        val analysisRole = AnalysisModelRole.fromStorage(role)
        val provider = AnalysisProvider.fromStorage(getRoleProvider(role))
        val normalized = if (AnalysisProvider.isModelForProvider(modelId, provider)) {
            modelId
        } else {
            defaultModelFor(analysisRole, provider)
        }
        prefs.edit().putString(roleModelKey(analysisRole.storageValue), normalized).apply()
    }

    private fun defaultModelFor(
        analysisRole: AnalysisModelRole,
        provider: AnalysisProvider
    ): String {
        return if (provider == AnalysisModelRole.defaultProvider(analysisRole)) {
            AnalysisModelRole.defaultModel(analysisRole)
        } else {
            AnalysisProvider.defaultModel(provider)
        }
    }

    private fun roleProviderKey(role: String): String = "${KEY_ROLE_PROVIDER_PREFIX}$role"
    private fun roleModelKey(role: String): String = "${KEY_ROLE_MODEL_PREFIX}$role"

    private fun readRecords(): List<StoredGeminiApiKey> {
        cachedRecords?.let { return it }
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            analysisJson.parseToJsonElement(raw)
                .jsonArray
                .mapNotNull { element -> element.jsonObject.toStoredRecordOrNull() }
        }.getOrDefault(emptyList()).also { cachedRecords = it }
    }

    private fun writeRecords(records: List<StoredGeminiApiKey>) {
        cachedRecords = records
        val array = buildJsonArray {
            records.forEach { record ->
                add(
                    buildJsonObject {
                        put("id", JsonPrimitive(record.id))
                        put("label", JsonPrimitive(record.label))
                        put("encryptedValue", JsonPrimitive(record.encryptedValue))
                        put("preview", JsonPrimitive(record.preview))
                        put("isActive", JsonPrimitive(record.isActive))
                    }
                )
            }
        }
        prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private fun JsonObject.toStoredRecordOrNull(): StoredGeminiApiKey? {
        val id = this["id"]?.jsonPrimitive?.content ?: return null
        val label = this["label"]?.jsonPrimitive?.content ?: return null
        val encryptedValue = this["encryptedValue"]?.jsonPrimitive?.content ?: return null
        val preview = this["preview"]?.jsonPrimitive?.content ?: return null
        val isActive = this["isActive"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
            ?: false
        return StoredGeminiApiKey(
            id = id,
            label = label,
            encryptedValue = encryptedValue,
            preview = preview,
            isActive = isActive
        )
    }

    private data class StoredGeminiApiKey(
        val id: String,
        val label: String,
        val encryptedValue: String,
        val preview: String,
        val isActive: Boolean
    ) {
        fun toRecord(): GeminiApiKeyRecord = GeminiApiKeyRecord(
            id = id,
            label = label,
            preview = preview,
            isActive = isActive
        )
    }

    private fun preview(rawKey: String): String {
        return "****${rawKey.takeLast(4)}"
    }

    private companion object {
        const val PREFS_NAME = "gemgemgen_analysis_api_keys"
        const val KEY_RECORDS = "records"
        const val KEY_ROLE_PROVIDER_PREFIX = "role_provider_"
        const val KEY_ROLE_MODEL_PREFIX = "role_model_"
        const val KEY_ALIAS = "gemgemgen_analysis_api_key"
    }
}
