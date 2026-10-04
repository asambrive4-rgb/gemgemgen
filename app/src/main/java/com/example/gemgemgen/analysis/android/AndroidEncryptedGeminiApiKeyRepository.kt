// 역할: Jetpack DataStore와 기기 보안 키체인을 활용하여 암호화된 Gemini API 키와 역할별 모델 설정을 로컬에 저장하고 불러옵니다.
package com.example.gemgemgen.analysis.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.usecase.GeminiApiKeyRecord
import com.example.gemgemgen.analysis.usecase.GeminiApiKeyRepository
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val Context.geminiApiKeyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AndroidEncryptedGeminiApiKeyRepository.DATASTORE_NAME
)

class AndroidEncryptedGeminiApiKeyRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: AndroidKeyStoreCipher = AndroidKeyStoreCipher(KEY_ALIAS)
) : GeminiApiKeyRepository {
    constructor(context: Context) : this(
        context.applicationContext.geminiApiKeyDataStore,
        AndroidKeyStoreCipher(KEY_ALIAS)
    )

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
        val key = stringPreferencesKey(roleProviderKey(analysisRole.storageValue))
        val stored = runBlocking {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { it[key] }
                .first()
        }
        if (!stored.isNullOrBlank()) {
            return AnalysisProvider.fromStorage(stored).storageValue
        }
        return AnalysisModelRole.defaultProvider(analysisRole).storageValue
    }

    override fun setRoleProvider(role: String, providerId: String) {
        val analysisRole = AnalysisModelRole.fromStorage(role)
        val provider = AnalysisProvider.fromStorage(providerId)
        val providerPrefKey = stringPreferencesKey(roleProviderKey(analysisRole.storageValue))
        val modelPrefKey = stringPreferencesKey(roleModelKey(analysisRole.storageValue))
        runBlocking {
            dataStore.edit { preferences ->
                preferences[providerPrefKey] = provider.storageValue
                val currentModel = preferences[modelPrefKey]
                if (currentModel.isNullOrBlank() ||
                    !AnalysisProvider.isModelForProvider(currentModel, provider)
                ) {
                    preferences[modelPrefKey] = defaultModelFor(analysisRole, provider)
                }
            }
        }
    }

    override fun getRoleModel(role: String): String {
        val analysisRole = AnalysisModelRole.fromStorage(role)
        val provider = AnalysisProvider.fromStorage(getRoleProvider(role))
        val key = stringPreferencesKey(roleModelKey(analysisRole.storageValue))
        val stored = runBlocking {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { it[key] }
                .first()
        }
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
        val key = stringPreferencesKey(roleModelKey(analysisRole.storageValue))
        runBlocking {
            dataStore.edit { preferences ->
                preferences[key] = normalized
            }
        }
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
        val raw = runBlocking {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { it[KEY_PREF_RECORDS] }
                .first()
        } ?: return emptyList()
        return runCatching {
            analysisJson.parseToJsonElement(raw)
                .jsonArray
                .mapNotNull { element -> element.jsonObject.toStoredRecordOrNull() }
        }.getOrDefault(emptyList()).also { cachedRecords = it }
    }

    private fun writeRecords(records: List<StoredGeminiApiKey>) {
        cachedRecords = records
        val jsonArray = buildJsonArray {
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
        }.toString()
        runBlocking {
            dataStore.edit { preferences ->
                preferences[KEY_PREF_RECORDS] = jsonArray
            }
        }
    }

    private fun JsonObject.toStoredRecordOrNull(): StoredGeminiApiKey? {
        val id = this["id"]?.jsonPrimitive?.content.orEmpty()
        val label = this["label"]?.jsonPrimitive?.content.orEmpty()
        val encryptedValue = this["encryptedValue"]?.jsonPrimitive?.content.orEmpty()
        val preview = this["preview"]?.jsonPrimitive?.content.orEmpty()
        val isActive = this["isActive"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        if (id.isBlank() || encryptedValue.isBlank()) return null
        return StoredGeminiApiKey(
            id = id,
            label = label.ifBlank { "API 키" },
            encryptedValue = encryptedValue,
            preview = preview.ifBlank { preview(encryptedValue) },
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

    companion object {
        const val DATASTORE_NAME = "gemgemgen_analysis_api_keys"
        val KEY_PREF_RECORDS = stringPreferencesKey("records")
        const val KEY_ROLE_PROVIDER_PREFIX = "role_provider_"
        const val KEY_ROLE_MODEL_PREFIX = "role_model_"
        const val KEY_ALIAS = "gemgemgen_analysis_api_key"
    }
}
