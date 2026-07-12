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
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.usecase.AnalysisCredentialResolver
import com.example.gemgemgen.analysis.usecase.AnalysisSaveAndReplaceResult
import com.example.gemgemgen.analysis.usecase.AnalysisWildcardSaveResult
import com.example.gemgemgen.analysis.usecase.AnalyzePromptForCategoryUseCase
import com.example.gemgemgen.analysis.usecase.CopyAnalysisResultsUseCase
import com.example.gemgemgen.analysis.usecase.GenerateAnalysisTxtUseCase
import com.example.gemgemgen.analysis.usecase.GeminiApiKeyRecord
import com.example.gemgemgen.analysis.usecase.GeminiApiKeyRepository
import com.example.gemgemgen.analysis.usecase.GrokAuthGateway
import com.example.gemgemgen.analysis.usecase.GrokAuthRepository
import com.example.gemgemgen.analysis.usecase.GrokAuthSession
import com.example.gemgemgen.analysis.usecase.GrokDeviceLoginChallenge
import com.example.gemgemgen.analysis.usecase.ManageGeminiApiKeysUseCase
import com.example.gemgemgen.analysis.usecase.ManageGrokAuthUseCase
import com.example.gemgemgen.analysis.usecase.ResolveAnalysisTargetUseCase
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
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        val result = GenerateAnalysisTxtUseCase(
            aiGateway = aiGateway,
            credentialResolver = AnalysisCredentialResolver(
                apiKeyRepository = keyRepository,
                grokAuth = ManageGrokAuthUseCase(
                    gateway = FakeGrokAuthGateway(),
                    repository = FakeGrokAuthRepository(),
                    dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
                ),
                dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
            ),
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
        val clipboard = RecordingClipboard()
        val dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        val useCase = SaveAnalysisWildcardFileUseCase(
            repository = repository,
            copyResults = CopyAnalysisResultsUseCase(clipboard, dispatchers),
            dispatchers = dispatchers
        )

        val exists = useCase.save("옷", listOf("new"), overwrite = false)
        assertEquals(AnalysisWildcardSaveResult.FileExists("옷.txt"), exists)
        assertEquals("old", repository.contentOf("옷.txt"))

        val saved = useCase.save("옷", listOf("new"), overwrite = true)
        assertEquals(AnalysisWildcardSaveResult.Success("옷.txt"), saved)
        assertEquals("new", repository.contentOf("옷.txt"))
    }

    @Test
    fun saveAndPrepareReplacedSource_writesFileReplacesSpanAndCopies() = runBlocking {
        val repository = FakeWildcardRepository()
        val clipboard = RecordingClipboard()
        val dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        val useCase = SaveAnalysisWildcardFileUseCase(
            repository = repository,
            copyResults = CopyAnalysisResultsUseCase(clipboard, dispatchers),
            dispatchers = dispatchers
        )
        val source = "red hair and blue dress"
        val segment = AnalysisTargetSegment(
            text = "red hair",
            startIndex = 0,
            endIndex = 8,
            source = AnalysisTargetSource.MANUAL,
            category = AnalysisCategory.WOMEN_HAIRSTYLE
        )

        val result = useCase.saveAndPrepareReplacedSource(
            fileNameInput = "hair",
            candidates = listOf("black hair", "blonde hair"),
            overwrite = false,
            sourcePrompt = source,
            targetSegment = segment
        )

        val success = result as AnalysisSaveAndReplaceResult.Success
        assertEquals("hair.txt", success.fileName)
        assertEquals("__hair__ and blue dress", success.replacedSource)
        assertTrue(success.clipboardCopied)
        assertEquals("black hair\nblonde hair", repository.contentOf("hair.txt"))
        assertEquals("__hair__ and blue dress", clipboard.writtenText)
    }

    @Test
    fun resolveTarget_reusesCacheUntilCategoryChanges() = runBlocking {
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """[{"text":"후보","explanation":"설명"}]"""
        )
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        val dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        val resolve = ResolveAnalysisTargetUseCase(
            AnalyzePromptForCategoryUseCase(
                aiGateway = aiGateway,
                credentialResolver = AnalysisCredentialResolver(
                    apiKeyRepository = keyRepository,
                    grokAuth = ManageGrokAuthUseCase(
                        gateway = FakeGrokAuthGateway(),
                        repository = FakeGrokAuthRepository(),
                        dispatchers = dispatchers
                    ),
                    dispatchers = dispatchers
                ),
                dispatchers = dispatchers
            )
        )
        val source = "red hair and blue dress"
        val category = AnalysisCategory.WOMEN_CLOTHING

        val masked = resolve.analyzeAndMask(source, category)
        assertEquals(1, aiGateway.analyzeCallCount)
        assertEquals(listOf(DEFAULT_ANALYSIS_MODEL), aiGateway.analyzeModelIds)

        val first = resolve.ensureForGeneration(
            source = source,
            category = category,
            existingTarget = masked.targetSegment,
            cache = masked.cache
        )
        assertEquals(1, aiGateway.analyzeCallCount)
        assertEquals(masked.targetSegment, first.target)
        assertFalse(first.didAnalyze)

        val afterCategoryChange = resolve.ensureForGeneration(
            source = source,
            category = AnalysisCategory.WOMEN_HAIRSTYLE,
            existingTarget = null,
            cache = first.cache
        )
        assertEquals(2, aiGateway.analyzeCallCount)
        assertTrue(afterCategoryChange.didAnalyze)
        assertEquals(AnalysisTargetSource.AUTO, afterCategoryChange.target.source)
        // 재분석도 마스킹 역할 모델(테스트 기본값)을 사용
        assertEquals(
            listOf(DEFAULT_ANALYSIS_MODEL, DEFAULT_ANALYSIS_MODEL),
            aiGateway.analyzeModelIds
        )
    }

    @Test
    fun ensureForGeneration_usesMaskingModelNotGenerationModel() = runBlocking {
        val maskingModel = "gemini-3.1-flash-lite"
        val generationModel = "gemini-3.5-flash"
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """[{"text":"후보","explanation":"설명"}]"""
        )
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret").apply {
            setRoleModel("masking", maskingModel)
            setRoleModel("generation", generationModel)
        }
        val dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        val credentialResolver = AnalysisCredentialResolver(
            apiKeyRepository = keyRepository,
            grokAuth = ManageGrokAuthUseCase(
                gateway = FakeGrokAuthGateway(),
                repository = FakeGrokAuthRepository(),
                dispatchers = dispatchers
            ),
            dispatchers = dispatchers
        )
        val resolve = ResolveAnalysisTargetUseCase(
            AnalyzePromptForCategoryUseCase(
                aiGateway = aiGateway,
                credentialResolver = credentialResolver,
                dispatchers = dispatchers
            )
        )
        val source = "red hair and blue dress"

        val ensured = resolve.ensureForGeneration(
            source = source,
            category = AnalysisCategory.WOMEN_CLOTHING,
            existingTarget = null,
            cache = null
        )
        assertTrue(ensured.didAnalyze)
        assertEquals(listOf(maskingModel), aiGateway.analyzeModelIds)

        GenerateAnalysisTxtUseCase(
            aiGateway = aiGateway,
            credentialResolver = credentialResolver,
            dispatchers = dispatchers
        ).generate(
            sourcePrompt = source,
            category = AnalysisCategory.WOMEN_CLOTHING,
            targetSegment = ensured.target,
            analysisReport = ensured.report,
            count = 1,
            selectedHints = emptyList()
        )
        assertEquals(listOf(generationModel), aiGateway.generateModelIds)
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

    @Test
    fun category_defaultWildcardSaveFileName_removesWhitespace() {
        assertEquals("여성의상.txt", AnalysisCategory.WOMEN_CLOTHING.defaultWildcardSaveFileName())
        assertEquals("장소.txt", AnalysisCategory.LOCATION.defaultWildcardSaveFileName())
        assertEquals(
            "여성헤어스타일.txt",
            AnalysisCategory.WOMEN_HAIRSTYLE.defaultWildcardSaveFileName()
        )
    }

    @Test
    fun viewModel_generateTxt_setsResultFileNameFromCategoryWithoutSpaces() {
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """[{"text":"후보","explanation":"설명"}]"""
        )
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        val viewModel = analysisViewModel(aiGateway, keyRepository)
        val prompt = "red hair and blue dress"

        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)
        viewModel.onResultFileNameChange("custom-name.txt")
        viewModel.generateTxt()

        assertEquals("여성의상.txt", viewModel.uiState.value.resultFileName)

        viewModel.onCategorySelected(AnalysisCategory.WOMEN_HAIRSTYLE)
        viewModel.generateTxt()

        assertEquals("여성헤어스타일.txt", viewModel.uiState.value.resultFileName)
    }

    @Test
    fun viewModel_cachingAndInvalidation() {
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """[{"text":"후보","explanation":"설명"}]"""
        )
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        val viewModel = analysisViewModel(aiGateway, keyRepository)
        val prompt = "red hair and blue dress"

        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)
        
        // 1. 자동 마스킹 실행 (최초 1차 분석 API 호출)
        viewModel.analyzeAndMask()
        assertEquals(1, aiGateway.analyzeCallCount)

        // 2. TXT 생성 실행 (캐시 재사용되어 1 유지)
        viewModel.generateTxt()
        assertEquals(1, aiGateway.analyzeCallCount)

        // 3. 한 번 더 TXT 생성 실행 (캐시 재사용되어 1 유지)
        viewModel.generateTxt()
        assertEquals(1, aiGateway.analyzeCallCount)

        // 4. 카테고리 변경 -> 캐시 무효화 -> 1차 분석 다시 수행 (호출 횟수 2)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_HAIRSTYLE)
        viewModel.generateTxt()
        assertEquals(2, aiGateway.analyzeCallCount)

        // 5. 수동 마스킹 지정 -> 캐시 무효화 -> 1차 분석 다시 수행 (호출 횟수 3)
        viewModel.sourcePromptTextFieldState.edit {
            selection = TextRange(0, "red hair".length)
        }
        viewModel.applyManualSelection()
        viewModel.generateTxt()
        assertEquals(3, aiGateway.analyzeCallCount)

        // 6. 동일한 수동 마스킹에서 다시 TXT 생성 실행 (캐시 재사용되어 3 유지)
        viewModel.generateTxt()
        assertEquals(3, aiGateway.analyzeCallCount)
    }

    @Test
    fun viewModel_trimForInactiveTab_clearsCandidatesAndKeepsSource() {
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """[{"text":"후보","explanation":"설명"}]"""
        )
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        val viewModel = analysisViewModel(aiGateway, keyRepository)
        val prompt = "red hair and blue dress"

        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)
        viewModel.generateTxt()
        assertEquals(listOf("후보"), viewModel.uiState.value.generatedCandidates)

        viewModel.trimForInactiveTab()

        assertEquals(emptyList<String>(), viewModel.uiState.value.generatedCandidates)
        assertEquals(null, viewModel.uiState.value.targetSegment)
        assertEquals(AnalysisStatus.IDLE, viewModel.uiState.value.status)
        assertEquals(prompt, viewModel.sourcePromptTextFieldState.text.toString())
        assertEquals(prompt, viewModel.uiState.value.sourcePrompt)
    }

    @Test
    fun importSourcePromptFromAutomation_replacesWholeSourcePrompt() {
        val viewModel = analysisViewModel(
            aiGateway = FakeAnalysisAiGateway(),
            keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        )
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd("old source")
        viewModel.onSourcePromptChange("old source")

        viewModel.importSourcePromptFromAutomation("automation source")

        assertEquals("automation source", viewModel.sourcePromptTextFieldState.text.toString())
        assertEquals("automation source", viewModel.uiState.value.sourcePrompt)
    }

    @Test
    fun importSourcePromptFromAutomation_clearsInvalidTargetSegment() {
        val viewModel = analysisViewModel(
            aiGateway = FakeAnalysisAiGateway(),
            keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        )
        val original = "red dress blue sky"
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(original)
        viewModel.onSourcePromptChange(original)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)
        // 원문에 있던 구간을 수동으로 지정한 뒤 가져오기로 원문을 바꾸면 구간이 비워져야 한다.
        viewModel.sourcePromptTextFieldState.edit {
            selection = TextRange(0, "red dress".length)
        }
        viewModel.applyManualSelection()
        assertTrue(viewModel.uiState.value.targetSegment != null)

        viewModel.importSourcePromptFromAutomation("completely different text")

        assertEquals("completely different text", viewModel.uiState.value.sourcePrompt)
        assertEquals(null, viewModel.uiState.value.targetSegment)
    }

    @Test
    fun importSourcePromptFromAutomation_keepsSourceWhenAutomationEmpty() {
        val viewModel = analysisViewModel(
            aiGateway = FakeAnalysisAiGateway(),
            keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        )
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd("keep me")
        viewModel.onSourcePromptChange("keep me")

        viewModel.importSourcePromptFromAutomation("   ")

        assertEquals("keep me", viewModel.sourcePromptTextFieldState.text.toString())
        assertEquals("keep me", viewModel.uiState.value.sourcePrompt)
        assertEquals("자동화에 입력된 텍스트가 없습니다.", viewModel.uiState.value.error)
    }

    @Test
    fun saveGeneratedResults_invokesOnSuccessWithReplacedSourceAndKeepsClipboard() {
        val clipboard = RecordingClipboard()
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """[{"text":"후보","explanation":"설명"}]"""
        )
        val viewModel = analysisViewModel(
            aiGateway = aiGateway,
            keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret"),
            clipboardGateway = clipboard
        )
        val prompt = "red hair and blue dress"
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.sourcePromptTextFieldState.edit {
            selection = TextRange(0, "red hair".length)
        }
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_HAIRSTYLE)
        viewModel.applyManualSelection()
        viewModel.generateTxt()
        viewModel.onResultFileNameChange("hair")

        var handedOff: String? = null
        viewModel.saveGeneratedResults { handedOff = it }

        assertEquals("__hair__ and blue dress", handedOff)
        assertEquals("__hair__ and blue dress", clipboard.writtenText)
        assertTrue(viewModel.uiState.value.message.contains("자동화 프롬프트"))
    }

    private fun analysisViewModel(
        aiGateway: AnalysisAiGateway,
        keyRepository: GeminiApiKeyRepository,
        clipboardGateway: ClipboardGateway = FakeClipboard()
    ): AnalysisViewModel {
        val dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        val grokAuth = ManageGrokAuthUseCase(
            gateway = FakeGrokAuthGateway(),
            repository = FakeGrokAuthRepository(),
            dispatchers = dispatchers
        )
        val credentialResolver = AnalysisCredentialResolver(
            apiKeyRepository = keyRepository,
            grokAuth = grokAuth,
            dispatchers = dispatchers
        )
        val analyzePrompt = AnalyzePromptForCategoryUseCase(
            aiGateway = aiGateway,
            credentialResolver = credentialResolver,
            dispatchers = dispatchers
        )
        val copyResults = CopyAnalysisResultsUseCase(
            clipboardGateway = clipboardGateway,
            dispatchers = dispatchers
        )
        return AnalysisViewModel(
            resolveTarget = ResolveAnalysisTargetUseCase(analyzePrompt),
            generateTxtUseCase = GenerateAnalysisTxtUseCase(
                aiGateway = aiGateway,
                credentialResolver = credentialResolver,
                dispatchers = dispatchers
            ),
            keyManager = ManageGeminiApiKeysUseCase(
                repository = keyRepository,
                dispatchers = dispatchers
            ),
            grokAuth = grokAuth,
            copyResults = copyResults,
            saveWildcardFile = SaveAnalysisWildcardFileUseCase(
                repository = FakeWildcardRepository(),
                copyResults = copyResults,
                dispatchers = dispatchers
            ),
            dispatchers = dispatchers,
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
        var analyzeCallCount = 0
        val analyzeModelIds = mutableListOf<String>()
        val generateModelIds = mutableListOf<String>()

        override suspend fun analyze(
            apiKey: String,
            modelId: String,
            payload: AnalysisPromptPayload
        ): String {
            analyzeCallCount++
            analyzeModelIds += modelId
            return analyzeResponse
        }

        override suspend fun generateTxt(
            apiKey: String,
            modelId: String,
            payload: AnalysisTxtPromptPayload
        ): String {
            generateModelIds += modelId
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

        override fun updateKeyLabel(id: String, newLabel: String) {
            records.replaceAll { if (it.id == id) it.copy(label = newLabel) else it }
        }

        // 단위 테스트는 Gemini 키만 두는 경우가 많아 기본은 둘 다 Gemini.
        private val roleProviders = mutableMapOf(
            "masking" to "gemini",
            "generation" to "gemini"
        )
        private val roleModels = mutableMapOf(
            "masking" to DEFAULT_ANALYSIS_MODEL,
            "generation" to DEFAULT_ANALYSIS_MODEL
        )

        override fun getRoleProvider(role: String): String =
            roleProviders[role] ?: "gemini"

        override fun setRoleProvider(role: String, providerId: String) {
            roleProviders[role] = providerId
            val model = roleModels[role]
            if (model == null ||
                (providerId == "grok" && !model.startsWith("grok-")) ||
                (providerId == "gemini" && !model.startsWith("gemini-"))
            ) {
                roleModels[role] = if (providerId == "grok") "grok-4.5" else DEFAULT_ANALYSIS_MODEL
            }
        }

        override fun getRoleModel(role: String): String =
            roleModels[role] ?: DEFAULT_ANALYSIS_MODEL

        override fun setRoleModel(role: String, modelId: String) {
            roleModels[role] = modelId
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

    private class FakeGrokAuthGateway : GrokAuthGateway {
        override suspend fun startDeviceLogin(): GrokDeviceLoginChallenge {
            return GrokDeviceLoginChallenge(
                deviceCode = "device",
                userCode = "USER-CODE",
                verificationUri = "https://auth.x.ai/device",
                verificationUriComplete = "https://auth.x.ai/device?user_code=USER-CODE",
                expiresInSeconds = 900,
                intervalSeconds = 5,
                tokenEndpoint = "https://auth.x.ai/oauth2/token"
            )
        }

        override suspend fun pollDeviceLogin(challenge: GrokDeviceLoginChallenge): GrokAuthSession? {
            return null
        }

        override suspend fun refreshSession(session: GrokAuthSession): GrokAuthSession {
            return session
        }
    }

    private class FakeGrokAuthRepository(
        private var session: GrokAuthSession? = null
    ) : GrokAuthRepository {
        override fun loadSession(): GrokAuthSession? = session

        override fun saveSession(session: GrokAuthSession) {
            this.session = session
        }

        override fun clearSession() {
            session = null
        }
    }

    private class FakeClipboard : ClipboardGateway {
        override fun readText(): String = ""
        override fun writeText(text: String) = Unit
    }

    private class RecordingClipboard : ClipboardGateway {
        var writtenText: String = ""
            private set

        override fun readText(): String = writtenText
        override fun writeText(text: String) {
            writtenText = text
        }
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
