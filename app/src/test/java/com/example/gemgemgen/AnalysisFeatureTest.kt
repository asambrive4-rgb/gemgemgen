// 역할: AI 프롬프트 분석 기능 전반의 동작 흐름을 검증합니다.
package com.example.gemgemgen

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisGenerationCountPolicy
import com.example.gemgemgen.analysis.domain.AnalysisResponseParser
import com.example.gemgemgen.analysis.domain.AnalysisResultPresentation
import com.example.gemgemgen.analysis.domain.AnalysisStatus
import com.example.gemgemgen.analysis.domain.AnalysisTargetSource
import com.example.gemgemgen.analysis.domain.AnalysisTxtCountPolicy
import com.example.gemgemgen.analysis.domain.DEFAULT_ANALYSIS_MODEL
import com.example.gemgemgen.analysis.domain.AnalysisPromptPayload
import com.example.gemgemgen.analysis.domain.AnalysisTxtPromptPayload
import com.example.gemgemgen.analysis.ui.AnalysisViewModel
import com.example.gemgemgen.analysis.ui.DEFAULT_ANALYSIS_RESULT_FILE_NAME
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisFeatureTest {
    @Test
    fun freeEdit_scatteredEdits_applySwitchAndRestoreWithoutRewritingGaps() {
        val source = "head|waist-up|KEEP|hide feet|tail"
        val analysis = analysisJson("waist-up").trimEnd().dropLast(1) + """,
            "cascadingTrace":{"conflictingSegments":[{
                "startIndex":999,"endIndex":1000,"exactText":"hide feet"
            }]}
        }
        """
        val edits = listOf("full body", "long shot").joinToString(prefix = "[", postfix = "]") { frame ->
            """{"edits":[
              {"exactText":"waist-up","replacement":"$frame"},
              {"exactText":"hide feet","replacement":"show feet"}
            ],"explanation":""}"""
        }
        val gateway = FakeAnalysisAiGateway(analyzeResponse = analysis, generateResponse = edits)
        val viewModel = analysisViewModel(gateway, FakeGeminiApiKeyRepository(activeKey = "test-credential"))
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(source)
        viewModel.onSourcePromptChange(source)
        viewModel.generate()
        assertEquals(AnalysisStatus.SUCCESS, viewModel.uiState.value.status)
        assertEquals(listOf("full body|KEEP|show feet", "long shot|KEEP|show feet"),
            viewModel.uiState.value.generatedCandidates)
        assertEquals("waist-up|KEEP|hide feet", viewModel.uiState.value.targetSegment?.text)
        var automation = source
        val apply = { expected: String, replacement: String, _: Int ->
            val start = automation.indexOf(expected)
            if (start < 0) null else {
                automation = automation.replaceRange(start, start + expected.length, replacement)
                start
            }
        }
        viewModel.applyCandidate(0, apply)
        assertEquals("head|full body|KEEP|show feet|tail", automation)
        viewModel.applyCandidate(1, apply)
        assertEquals("head|long shot|KEEP|show feet|tail", automation)
        viewModel.restoreOriginalPrompt(apply)
        assertEquals(source, automation)
        assertEquals(source, viewModel.sourcePromptTextFieldState.text.toString())
    }

    @Test
    fun essentialClarification_stopsBeforeGenerationAndPromptsForCustomHint() {
        val analysis = analysisJson("blue dress").trimEnd().dropLast(1) +
            """, "clarificationQuestion":"의상 길이를 어떻게 바꿀까요?" }"""
        val gateway = FakeAnalysisAiGateway(analyzeResponse = analysis)
        val viewModel = analysisViewModel(gateway, FakeGeminiApiKeyRepository(activeKey = "test-credential"))
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd("blue dress")
        viewModel.onSourcePromptChange("blue dress")
        viewModel.generate()
        assertEquals(0, gateway.generateCallCount)
        assertEquals(AnalysisStatus.ERROR, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.error.contains("추가 요구사항"))
        assertTrue(viewModel.uiState.value.error.contains("의상 길이"))
    }

    @Test
    fun multilineWildcardCandidate_isRejectedBeforeCreatingFile() = runBlocking {
        val repository = FakeWildcardRepository()
        val dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        val save = SaveAnalysisWildcardFileUseCase(repository,
            CopyAnalysisResultsUseCase(FakeClipboard(), dispatchers), dispatchers)
        val result = runCatching { save.save("test.txt", listOf("first\nsecond"), false) }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(repository.listFiles().isEmpty())
    }

    @Test
    fun categories_keepAllTypesFromSourceApp() {
        assertEquals(
            listOf(
                "분석 수정",
                "구도",
                "카메라 질감",
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
    fun newCategories_haveValidRulesAndFileNames() {
        assertEquals("분석수정.txt", AnalysisCategory.FREE_EDIT.defaultWildcardSaveFileName())
        assertEquals("구도.txt", AnalysisCategory.COMPOSITION.defaultWildcardSaveFileName())
        assertEquals("카메라질감.txt", AnalysisCategory.CAMERA_TEXTURE.defaultWildcardSaveFileName())

        val freeRule = com.example.gemgemgen.analysis.domain.AnalysisCategoryRules.ruleFor(AnalysisCategory.FREE_EDIT)
        assertTrue(freeRule.goal.contains("연쇄 보완"))
        assertTrue(freeRule.goal.contains("정밀 분석"))
        assertTrue(freeRule.required.contains("관찰 가능한 시각적/물리적 조건"))

        val compRule = com.example.gemgemgen.analysis.domain.AnalysisCategoryRules.ruleFor(AnalysisCategory.COMPOSITION)
        assertTrue(compRule.goal.contains("구도"))

        val camRule = com.example.gemgemgen.analysis.domain.AnalysisCategoryRules.ruleFor(AnalysisCategory.CAMERA_TEXTURE)
        assertTrue(camRule.goal.contains("질감"))
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
    fun parseReport_readsVariationGoalFromModel() {
        val report = AnalysisResponseParser.parseReport(
            jsonText = analysisJson(
                exactText = "blue dress",
                variationGoal = "디테일과 분량을 늘린 여성 의상 조각 생성"
            ),
            sourcePrompt = "blue dress"
        )
        assertEquals("디테일과 분량을 늘린 여성 의상 조각 생성", report.variationGoal)
    }

    @Test
    fun buildAnalysisPrompt_doesNotHardcodeCategoryGoal_andIncludesChips() {
        val payload = com.example.gemgemgen.analysis.domain.AnalysisPromptBuilder.buildAnalysisPrompt(
            sourcePrompt = "흰색 린넨 셔츠",
            category = AnalysisCategory.WOMEN_CLOTHING,
            selectedHints = listOf("Keep the same concept and write longer"),
            customHint = "레이스 디테일 추가"
        )
        assertFalse(payload.systemInstruction.contains("- Goal: 짧고 실용적인"))
        assertTrue(payload.systemInstruction.contains("Infer variationGoal"))
        assertTrue(payload.userPrompt.contains("Keep the same concept and write longer"))
        assertTrue(payload.userPrompt.contains("레이스 디테일 추가"))
        assertTrue(payload.userPrompt.contains("Selected direction chips:"))
    }

    @Test
    fun buildTxtPrompt_includesVariationGoalFromReport() {
        val report = AnalysisResponseParser.parseReport(
            jsonText = analysisJson(
                exactText = "blue dress",
                variationGoal = "같은 계열 안에서 다른 종류로 바꾼 짧은 의상 조각"
            ),
            sourcePrompt = "blue dress"
        )
        val payload = com.example.gemgemgen.analysis.domain.AnalysisPromptBuilder.buildTxtPrompt(
            sourcePrompt = "blue dress",
            category = AnalysisCategory.WOMEN_CLOTHING,
            targetSegment = AnalysisTargetSegment(
                text = "blue dress",
                startIndex = 0,
                endIndex = 10,
                source = AnalysisTargetSource.AUTO,
                category = AnalysisCategory.WOMEN_CLOTHING
            ),
            analysisReport = report,
            count = 10,
            selectedHints = emptyList()
        )
        assertTrue(payload.systemInstruction.contains("같은 계열 안에서 다른 종류로 바꾼 짧은 의상 조각"))
        assertTrue(payload.systemInstruction.contains("Variation goal"))
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
    fun generate_withCardCount_requestsExactlySixCandidates() = runBlocking {
        val aiGateway = FakeAnalysisAiGateway(
            generateResponse = """
                [
                  {"text":"a","explanation":""},
                  {"text":"b","explanation":""},
                  {"text":"c","explanation":""},
                  {"text":"d","explanation":""},
                  {"text":"e","explanation":""},
                  {"text":"f","explanation":""},
                  {"text":"g","explanation":""}
                ]
            """.trimIndent()
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
            targetSegment = AnalysisTargetSegment(
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
            count = AnalysisGenerationCountPolicy.FIXED_COUNT,
            selectedHints = emptyList()
        )

        assertEquals(AnalysisGenerationCountPolicy.FIXED_COUNT, aiGateway.lastGenerateCount)
        assertEquals(
            listOf("a", "b", "c", "d", "e", "f"),
            result.candidates
        )
        assertEquals("", result.warning)
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

        // 칩이 바뀌면 variationGoal 기준이 달라지므로 재분석
        val afterHintChange = resolve.ensureForGeneration(
            source = source,
            category = category,
            existingTarget = masked.targetSegment,
            cache = first.cache,
            selectedHints = listOf("expand length and richness")
        )
        assertEquals(2, aiGateway.analyzeCallCount)
        assertTrue(afterHintChange.didAnalyze)

        val afterCategoryChange = resolve.ensureForGeneration(
            source = source,
            category = AnalysisCategory.WOMEN_HAIRSTYLE,
            existingTarget = null,
            cache = afterHintChange.cache,
            selectedHints = listOf("expand length and richness")
        )
        assertEquals(3, aiGateway.analyzeCallCount)
        assertTrue(afterCategoryChange.didAnalyze)
        assertEquals(AnalysisTargetSource.AUTO, afterCategoryChange.target.source)
        // 재분석도 마스킹 역할 모델(테스트 기본값)을 사용
        assertEquals(
            listOf(DEFAULT_ANALYSIS_MODEL, DEFAULT_ANALYSIS_MODEL, DEFAULT_ANALYSIS_MODEL),
            aiGateway.analyzeModelIds
        )
    }

    @Test
    fun ensureForGeneration_usesMaskingModelNotGenerationModel() = runBlocking {
        val maskingModel = "gemini-3.5-flash-lite"
        val generationModel = "gemini-3.7-flash"
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
    fun viewModel_generateTxt_runsAutoMaskThenGeneration() {
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """[{"text":"자동 후보","explanation":"설명"}]"""
        )
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        val viewModel = analysisViewModel(aiGateway, keyRepository)
        val prompt = "red hair and blue dress"

        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)
        viewModel.generateTxt()

        assertEquals(1, aiGateway.analyzeCallCount)
        assertEquals(AnalysisTargetSource.AUTO, viewModel.uiState.value.targetSegment?.source)
        assertEquals("blue dress", viewModel.uiState.value.targetSegment?.text)
        assertEquals(listOf("자동 후보"), viewModel.uiState.value.generatedCandidates)
        assertEquals(AnalysisResultPresentation.TXT, viewModel.uiState.value.resultPresentation)
        assertEquals(AnalysisStatus.SUCCESS, viewModel.uiState.value.status)
    }

    @Test
    fun viewModel_generate_usesCardPresentationAndFixedCount() {
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """
                [
                  {"text":"후보1","explanation":""},
                  {"text":"후보2","explanation":""},
                  {"text":"후보3","explanation":""},
                  {"text":"후보4","explanation":""},
                  {"text":"후보5","explanation":""},
                  {"text":"후보6","explanation":""}
                ]
            """.trimIndent()
        )
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        val viewModel = analysisViewModel(aiGateway, keyRepository)
        val prompt = "red hair and blue dress"

        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)
        viewModel.onTxtCountChange(50)
        viewModel.generate()

        assertEquals(AnalysisGenerationCountPolicy.FIXED_COUNT, aiGateway.lastGenerateCount)
        assertEquals(AnalysisResultPresentation.CARDS, viewModel.uiState.value.resultPresentation)
        assertEquals(6, viewModel.uiState.value.generatedCandidates.size)
        assertFalse(viewModel.uiState.value.canCopyOrSave)
    }

    @Test
    fun viewModel_applyCandidate_copiesAndReplacesAutomationWithoutChangingSource() {
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """
                [
                  {"text":"검은 원피스","explanation":""},
                  {"text":"흰 셔츠","explanation":""}
                ]
            """.trimIndent()
        )
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        val clipboard = RecordingClipboard()
        val viewModel = analysisViewModel(aiGateway, keyRepository, clipboard)
        val prompt = "red hair and blue dress"

        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)
        viewModel.generate()

        var automationPrompt = "quality, $prompt"
        viewModel.applyCandidate(0) { expectedSegment, replacement, _ ->
            val start = automationPrompt.indexOf(expectedSegment)
            if (start < 0) {
                null
            } else {
                automationPrompt = automationPrompt.replaceRange(
                    start,
                    start + expectedSegment.length,
                    replacement
                )
                start
            }
        }

        assertEquals("검은 원피스", clipboard.writtenText)
        assertEquals(prompt, viewModel.sourcePromptTextFieldState.text.toString())
        assertEquals(prompt, viewModel.uiState.value.sourcePrompt)
        assertEquals("quality, red hair and 검은 원피스", automationPrompt)
        assertEquals(0, viewModel.uiState.value.selectedCandidateIndex)
        assertEquals("blue dress", viewModel.uiState.value.targetSegment?.text)
        assertTrue(viewModel.uiState.value.hasAppliedCandidateToAutomation)
        assertEquals(2, viewModel.uiState.value.generatedCandidates.size)
        assertEquals(AnalysisResultPresentation.CARDS, viewModel.uiState.value.resultPresentation)
    }

    @Test
    fun viewModel_applyCandidate_replacesPreviousCandidateAndRestoresOriginal() {
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """
                [
                  {"text":"검은 원피스","explanation":""},
                  {"text":"흰 셔츠","explanation":""}
                ]
            """.trimIndent()
        )
        val clipboard = RecordingClipboard()
        val viewModel = analysisViewModel(
            aiGateway = aiGateway,
            keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret"),
            clipboardGateway = clipboard
        )
        val original = "red hair and blue dress"
        var automationPrompt = original
        val replaceSegment = { expected: String, replacement: String, _: Int ->
            val start = automationPrompt.indexOf(expected)
            if (start < 0) {
                null
            } else {
                automationPrompt = automationPrompt.replaceRange(
                    start,
                    start + expected.length,
                    replacement
                )
                start
            }
        }

        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(original)
        viewModel.onSourcePromptChange(original)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)
        viewModel.generate()

        viewModel.applyCandidate(0, replaceSegment)
        automationPrompt = "quality, $automationPrompt"
        viewModel.applyCandidate(1, replaceSegment)

        assertEquals("quality, red hair and 흰 셔츠", automationPrompt)
        assertEquals(original, viewModel.sourcePromptTextFieldState.text.toString())
        assertEquals("흰 셔츠", clipboard.writtenText)
        assertEquals(1, viewModel.uiState.value.selectedCandidateIndex)

        viewModel.restoreOriginalPrompt(replaceSegment)

        assertEquals("quality, $original", automationPrompt)
        assertEquals(original, viewModel.sourcePromptTextFieldState.text.toString())
        assertEquals(null, viewModel.uiState.value.selectedCandidateIndex)
        assertFalse(viewModel.uiState.value.hasAppliedCandidateToAutomation)
        assertEquals("흰 셔츠", clipboard.writtenText)
    }

    @Test
    fun viewModel_applyCandidate_doesNotOverwriteUnexpectedAutomationPrompt() {
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """[{"text":"검은 원피스","explanation":""}]"""
        )
        val viewModel = analysisViewModel(
            aiGateway = aiGateway,
            keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        )
        val original = "red hair and blue dress"

        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(original)
        viewModel.onSourcePromptChange(original)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)
        viewModel.generate()
        viewModel.applyCandidate(0) { _, _, _ -> null }

        assertEquals(original, viewModel.sourcePromptTextFieldState.text.toString())
        assertEquals(null, viewModel.uiState.value.selectedCandidateIndex)
        assertFalse(viewModel.uiState.value.hasAppliedCandidateToAutomation)
        assertTrue(viewModel.uiState.value.error.contains("교체할 구간을 찾지 못했습니다"))
    }

    @Test
    fun viewModel_copyCandidate_copiesOnlyCandidateWithoutApplyingIt() {
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """[{"text":"검은 원피스","explanation":""}]"""
        )
        val clipboard = RecordingClipboard()
        val viewModel = analysisViewModel(
            aiGateway = aiGateway,
            keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret"),
            clipboardGateway = clipboard
        )
        val original = "red hair and blue dress"

        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(original)
        viewModel.onSourcePromptChange(original)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)
        viewModel.generate()

        viewModel.copyCandidate(0)

        assertEquals("검은 원피스", clipboard.writtenText)
        assertEquals(original, viewModel.sourcePromptTextFieldState.text.toString())
        assertEquals(null, viewModel.uiState.value.selectedCandidateIndex)
        assertFalse(viewModel.uiState.value.hasAppliedCandidateToAutomation)
        assertEquals("1번 후보를 복사했습니다.", viewModel.uiState.value.message)
    }

    @Test
    fun viewModel_resetSession_requiresConfirmationAndClearsOnlyAnalysisSession() {
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """[{"text":"검은 원피스","explanation":""}]"""
        )
        val viewModel = analysisViewModel(
            aiGateway = aiGateway,
            keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        )
        val original = "red hair and blue dress"
        var automationPrompt = original
        val directionId = viewModel.uiState.value.directions.first().id

        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(original)
        viewModel.onSourcePromptChange(original)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)
        viewModel.onTxtCountChange(37)
        viewModel.toggleDirection(directionId)
        viewModel.onCustomHintChange("새로운 분위기")
        viewModel.onResultFileNameChange("custom.txt")
        viewModel.generate()
        viewModel.applyCandidate(0) { expected, replacement, _ ->
            val start = automationPrompt.indexOf(expected)
            automationPrompt = automationPrompt.replaceRange(
                start,
                start + expected.length,
                replacement
            )
            start
        }

        val apiKeysBeforeReset = viewModel.uiState.value.apiKeys
        val maskingProviderBeforeReset = viewModel.uiState.value.maskingProvider
        val generationProviderBeforeReset = viewModel.uiState.value.generationProvider
        val maskingModelBeforeReset = viewModel.uiState.value.maskingModel
        val generationModelBeforeReset = viewModel.uiState.value.generationModel
        assertTrue(viewModel.uiState.value.canResetSession)

        viewModel.requestResetSession()

        assertTrue(viewModel.uiState.value.showResetConfirmation)
        assertEquals(original, viewModel.sourcePromptTextFieldState.text.toString())

        viewModel.dismissResetSession()

        assertFalse(viewModel.uiState.value.showResetConfirmation)
        assertEquals(original, viewModel.sourcePromptTextFieldState.text.toString())

        viewModel.requestResetSession()
        viewModel.confirmResetSession()

        val resetState = viewModel.uiState.value
        assertEquals("", viewModel.sourcePromptTextFieldState.text.toString())
        assertEquals("", resetState.sourcePrompt)
        assertEquals(AnalysisCategory.FREE_EDIT, resetState.selectedCategory)
        assertEquals(null, resetState.targetSegment)
        assertEquals(AnalysisStatus.IDLE, resetState.status)
        assertEquals(AnalysisTxtCountPolicy.DEFAULT_COUNT, resetState.txtCount)
        assertTrue(resetState.selectedDirectionIds.isEmpty())
        assertEquals("", resetState.customHint)
        assertTrue(resetState.generatedCandidates.isEmpty())
        assertEquals(AnalysisResultPresentation.NONE, resetState.resultPresentation)
        assertEquals(null, resetState.selectedCandidateIndex)
        assertFalse(resetState.hasAppliedCandidateToAutomation)
        assertEquals(DEFAULT_ANALYSIS_RESULT_FILE_NAME, resetState.resultFileName)
        assertFalse(resetState.showResetConfirmation)
        assertFalse(resetState.canResetSession)
        assertEquals("red hair and 검은 원피스", automationPrompt)
        assertEquals(apiKeysBeforeReset, resetState.apiKeys)
        assertEquals(maskingProviderBeforeReset, resetState.maskingProvider)
        assertEquals(generationProviderBeforeReset, resetState.generationProvider)
        assertEquals(maskingModelBeforeReset, resetState.maskingModel)
        assertEquals(generationModelBeforeReset, resetState.generationModel)
    }

    @Test
    fun viewModel_resetSession_doesNotOpenConfirmationForEmptySession() {
        val viewModel = analysisViewModel(
            aiGateway = FakeAnalysisAiGateway(),
            keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        )

        viewModel.requestResetSession()

        assertFalse(viewModel.uiState.value.showResetConfirmation)
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
        assertEquals(AnalysisResultPresentation.TXT, viewModel.uiState.value.resultPresentation)

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

        // 1. TXT 생성 → 내부 자동 마스킹으로 최초 1차 분석 API 호출
        viewModel.generateTxt()
        assertEquals(1, aiGateway.analyzeCallCount)

        // 2. 한 번 더 TXT 생성 (캐시 재사용되어 1 유지)
        viewModel.generateTxt()
        assertEquals(1, aiGateway.analyzeCallCount)

        // 3. 카테고리 변경 → 캐시 무효화 → 1차 분석 다시 수행
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_HAIRSTYLE)
        viewModel.generateTxt()
        assertEquals(2, aiGateway.analyzeCallCount)

        // 4. 마스킹 해제 후 다시 생성 → 분석 재수행
        viewModel.clearTargetSegment()
        viewModel.generateTxt()
        assertEquals(3, aiGateway.analyzeCallCount)

        // 5. 동일 상태에서 다시 TXT 생성 (캐시 재사용)
        viewModel.generateTxt()
        assertEquals(3, aiGateway.analyzeCallCount)
    }

    @Test
    fun viewModel_trimForInactiveTab_keepsResultsAndSource() {
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
        val segmentBefore = viewModel.uiState.value.targetSegment
        assertNotNull(segmentBefore)

        viewModel.trimForInactiveTab()

        assertEquals(listOf("후보"), viewModel.uiState.value.generatedCandidates)
        assertEquals(segmentBefore, viewModel.uiState.value.targetSegment)
        assertEquals(AnalysisCategory.WOMEN_CLOTHING, viewModel.uiState.value.selectedCategory)
        assertEquals(AnalysisStatus.SUCCESS, viewModel.uiState.value.status)
        assertEquals(prompt, viewModel.sourcePromptTextFieldState.text.toString())
        assertEquals(prompt, viewModel.uiState.value.sourcePrompt)
    }

    @Test
    fun viewModel_trimForInactiveTab_duringMasking_keepsGeneratingJobAndCompletesSuccessfully() {
        var observedStatusDuringMasking: AnalysisStatus? = null
        var observedMessageDuringMasking: String? = null
        var vmRef: AnalysisViewModel? = null

        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """[{"text":"후보 결과","explanation":""}]""",
            onAnalyze = {
                val vm = vmRef ?: return@FakeAnalysisAiGateway
                observedStatusDuringMasking = vm.uiState.value.status
                observedMessageDuringMasking = vm.uiState.value.message
                // 자동 마스킹 진행 중 다른 탭으로 이동 (비활성화)
                vm.trimForInactiveTab()
            }
        )
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        val viewModel = analysisViewModel(aiGateway, keyRepository)
        vmRef = viewModel

        val prompt = "red hair and blue dress"
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)

        // TXT 생성 시작 (마스킹 캐시 없음 -> 자동 마스킹 수행)
        viewModel.generateTxt()

        // 탭 이동 시점의 상태 검증
        assertEquals(AnalysisStatus.GENERATING, observedStatusDuringMasking)
        assertEquals("자동 마스킹 중...", observedMessageDuringMasking)

        // 탭 이동에도 불구하고 작업이 취소되지 않고 최종 완료까지 도달
        assertEquals(AnalysisStatus.SUCCESS, viewModel.uiState.value.status)
        assertEquals(listOf("후보 결과"), viewModel.uiState.value.generatedCandidates)
        assertEquals(1, aiGateway.analyzeCallCount)
        assertEquals(1, aiGateway.generateCallCount)
    }

    @Test
    fun viewModel_trimForInactiveTab_duringCandidateGeneration_keepsGeneratingJobAndCompletesSuccessfully() {
        var observedStatusDuringGen: AnalysisStatus? = null
        var observedMessageDuringGen: String? = null
        var vmRef: AnalysisViewModel? = null

        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """[{"text":"생성된 후보","explanation":""}]""",
            onGenerateTxt = {
                val vm = vmRef ?: return@FakeAnalysisAiGateway
                observedStatusDuringGen = vm.uiState.value.status
                observedMessageDuringGen = vm.uiState.value.message
                // 후보 생성 중 다른 탭으로 이동 (비활성화)
                vm.trimForInactiveTab()
            }
        )
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        val viewModel = analysisViewModel(aiGateway, keyRepository)
        vmRef = viewModel

        val prompt = "red hair and blue dress"
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)

        viewModel.generateTxt()

        assertEquals(AnalysisStatus.GENERATING, observedStatusDuringGen)
        assertEquals("프롬프트 목록 생성 중...", observedMessageDuringGen)

        // 탭을 벗어났어도 백그라운드에서 완료되어 SUCCESS 상태로 전환됨
        assertEquals(AnalysisStatus.SUCCESS, viewModel.uiState.value.status)
        assertEquals(listOf("생성된 후보"), viewModel.uiState.value.generatedCandidates)
    }

    @Test
    fun viewModel_trimForInactiveTab_duringGeneration_failureReflectsInUiState() {
        var vmRef: AnalysisViewModel? = null

        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            onGenerateTxt = {
                val vm = vmRef ?: return@FakeAnalysisAiGateway
                // 생성 중 탭 전환
                vm.trimForInactiveTab()
                // API 실패 시뮬레이션
                throw IllegalStateException("AI 서버 응답 실패")
            }
        )
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        val viewModel = analysisViewModel(aiGateway, keyRepository)
        vmRef = viewModel

        val prompt = "red hair and blue dress"
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)

        viewModel.generateTxt()

        // 탭 밖에서 발생한 실패가 정상적으로 UI 상태에 반영됨
        assertEquals(AnalysisStatus.ERROR, viewModel.uiState.value.status)
        assertEquals("AI 서버 응답 실패", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.canGenerate) // 재시도 가능
    }

    @Test
    fun viewModel_trimForInactiveTab_rapidTabSwitches_doesNotDuplicateOrReset() {
        var vmRef: AnalysisViewModel? = null

        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """[{"text":"안정적인 결과","explanation":""}]""",
            onGenerateTxt = {
                val vm = vmRef ?: return@FakeAnalysisAiGateway
                // 빠른 탭 왕복 시뮬레이션 (비활성화 다회 호출)
                repeat(5) {
                    vm.trimForInactiveTab()
                }
            }
        )
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        val viewModel = analysisViewModel(aiGateway, keyRepository)
        vmRef = viewModel

        val prompt = "red hair and blue dress"
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)

        viewModel.generateTxt()

        // 중복 호출 없이 1회만 호출되고 결과 정상 보존
        assertEquals(1, aiGateway.generateCallCount)
        assertEquals(AnalysisStatus.SUCCESS, viewModel.uiState.value.status)
        assertEquals(listOf("안정적인 결과"), viewModel.uiState.value.generatedCandidates)
    }

    @Test
    fun viewModel_cancelActiveWork_explicitStopCancelsJobAndPreventsLateResults() {
        var vmRef: AnalysisViewModel? = null

        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """[{"text":"늦게 도착한 결과","explanation":""}]""",
            onGenerateTxt = {
                val vm = vmRef ?: return@FakeAnalysisAiGateway
                // 사용자가 명시적으로 중지 버튼 클릭
                vm.cancelActiveWork()
            }
        )
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        val viewModel = analysisViewModel(aiGateway, keyRepository)
        vmRef = viewModel

        val prompt = "red hair and blue dress"
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)

        viewModel.generateTxt()

        // 취소 후 상태는 IDLE, 메시지는 "작업을 중지했습니다."
        assertEquals(AnalysisStatus.IDLE, viewModel.uiState.value.status)
        assertEquals("작업을 중지했습니다.", viewModel.uiState.value.message)
        // 늦게 도착한 결과가 반영되지 않아야 함
        assertTrue(viewModel.uiState.value.generatedCandidates.isEmpty())
    }

    @Test
    fun viewModel_confirmResetSession_cancelsJobAndResetsState() {
        var vmRef: AnalysisViewModel? = null

        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = """[{"text":"취소된 결과","explanation":""}]""",
            onGenerateTxt = {
                val vm = vmRef ?: return@FakeAnalysisAiGateway
                // 작업 중 세션 초기화 확정
                vm.confirmResetSession()
            }
        )
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        val viewModel = analysisViewModel(aiGateway, keyRepository)
        vmRef = viewModel

        val prompt = "red hair and blue dress"
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)

        viewModel.generateTxt()

        // 세션이 완전히 초기화되었는지 확인
        assertEquals(AnalysisStatus.IDLE, viewModel.uiState.value.status)
        assertEquals("", viewModel.uiState.value.sourcePrompt)
        assertTrue(viewModel.uiState.value.generatedCandidates.isEmpty())
    }

    @Test
    fun viewModel_generate_cardsMode_keepsRunningAcrossInactiveTab() {
        var vmRef: AnalysisViewModel? = null

        val candidatesJson = (1..6).joinToString(separator = ",", prefix = "[", postfix = "]") {
            """{"text":"카드 후보 $it","explanation":""}"""
        }
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "blue dress"),
            generateResponse = candidatesJson,
            onGenerateTxt = {
                val vm = vmRef ?: return@FakeAnalysisAiGateway
                // 후보 6개 생성 중 탭 이동
                vm.trimForInactiveTab()
            }
        )
        val keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        val viewModel = analysisViewModel(aiGateway, keyRepository)
        vmRef = viewModel

        val prompt = "red hair and blue dress"
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)

        // 후보 6개 모드인 generate() 호출
        viewModel.generate()

        assertEquals(AnalysisStatus.SUCCESS, viewModel.uiState.value.status)
        assertEquals(AnalysisResultPresentation.CARDS, viewModel.uiState.value.resultPresentation)
        assertEquals(6, viewModel.uiState.value.generatedCandidates.size)
        assertEquals("카드 후보 1", viewModel.uiState.value.generatedCandidates[0])
    }

    @Test
    fun viewModel_trimForInactiveTab_dismissesTemporaryDialogs() {
        val viewModel = analysisViewModel(
            aiGateway = FakeAnalysisAiGateway(),
            keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        )
        val prompt = "red hair and blue dress"
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)

        viewModel.requestResetSession()
        assertTrue(viewModel.uiState.value.showResetConfirmation)

        viewModel.trimForInactiveTab()
        assertFalse(viewModel.uiState.value.showResetConfirmation)
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
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "red dress"),
            generateResponse = """[{"text":"후보","explanation":"설명"}]"""
        )
        val viewModel = analysisViewModel(
            aiGateway = aiGateway,
            keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret")
        )
        val original = "red dress blue sky"
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(original)
        viewModel.onSourcePromptChange(original)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_CLOTHING)
        // 생성으로 자동 마스킹 구간을 만든 뒤, 가져오기로 원문을 바꾸면 구간이 비워져야 한다.
        viewModel.generateTxt()
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
            analyzeResponse = analysisJson(exactText = "red hair"),
            generateResponse = """[{"text":"후보","explanation":"설명"}]"""
        )
        val viewModel = analysisViewModel(
            aiGateway = aiGateway,
            keyRepository = FakeGeminiApiKeyRepository(activeKey = "secret"),
            clipboardGateway = clipboard
        )
        val prompt = "red hair and blue dress"
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_HAIRSTYLE)
        viewModel.generateTxt()
        viewModel.onResultFileNameChange("hair")

        var handedOff: String? = null
        viewModel.saveGeneratedResults { handedOff = it }

        assertEquals("__hair__ and blue dress", handedOff)
        assertEquals("__hair__ and blue dress", clipboard.writtenText)
        assertTrue(viewModel.uiState.value.message.contains("자동화 프롬프트"))
    }

    @Test
    fun preconditionHint_progressesThrough_source_category_masking_generation_order() {
        val keyRepo = FakeGeminiApiKeyRepository()
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "hair"),
            generateResponse = """[{"text":"후보 1","explanation":"설명"},{"text":"후보 2","explanation":"설명"}]"""
        )
        val viewModel = analysisViewModel(aiGateway = aiGateway, keyRepository = keyRepo)
        // 생성 프로바이더를 Grok으로 명시적 설정 (Grok 미로그인 상태)
        viewModel.onRoleProviderSelected(
            com.example.gemgemgen.analysis.domain.AnalysisModelRole.GENERATION,
            com.example.gemgemgen.analysis.domain.AnalysisProvider.GROK
        )

        // 1. 원문 없음 -> 원문 안내 + canGenerate false
        assertEquals("원문을 입력하거나 가져오세요.", viewModel.uiState.value.preconditionHintMessage)
        assertFalse(viewModel.uiState.value.canGenerate)

        // 2. 원문 입력 -> 기본 카테고리(분석 수정)가 이미 선택되어 있으므로 바로 마스킹 키 안내 + canGenerate false
        val prompt = "red hair and blue dress"
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)
        assertEquals(
            "자동 마스킹용 Gemini API 키를 등록하거나 활성화하세요.",
            viewModel.uiState.value.preconditionHintMessage
        )
        assertFalse(viewModel.uiState.value.canGenerate)

        // 3. 다른 카테고리 선택 -> 여전히 마스킹 키 없음 -> 동일 안내
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_HAIRSTYLE)
        assertEquals(
            "자동 마스킹용 Gemini API 키를 등록하거나 활성화하세요.",
            viewModel.uiState.value.preconditionHintMessage
        )
        assertFalse(viewModel.uiState.value.canGenerate)

        // 마스킹 모델을 Grok으로 변경 시 -> Grok 로그인 안내
        viewModel.onRoleProviderSelected(
            com.example.gemgemgen.analysis.domain.AnalysisModelRole.MASKING,
            com.example.gemgemgen.analysis.domain.AnalysisProvider.GROK
        )
        assertEquals(
            "자동 마스킹용 Grok에 로그인하세요.",
            viewModel.uiState.value.preconditionHintMessage
        )
        assertFalse(viewModel.uiState.value.canGenerate)

        // 마스킹 모델을 다시 Gemini로 변경 후 Gemini 키 등록/활성화 -> 마스킹 인증 충족
        viewModel.onRoleProviderSelected(
            com.example.gemgemgen.analysis.domain.AnalysisModelRole.MASKING,
            com.example.gemgemgen.analysis.domain.AnalysisProvider.GEMINI
        )
        viewModel.onKeyLabelChange("gemini-key")
        viewModel.onKeyValueChange("valid-key-value")
        viewModel.addApiKey()
        // 기본 생성 프로바이더는 Grok이며 Grok 로그인 안됨 -> 생성용 Grok 로그인 안내 (4순위)
        assertEquals(
            "생성용 Grok에 로그인하세요.",
            viewModel.uiState.value.preconditionHintMessage
        )
        assertFalse(viewModel.uiState.value.canGenerate)

        // 생성 모델도 Gemini로 변경 -> Gemini 키가 이미 있으므로 모든 조건 충족!
        viewModel.onRoleProviderSelected(
            com.example.gemgemgen.analysis.domain.AnalysisModelRole.GENERATION,
            com.example.gemgemgen.analysis.domain.AnalysisProvider.GEMINI
        )
        org.junit.Assert.assertNull(viewModel.uiState.value.preconditionHintMessage)
        assertTrue(viewModel.uiState.value.canGenerate)
    }

    @Test
    fun preconditionHint_allowsGenerationWithoutMaskingCredential_whenCacheReusable() {
        val keyRepo = FakeGeminiApiKeyRepository(activeKey = "gemini-secret")
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "hair"),
            generateResponse = """[{"text":"후보 1","explanation":"설명"},{"text":"후보 2","explanation":"설명"}]"""
        )
        val viewModel = analysisViewModel(
            aiGateway = aiGateway,
            keyRepository = keyRepo
        )
        viewModel.onRoleProviderSelected(
            com.example.gemgemgen.analysis.domain.AnalysisModelRole.MASKING,
            com.example.gemgemgen.analysis.domain.AnalysisProvider.GEMINI
        )
        viewModel.onRoleProviderSelected(
            com.example.gemgemgen.analysis.domain.AnalysisModelRole.GENERATION,
            com.example.gemgemgen.analysis.domain.AnalysisProvider.GEMINI
        )

        val prompt = "red hair and blue dress"
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(prompt)
        viewModel.onSourcePromptChange(prompt)
        viewModel.onCategorySelected(AnalysisCategory.WOMEN_HAIRSTYLE)

        // 처음에는 마스킹 키(Gemini)와 생성 키(Gemini) 모두 충족
        org.junit.Assert.assertNull(viewModel.uiState.value.preconditionHintMessage)
        assertTrue(viewModel.uiState.value.canGenerate)

        // 1회 생성 실행 -> 자동 마스킹 완료 및 캐시 생성됨
        viewModel.generate()
        assertEquals(2, viewModel.uiState.value.generatedCandidates.size)
        assertFalse(viewModel.uiState.value.needsMaskingAnalysis)

        // 이제 마스킹 프로바이더를 Grok(로그인 안 됨)으로 변경
        viewModel.onRoleProviderSelected(
            com.example.gemgemgen.analysis.domain.AnalysisModelRole.MASKING,
            com.example.gemgemgen.analysis.domain.AnalysisProvider.GROK
        )
        assertFalse(viewModel.uiState.value.hasMaskingCredential)
        assertTrue(viewModel.uiState.value.hasGenerationCredential)

        // 캐시가 유효하므로 마스킹 인증(Grok)이 없어도 생성이 차단되지 않고 계속 허용됨!
        org.junit.Assert.assertNull(viewModel.uiState.value.preconditionHintMessage)
        assertTrue(viewModel.uiState.value.canGenerate)

        // 하지만 원문이 변경되면 캐시가 무효화되어 다시 마스킹 인증이 필요해짐!
        val modifiedPrompt = "blonde hair and blue dress"
        viewModel.sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(modifiedPrompt)
        viewModel.onSourcePromptChange(modifiedPrompt)

        // 캐시 무효화로 인해 즉시 마스킹 키 부족 안내가 나타나고 버튼 비활성화!
        assertEquals(
            "자동 마스킹용 Grok에 로그인하세요.",
            viewModel.uiState.value.preconditionHintMessage
        )
        assertFalse(viewModel.uiState.value.canGenerate)
    }

    @Test
    fun preconditionHint_hiddenDuringActiveWork() {
        val keyRepo = FakeGeminiApiKeyRepository(activeKey = "gemini-secret")
        val grokRepo = FakeGrokAuthRepository(
            session = GrokAuthSession(
                accessToken = "grok-token",
                refreshToken = "refresh",
                expiresAtMillis = System.currentTimeMillis() + 10 * 60_000L,
                tokenEndpoint = "https://auth.x.ai/oauth2/token",
                accountPreview = "grok_user"
            )
        )
        val aiGateway = FakeAnalysisAiGateway(
            analyzeResponse = analysisJson(exactText = "hair"),
            generateResponse = """[{"text":"후보 1","explanation":"설명"},{"text":"후보 2","explanation":"설명"}]"""
        )
        val viewModel = analysisViewModel(
            aiGateway = aiGateway,
            keyRepository = keyRepo,
            grokAuthRepository = grokRepo
        )

        // IDLE 상태에서 원문이 비어있으면 안내 노출
        assertEquals("원문을 입력하거나 가져오세요.", viewModel.uiState.value.preconditionHintMessage)

        // GENERATING 상태인 UI State로 확인 시 isBusy이므로 안내는 노출되지 않음
        val busyState = viewModel.uiState.value.copy(status = AnalysisStatus.GENERATING)
        org.junit.Assert.assertNull(busyState.preconditionHintMessage)
        assertFalse(busyState.canGenerate)
    }

    private fun analysisViewModel(
        aiGateway: AnalysisAiGateway,
        keyRepository: GeminiApiKeyRepository,
        clipboardGateway: ClipboardGateway = FakeClipboard(),
        grokAuthRepository: GrokAuthRepository = FakeGrokAuthRepository()
    ): AnalysisViewModel {
        val dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        val grokAuth = ManageGrokAuthUseCase(
            gateway = FakeGrokAuthGateway(),
            repository = grokAuthRepository,
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

    private fun analysisJson(
        exactText: String,
        variationGoal: String = "테스트용 변주 목표"
    ): String {
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
              "variationGoal": "$variationGoal",
              "warnings": []
            }
        """.trimIndent()
    }

    private class FakeAnalysisAiGateway(
        private val analyzeResponse: String = analysisJsonStatic("long hair"),
        private val generateResponse: String = "[]",
        private val onAnalyze: (suspend () -> Unit)? = null,
        private val onGenerateTxt: (suspend () -> Unit)? = null
    ) : AnalysisAiGateway {
        var analyzeCallCount = 0
        var generateCallCount = 0
        var lastGenerateCount: Int? = null
        val analyzeModelIds = mutableListOf<String>()
        val generateModelIds = mutableListOf<String>()

        override suspend fun analyze(
            apiKey: String,
            modelId: String,
            payload: AnalysisPromptPayload
        ): String {
            analyzeCallCount++
            analyzeModelIds += modelId
            onAnalyze?.invoke()
            return analyzeResponse
        }

        override suspend fun generateTxt(
            apiKey: String,
            modelId: String,
            payload: AnalysisTxtPromptPayload
        ): String {
            generateCallCount++
            generateModelIds += modelId
            lastGenerateCount = Regex("""Generate exactly (\d+)""")
                .find(payload.systemInstruction)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            assertFalse(payload.systemInstruction.contains("fallback", ignoreCase = true))
            onGenerateTxt?.invoke()
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
                  "variationGoal": "테스트용 변주 목표",
                  "warnings": []
                }
            """.trimIndent()
        }
    }
}
