// 역할: 와일드카드 AI 단어 분류 코디네이터의 상태 전이를 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisPromptPayload
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.AnalysisTxtPromptPayload
import com.example.gemgemgen.analysis.domain.DEFAULT_ANALYSIS_MODEL
import com.example.gemgemgen.analysis.usecase.AnalysisAiGateway
import com.example.gemgemgen.analysis.usecase.ResolveAnalysisCredentialUseCase
import com.example.gemgemgen.analysis.usecase.GeminiApiKeyRecord
import com.example.gemgemgen.analysis.usecase.GeminiApiKeyRepository
import com.example.gemgemgen.analysis.usecase.GrokAuthGateway
import com.example.gemgemgen.analysis.usecase.GrokAuthRepository
import com.example.gemgemgen.analysis.usecase.GrokAuthSession
import com.example.gemgemgen.analysis.usecase.GrokDeviceLoginChallenge
import com.example.gemgemgen.analysis.usecase.ManageGeminiApiKeysUseCase
import com.example.gemgemgen.analysis.usecase.ManageGrokAuthUseCase
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.wildcard.domain.WildcardFileException
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.ui.WildcardClassifyCoordinator
import com.example.gemgemgen.wildcard.ui.WildcardClassifyUiState
import com.example.gemgemgen.wildcard.usecase.ClassifyWildcardLinesUseCase
import com.example.gemgemgen.wildcard.usecase.SaveWildcardClassifyResultUseCase
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WildcardClassifyCoordinatorTest {

    @Test
    fun requestClassify_showsErrorWhenFileNotSelected() {
        val host = FakeHost(selectedFile = null)
        val coordinator = createCoordinator(host = host)

        coordinator.requestClassify()

        assertEquals("먼저 txt 파일을 선택해주세요.", host.lastError)
        assertFalse(coordinator.classifyUiState.value.showClassifyCriteriaDialog)
    }

    @Test
    fun requestClassify_showsErrorWhenLinesEmpty() {
        val host = FakeHost(editingText = "")
        val coordinator = createCoordinator(host = host)

        coordinator.requestClassify()

        assertEquals("분류할 줄이 없습니다.", host.lastError)
        assertFalse(coordinator.classifyUiState.value.showClassifyCriteriaDialog)
    }

    @Test
    fun requestClassify_showsErrorWhenCannotModifyFiles() {
        val host = FakeHost(canModifyFiles = false)
        val coordinator = createCoordinator(host = host)

        coordinator.requestClassify()

        assertEquals("파일을 저장하려면 wildcard 폴더를 다시 선택해주세요.", host.lastError)
        assertFalse(coordinator.classifyUiState.value.showClassifyCriteriaDialog)
    }

    @Test
    fun requestClassify_success_opensCriteriaDialogAndClearsSelection() {
        val host = FakeHost()
        val coordinator = createCoordinator(host = host)

        coordinator.requestClassify()

        assertTrue(host.lineSelectionCleared)
        assertTrue(coordinator.classifyUiState.value.showClassifyCriteriaDialog)
        assertEquals("", host.lastError)
    }

    @Test
    fun onClassifyCriteriaChange_updatesCriteriaAndClearsError() {
        val host = FakeHost()
        val coordinator = createCoordinator(host = host)

        host.showError("임의 에러")
        coordinator.onClassifyCriteriaChange("분위기별 분류")

        assertEquals("분위기별 분류", coordinator.classifyUiState.value.classifyCriteria)
        assertEquals("", host.lastError)
    }

    @Test
    fun dismissClassifyCriteriaDialog_closesDialog() {
        val host = FakeHost()
        val coordinator = createCoordinator(host = host)

        coordinator.requestClassify()
        assertTrue(coordinator.classifyUiState.value.showClassifyCriteriaDialog)

        coordinator.dismissClassifyCriteriaDialog()
        assertFalse(coordinator.classifyUiState.value.showClassifyCriteriaDialog)
    }

    @Test
    fun runClassify_success_generatesPreviewAndSaveEntries() {
        val host = FakeHost(editingText = "red tee\nblue jeans")
        val aiResponse = """
            {
              "groups": [
                { "name": "상의", "items": ["red tee"] },
                { "name": "하의", "items": ["blue jeans"] }
              ]
            }
        """.trimIndent()
        val coordinator = createCoordinator(
            host = host,
            aiResponseText = aiResponse
        )

        coordinator.requestClassify()
        coordinator.onClassifyCriteriaChange("의류 종류별")
        coordinator.runClassify()

        val state = coordinator.classifyUiState.value
        assertFalse(state.isClassifying)
        assertFalse(state.showClassifyCriteriaDialog)
        assertNotNull(state.classifyPreview)
        assertEquals(2, state.classifySaveEntries.size)
        assertEquals("상의", state.classifySaveEntries[0].fileNameInput)
        assertEquals("하의", state.classifySaveEntries[1].fileNameInput)
        assertEquals("분류 미리보기: 2개 파일", host.lastMessage)
    }

    @Test
    fun runClassify_blankCriteria_showsError() {
        val host = FakeHost()
        val coordinator = createCoordinator(host = host)

        coordinator.requestClassify()
        coordinator.onClassifyCriteriaChange("")
        coordinator.runClassify()

        assertEquals("분류 기준을 입력해주세요.", host.lastError)
    }

    @Test
    fun onClassifyFileNameChange_updatesTargetEntryName() {
        val host = FakeHost(editingText = "apple\nbanana")
        val aiResponse = """
            {
              "groups": [
                { "name": "과일", "items": ["apple", "banana"] }
              ]
            }
        """.trimIndent()
        val coordinator = createCoordinator(host = host, aiResponseText = aiResponse)

        coordinator.requestClassify()
        coordinator.onClassifyCriteriaChange("과일")
        coordinator.runClassify()

        assertEquals(1, coordinator.classifyUiState.value.classifySaveEntries.size)
        coordinator.onClassifyFileNameChange(0, "fresh_fruits")

        assertEquals("fresh_fruits", coordinator.classifyUiState.value.classifySaveEntries[0].fileNameInput)
    }

    @Test
    fun saveClassifyResult_success_savesFilesAndClearsPreview() {
        val host = FakeHost(editingText = "red\nblue")
        val repo = FakeWildcardRepo()
        val aiResponse = """
            {
              "groups": [
                { "name": "색상", "items": ["red", "blue"] }
              ]
            }
        """.trimIndent()
        val coordinator = createCoordinator(
            host = host,
            aiResponseText = aiResponse,
            repo = repo
        )

        coordinator.requestClassify()
        coordinator.onClassifyCriteriaChange("색상")
        coordinator.runClassify()
        coordinator.saveClassifyResult()

        assertTrue(host.filesSavedCalled)
        assertNull(coordinator.classifyUiState.value.classifyPreview)
        assertTrue(coordinator.classifyUiState.value.classifySaveEntries.isEmpty())
        assertEquals("1개 파일로 저장했습니다.", host.lastMessage)
        assertEquals("red\nblue", repo.contentOf("색상.txt"))
    }

    @Test
    fun saveClassifyResult_detectsConflict_andConfirmOverwriteSucceeds() {
        val repo = FakeWildcardRepo("색상.txt" to "old")
        val host = FakeHost(editingText = "red\nblue")
        val aiResponse = """
            {
              "groups": [
                { "name": "색상", "items": ["red", "blue"] }
              ]
            }
        """.trimIndent()
        val coordinator = createCoordinator(
            host = host,
            aiResponseText = aiResponse,
            repo = repo
        )

        coordinator.requestClassify()
        coordinator.onClassifyCriteriaChange("색상")
        coordinator.runClassify()
        coordinator.saveClassifyResult(overwrite = false)

        assertEquals(listOf("색상.txt"), coordinator.classifyUiState.value.classifyOverwriteConflicts)
        assertEquals("같은 이름의 파일이 있습니다. 덮어쓸까요?", host.lastError)

        coordinator.confirmClassifyOverwrite()

        assertTrue(host.filesSavedCalled)
        assertEquals("red\nblue", repo.contentOf("색상.txt"))
        assertEquals("1개 파일로 저장했습니다.", host.lastMessage)
    }

    @Test
    fun reset_cancelsJobAndClearsState() {
        val host = FakeHost()
        val coordinator = createCoordinator(host = host)

        coordinator.onClassifyCriteriaChange("임의 기준")
        assertEquals("임의 기준", coordinator.classifyUiState.value.classifyCriteria)

        coordinator.reset()
        assertEquals("", coordinator.classifyUiState.value.classifyCriteria)
    }

    private fun createCoordinator(
        host: FakeHost,
        aiResponseText: String = "",
        repo: FakeWildcardRepo = FakeWildcardRepo()
    ): WildcardClassifyCoordinator {
        val fakeAiGateway = object : AnalysisAiGateway {
            override suspend fun analyze(apiKey: String, modelId: String, payload: AnalysisPromptPayload): String = ""
            override suspend fun generateTxt(apiKey: String, modelId: String, payload: AnalysisTxtPromptPayload): String = aiResponseText
        }
        val keyRepository = FakeKeyRepo(activeKey = "fake-key")
        val fakeGrokGateway = object : GrokAuthGateway {
            override suspend fun startDeviceLogin(): GrokDeviceLoginChallenge = error("unused")
            override suspend fun pollDeviceLogin(challenge: GrokDeviceLoginChallenge): GrokAuthSession? = error("unused")
            override suspend fun refreshSession(session: GrokAuthSession): GrokAuthSession = error("unused")
        }
        val fakeGrokRepo = object : GrokAuthRepository {
            override fun loadSession(): GrokAuthSession? = null
            override fun saveSession(session: GrokAuthSession) = Unit
            override fun clearSession() = Unit
        }
        val dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        val grokAuth = ManageGrokAuthUseCase(
            gateway = fakeGrokGateway,
            repository = fakeGrokRepo,
            dispatchers = dispatchers
        )
        val resolver = ResolveAnalysisCredentialUseCase(
            apiKeyRepository = keyRepository,
            grokAuth = grokAuth,
            dispatchers = dispatchers
        )
        val classifyUseCase = ClassifyWildcardLinesUseCase(
            aiGateway = fakeAiGateway,
            credentialResolver = resolver,
            dispatchers = dispatchers
        )
        val saveUseCase = SaveWildcardClassifyResultUseCase(
            repository = repo,
            dispatchers = dispatchers
        )
        val keyManager = ManageGeminiApiKeysUseCase(
            repository = keyRepository,
            dispatchers = dispatchers
        )

        return WildcardClassifyCoordinator(
            classifyWildcardLines = classifyUseCase,
            saveWildcardClassifyResult = saveUseCase,
            analysisKeyManager = keyManager,
            scope = CoroutineScope(Dispatchers.Unconfined),
            host = host
        )
    }

    private class FakeKeyRepo(activeKey: String? = null) : GeminiApiKeyRepository {
        private val rawKeys = mutableMapOf<String, String>()
        private val records = mutableListOf<GeminiApiKeyRecord>()
        private val roleProviders = mutableMapOf("masking" to "gemini", "generation" to "gemini")
        private val roleModels = mutableMapOf("masking" to DEFAULT_ANALYSIS_MODEL, "generation" to DEFAULT_ANALYSIS_MODEL)

        init {
            if (activeKey != null) {
                rawKeys["k1"] = activeKey
                records += GeminiApiKeyRecord("k1", "key1", "enc", "preview", 0L, isActive = true)
            }
        }

        override fun listKeys(): List<GeminiApiKeyRecord> = records
        override fun addKey(label: String, rawKey: String, createdAtMillis: Long): GeminiApiKeyRecord = error("unused")
        override fun deleteKey(id: String) = Unit
        override fun activateKey(id: String) = Unit
        override fun activeKeyValue(): String? = records.firstOrNull { it.isActive }?.let { rawKeys[it.id] }
        override fun updateKeyLabel(id: String, newLabel: String) = Unit
        override fun getRoleProvider(role: String): String = roleProviders[role] ?: "gemini"
        override fun setRoleProvider(role: String, providerId: String) { roleProviders[role] = providerId }
        override fun getRoleModel(role: String): String = roleModels[role] ?: DEFAULT_ANALYSIS_MODEL
        override fun setRoleModel(role: String, modelId: String) { roleModels[role] = modelId }
    }

    private class FakeHost(
        override var selectedFile: WildcardTextFile? = WildcardTextFile("test.txt", "test.txt"),
        override var editingText: String = "line1\nline2",
        override var canModifyFiles: Boolean = true,
        override var isFileOperationInProgress: Boolean = false
    ) : WildcardClassifyCoordinator.Host {
        override val selectableLines: List<String>
            get() = com.example.gemgemgen.wildcard.domain.WildcardDynamicPromptComposer.selectableLines(editingText)
        var lineSelectionCleared: Boolean = false
        var lastMessage: String = ""
        var lastError: String = ""
        var filesSavedCalled: Boolean = false
        var classifyState: WildcardClassifyUiState = WildcardClassifyUiState()

        override val canRequestClassify: Boolean
            get() = canModifyFiles &&
                selectedFile != null &&
                selectableLines.isNotEmpty() &&
                !isFileOperationInProgress &&
                !classifyState.isBusy

        override fun onLineSelectionCleared() {
            lineSelectionCleared = true
        }

        override fun showMessage(message: String) {
            lastMessage = message
            lastError = ""
        }

        override fun showError(error: String) {
            lastError = error
            lastMessage = ""
        }

        override fun clearError() {
            lastError = ""
        }

        override fun clearMessageAndError() {
            lastMessage = ""
            lastError = ""
        }

        override fun updateClassifyState(transform: (WildcardClassifyUiState) -> WildcardClassifyUiState) {
            classifyState = transform(classifyState)
        }

        override fun beginFileOperation(): Boolean = true
        override fun endFileOperation() = Unit
        override suspend fun onFilesSaved() {
            filesSavedCalled = true
        }
    }

    private class FakeWildcardRepo(
        vararg initial: Pair<String, String>
    ) : WildcardFileRepository {
        private val files = linkedMapOf<String, String>()

        init {
            initial.forEach { (name, text) -> files[name] = text }
        }

        fun contentOf(name: String): String = files[name].orEmpty()

        override fun listFiles(): List<WildcardTextFile> =
            files.keys.map { WildcardTextFile(id = it, fileName = it) }

        override fun readFile(file: WildcardTextFile): String =
            files[file.fileName] ?: throw WildcardFileException("없음")

        override fun createFile(fileName: String): WildcardTextFile {
            files[fileName] = ""
            return WildcardTextFile(id = fileName, fileName = fileName)
        }

        override fun writeFile(file: WildcardTextFile, text: String) {
            files[file.fileName] = text
        }

        override fun deleteFile(file: WildcardTextFile) {
            files.remove(file.fileName)
        }

        override fun renameFile(file: WildcardTextFile, newName: String): WildcardTextFile {
            val text = files.remove(file.fileName).orEmpty()
            files[newName] = text
            return WildcardTextFile(id = newName, fileName = newName)
        }
    }
}
