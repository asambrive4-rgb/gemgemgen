// 역할: DataStore에 사용자가 만든 추천 방향 칩을 JSON으로 저장하고 불러옵니다.
package com.example.gemgemgen.analysis.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.analysis.domain.AnalysisDirection
import com.example.gemgemgen.analysis.usecase.AnalysisCustomDirectionRepository
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val Context.customDirectionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AndroidAnalysisCustomDirectionRepository.DATASTORE_NAME
)

class AndroidAnalysisCustomDirectionRepository internal constructor(
    private val dataStore: DataStore<Preferences>
) : AnalysisCustomDirectionRepository {
    constructor(context: Context) : this(context.applicationContext.customDirectionDataStore)

    override fun load(): List<AnalysisDirection> {
        val raw = runBlocking {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { it[KEY_DIRECTIONS] }
                .first()
        } ?: return emptyList()
        return runCatching {
            analysisJson.parseToJsonElement(raw).jsonArray.map { element ->
                val item = element.jsonObject
                AnalysisDirection(
                    id = item.getValue("id").jsonPrimitive.content,
                    title = item.getValue("title").jsonPrimitive.content,
                    hint = item.getValue("hint").jsonPrimitive.content
                )
            }
        }.getOrDefault(emptyList())
    }

    override fun save(directions: List<AnalysisDirection>) {
        val payload = JsonArray(directions.map { it.toJson() }).toString()
        runBlocking {
            dataStore.edit { it[KEY_DIRECTIONS] = payload }
        }
    }

    private fun AnalysisDirection.toJson(): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("title", JsonPrimitive(title))
        put("hint", JsonPrimitive(hint))
    }

    companion object {
        const val DATASTORE_NAME = "gemgemgen_analysis_custom_directions"
        val KEY_DIRECTIONS = stringPreferencesKey("directions")
    }
}
