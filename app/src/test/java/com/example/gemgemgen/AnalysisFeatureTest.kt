package com.example.gemgemgen

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.text.TextRange
import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisResponseParser
import com.example.gemgemgen.analysis.domain.AnalysisStatus
import com.example.gemgemgen.analysis.domain.AnalysisTargetSource
import com.example.gemgemgen.analysis.domain.AnalysisTxtCountPolicy
import com.example.gemgemgen.analysis.domain.DEFAULT_ANALYSIS_MODEL
import com.example.gemgemgen.analysis.domain.AnalysisPromptPayload
import com.example.gemgemgen.analysis.domain.AnalysisTxtPromptPayload
import com.example.gemgemgen.analysis.ui.AnalysisViewModel
import com.example.gemgemgen.analysis.usecase.AnalysisAiGateway
import com.example.gemgemgen.analysis.usecase.AnalysisWildcardSaveResult
import com.example.gemgemgen.analysis.usecase.AnalyzePromptForCategoryUseCase
import com.example.gemgemgen.analysis.usecase.CopyAnalysisResultsUseCase
import com.example.gemgemgen.analysis.usecase.GenerateAnalysisTxtUseCase
import com.example.gemgemgen.analysis.usecase.GeminiApiKeyRecord
import com.example.gemgemgen.analysis.usecase.GeminiApiKeyRepository
import com.example.gemgemgen.analysis.usecase.ManageGeminiApiKeysUseCase
import com.example.gemgemgen.analysis.usecase.SaveAnalysisWildcardFileUseCase
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisFeatureTest {
    @Test
    fun categories_keepAllTypesFromSourceApp() {
        assertEquals(
            listOf(
                "여성 의상",
                "남성 의상",
                "남성 외모",
                "장소",
                "여성 자세",
                "남성 자세",
                "여성 표정",
                "여성 헤어스타일",
                "와카"
            ),
            AnalysisCategory.entries.map { it.label }
        )
    }

    @Test
    fun parseReport_marksMissingExactTextAsInvalidSegment() {
        val report = AnalysisResponseParser.parseReport(
            jsonText = analysisJson(exactText = "missing fragment"),
            sourcePrompt = "portrait with long hair"
        )

        assertEquals(false, report.targetSegment?.isValid)
        assertEquals(-1, report.targetSegment?.startIndex)
    }

    @Test
    fun countPolicy_clampsToSliderRange() {
        assertEquals(AnalysisTxtCountPolicy.MIN_COUNT, AnalysisTxtCountPolicy.coerce(1))
        assertEquals(50, AnalysisTxtCountPolicy.coerce(50))
        assertEquals(AnalysisTxtCountPolicy.MAX_COUNT, AnalysisTxtCountPolicy.coerce(999))
    }

    @Test
    fun generateTxt_doesNotFillMissingCandidatesWithFallback() = runBlocking {
        val aiGateway = FakeAnalysisAiGateway(
            generateResponse = """[{"text":"후보 하나","explanation":"설명"}]"""
        )
        val result = GenerateAnalysisTxtUseCase(
            aiGateway = aiGateway,
            apiKeyRepository = FakeGeminiApiKeyRepository(activeKey = "secret"),
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        ).generate(
            sourcePrompt = "portrait with long hair",
            category = AnalysisCategory.WOMEN_HAIRSTYLE,
            targetSegment = com.example.gemgemgen.analysis.domain.AnalysisTargetSegment(
                text = "long hair",
                startIndex = 14,
                endIndex = 23,
                source = AnalysisTargetSource.AUTO,
                category = AnalysisCategory.WOMEN_HAIRSTYLE
            ),
            analysisReport = AnalysisResponseParser.parseReport(
                analysisJson(exactText = "long hair"),
                "portrait with long hair"
            ),
            count = 50,
            selectedHints = emptyList()
        )

        assertEquals(listOf("후보 하나"), result.candidates)
        assertTrue(result.warning.contains("적은"))
    }

    @Test
    fun saveWildcardFile_whenDuplicateExistsAsksForOverwrite() = runBlocking {
        val repository = FakeWildcardRepository("옷.txt" to "old")
        val useCase = SaveAnalysisWildcardFileUseCase(
            repository = repository,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        )

        val exists = useCase.save("옷", listOf("new"), overwrite = false)
        assertEquals(AnalysisWildcardSaveResult.FileExists("옷.txt"), exists)
        assertEquals("old", repository.contentOf("옷.txt"))

        val saved = useCase.save("옷", listOf("new"), overwrite = true)
        assertEquals(AnalysisWildcardSaveResult.Success("옷.txt"), saved)
        assertEquals("new", repository.contentOf("옷.txt"))
    }

    @Test
    fun keyManager_deleteActiveKeyLeavesNoActiveKey() = runBlocking {
        val repository = FakeGeminiApiKeyRepository()
        val manager = ManageGeminiApiKeysUseCase(
            repository = repository,
            clock = { 1L },
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        )

        val keys = manager.addKey("main", "secret-key")
        assertTrue(keys.single().isActive)

        val afterDelete = manager.deleteKey(keys.single().id)
        assertTrue(afterDelete.isEmpty())
        assertEquals(null, repository.activeKeyValue())
    }

    @Test
    fun viewModel_manualMaskOverridesAutoMaskForGeneration() {
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """[{"text":"수동 후보","explanation":"설명"}]"""
        )
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        val viewModel = analysisViewModel(aiGateway, keyRepository)
        val prompt = "red hair and blue dress"

        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.sourcePromptTextFieldState.edit {
            selection = TextRange(0, "red hair".length)
        }
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_HAIRSTYLE)
        viewModel.applyManualSelection()
        viewModel.generateTxt()

        assertEquals(AnalysisTargetSource.MANUAL, viewModel.uiState.value.targetSegment?.source)
        assertEquals("red hair", viewModel.uiState.value.targetSegment?.text)
        assertEquals(listOf("수동 후보"), viewModel.uiState.value.generatedCandidates)
        assertEquals(AnalysisStatus.SUCCESS, viewModel.uiState.value.status)
    }

    private fun analysisViewModel(
        aiGateway: AnalysisAiGateway,
        keyRepository: GeminiApiKeyRepository
    ): AnalysisViewModel {
        val dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        return AnalysisViewModel(
            analyzePrompt = AnalyzePromptForCategoryUseCase(
                aiGateway = aiGateway,
                apiKeyRepository = keyRepository,
                dispatchers = dispatchers
            ),
            generateTxtUseCase = GenerateAnalysisTxtUseCase(
                aiGateway = aiGateway,
                apiKeyRepository = keyRepository,
                dispatchers = dispatchers
            ),
            keyManager = ManageGeminiApiKeysUseCase(
                repository = keyRepository,
                dispatchers = dispatchers
            ),
            copyResults = CopyAnalysisResultsUseCase(
                clipboardGateway = FakeClipboard(),
                dispatchers = dispatchers
            ),
            saveWildcardFile = SaveAnalysisWildcardFileUseCase(
                repository = FakeWildcardRepository(),
                dispatchers = dispatchers
            ),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    private fun analysisJson(exactText: String): String {
        return """
            {
              "targetSegment": {
                "exactText": "$exactText",
                "startIndex": 0,
                "endIndex": 1,
                "confidence": 0.9,
                "reason": "테스트"
              },
              "visualContext": {
                "viewpoint": "정면",
                "distance": "중거리",
                "visibleScope": "상반신",
                "cameraAngle": "눈높이",
                "visibleElements": [],
                "hiddenOrUnclearElements": []
              },
              "spatialLayout": {
                "subjectPlacement": "중앙",
                "foreground": [],
                "midground": [],
                "background": [],
                "leftSide": [],
                "center": [],
                "rightSide": [],
                "above": [],
                "below": [],
                "behindSubject": [],
                "besideSubject": [],
                "fixedAnchors": [],
                "mutableZones": []
              },
              "categoryConstraints": {
                "allowed": [],
                "avoid": []
              },
              "warnings": []
            }
        """.trimIndent()
    }

    private class FakeAnalysisAiGateway(
        private val analyzeResponse: String = analysisJsonStatic("long hair"),
        private val generateResponse: String = "[]"
    ) : AnalysisAiGateway {
        override suspend fun analyze(
            apiKey: String,
            modelId: String,
            payload: AnalysisPromptPayload
        ): String {
            assertEquals(DEFAULT_ANALYSIS_MODEL, modelId)
            return analyzeResponse
        }

        override suspend fun generateTxt(
            apiKey: String,
            modelId: String,
            payload: AnalysisTxtPromptPayload
        ): String {
            assertFalse(payload.systemInstruction.contains("fallback", ignoreCase = true))
            return generateResponse
        }
    }

    private class FakeGeminiApiKeyRepository(
        activeKey: String? = null
    ) : GeminiApiKeyRepository {
        private val rawKeys = mutableMapOf<String, String>()
        private val records = mutableListOf<GeminiApiKeyRecord>()

        init {
            if (activeKey != null) {
                rawKeys["initial"] = activeKey
                records += GeminiApiKeyRecord(
                    id = "initial",
                    label = "initial",
                    encryptedValue = "encrypted",
                    preview = "****${activeKey.takeLast(4)}",
                    createdAtMillis = 0L,
                    isActive = true
                )
            }
        }

        override fun listKeys(): List<GeminiApiKeyRecord> = records

        override fun addKey(
            label: String,
            rawKey: String,
            createdAtMillis: Long
        ): GeminiApiKeyRecord {
            val id = "key-${records.size + 1}"
            rawKeys[id] = rawKey
            val record = GeminiApiKeyRecord(
                id = id,
                label = label,
                encryptedValue = "encrypted-$id",
                preview = "****${rawKey.takeLast(4)}",
                createdAtMillis = createdAtMillis,
                isActive = records.none { it.isActive }
            )
            records += record
            return record
        }

        override fun deleteKey(id: String) {
            rawKeys.remove(id)
            records.removeAll { it.id == id }
        }

        override fun activateKey(id: String) {
            records.replaceAll { it.copy(isActive = it.id == id) }
        }

        override fun activeKeyValue(): String? {
            return records.firstOrNull { it.isActive }?.let { rawKeys[it.id] }
        }
    }

    private class FakeWildcardRepository(
        vararg files: Pair<String, String>
    ) : WildcardFileRepository {
        private val contents = linkedMapOf<String, String>().apply { putAll(files) }

        override fun listFiles(): List<WildcardTextFile> {
            return contents.keys.map { WildcardTextFile(id = it, fileName = it) }
        }

        override fun readFile(file: WildcardTextFile): String {
            return contents.getValue(file.fileName)
        }

        override fun createFile(fileName: String): WildcardTextFile {
            contents[fileName] = ""
            return WildcardTextFile(id = fileName, fileName = fileName)
        }

        override fun renameFile(file: WildcardTextFile, newName: String): WildcardTextFile {
            val content = contents.remove(file.fileName).orEmpty()
            contents[newName] = content
            return WildcardTextFile(id = newName, fileName = newName)
        }

        override fun writeFile(file: WildcardTextFile, text: String) {
            contents[file.fileName] = text
        }

        override fun deleteFile(file: WildcardTextFile) {
            contents.remove(file.fileName)
        }

        fun contentOf(fileName: String): String = contents.getValue(fileName)
    }

    private class FakeClipboard : ClipboardGateway {
        override fun readText(): String = ""
        override fun writeText(text: String) = Unit
    }

    private companion object {
        fun analysisJsonStatic(exactText: String): String {
            return """
                {
                  "targetSegment": {
                    "exactText": "$exactText",
                    "startIndex": 0,
                    "endIndex": 1,
                    "confidence": 0.9,
                    "reason": "테스트"
                  },
                  "visualContext": {
                    "viewpoint": "정면",
                    "distance": "중거리",
                    "visibleScope": "상반신",
                    "cameraAngle": "눈높이",
                    "visibleElements": [],
                    "hiddenOrUnclearElements": []
                  },
                  "spatialLayout": {},
                  "categoryConstraints": {
                    "allowed": [],
                    "avoid": []
                  },
                  "warnings": []
                }
            """.trimIndent()
        }
    }
}
