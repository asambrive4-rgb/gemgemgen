// 역할: 와일드카드 뷰모델의 파일 탐색, 편집, 화면 액션 인터페이스 및 폴더 관리 이벤트 흐름을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.android.*
import com.example.gemgemgen.automation.domain.*
import com.example.gemgemgen.automation.usecase.*
import com.example.gemgemgen.core.*
import com.example.gemgemgen.environment.android.*
import com.example.gemgemgen.environment.domain.*
import com.example.gemgemgen.environment.usecase.*
import com.example.gemgemgen.ui.*
import com.example.gemgemgen.wildcard.ui.*
import com.example.gemgemgen.wildcard.domain.*
import com.example.gemgemgen.wildcard.usecase.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class WildcardViewModelTest {
    @Test
    fun init_opensFirstFile() {
        val viewModel = viewModel(
            fileManager = FakeWildcardFileManager(
                "hair.txt" to "black hair",
                "color.txt" to "blue"
            )
        )

        assertEquals("color.txt", viewModel.uiState.value.selectedFile?.fileName)
        assertEquals("blue", viewModel.uiState.value.editingText)
    }

    @Test
    fun createNewFile_addsTxtExtensionAndOpensFile() {
        val fileManager = FakeWildcardFileManager()
        val viewModel = viewModel(fileManager = fileManager)

        viewModel.requestNewFile()
        viewModel.onNewFileNameChange("hair")
        viewModel.createNewFile()

        assertEquals("hair.txt", viewModel.uiState.value.selectedFile?.fileName)
        assertEquals("", fileManager.contentOf("hair.txt"))
    }

    @Test
    fun createNewFile_rejectsDuplicateFileName() {
        val viewModel = viewModel(
            fileManager = FakeWildcardFileManager("hair.txt" to "black hair")
        )

        viewModel.requestNewFile()
        viewModel.onNewFileNameChange("hair")
        viewModel.createNewFile()

        assertEquals("이미 같은 이름의 파일이 있습니다.", viewModel.uiState.value.error)
    }

    @Test
    fun saveCurrent_updatesSavedTextAndFileContent() {
        val fileManager = FakeWildcardFileManager("hair.txt" to "black hair")
        val viewModel = viewModel(fileManager = fileManager)

        viewModel.onTextChange("silver hair")
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.saveCurrent()

        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertEquals("silver hair", fileManager.contentOf("hair.txt"))
    }

    @Test
    fun trimForInactiveTab_preservesCleanEditorBody() {
        val viewModel = viewModel(
            fileManager = FakeWildcardFileManager("hair.txt" to "black hair")
        )
        assertEquals("black hair", viewModel.uiState.value.editingText)

        viewModel.trimForInactiveTab()

        assertEquals("hair.txt", viewModel.uiState.value.selectedFile?.fileName)
        assertEquals("black hair", viewModel.uiState.value.editingText)
        assertEquals("black hair", viewModel.uiState.value.savedText)
        assertTrue(viewModel.uiState.value.undoStack.isEmpty())
    }

    @Test
    fun onTabEntered_maintainsPreservedCleanSelection() {
        val viewModel = viewModel(
            fileManager = FakeWildcardFileManager("hair.txt" to "black hair")
        )
        viewModel.trimForInactiveTab()
        assertEquals("black hair", viewModel.uiState.value.editingText)

        viewModel.onTabEntered()

        assertEquals("black hair", viewModel.uiState.value.editingText)
    }

    @Test
    fun selectFile_withUnsavedChangesShowsPendingAction() {
        val fileManager = FakeWildcardFileManager(
            "hair.txt" to "black hair",
            "color.txt" to "blue"
        )
        val viewModel = viewModel(fileManager = fileManager)
        val colorFile = viewModel.uiState.value.files.first { it.fileName == "color.txt" }
        val hairFile = viewModel.uiState.value.files.first { it.fileName == "hair.txt" }

        viewModel.selectFile(hairFile)
        viewModel.onTextChange("silver hair")
        viewModel.selectFile(colorFile)

        assertEquals("hair.txt", viewModel.uiState.value.selectedFile?.fileName)
        assertNotNull(viewModel.uiState.value.pendingAction)
    }

    @Test
    fun pasteBelowAndUndo_restorePreviousText() {
        val viewModel = viewModel(
            fileManager = FakeWildcardFileManager("hair.txt" to "black hair"),
            clipboardText = "silver hair"
        )

        viewModel.pasteBelowFromClipboard()
        assertEquals("black hair\nsilver hair", viewModel.uiState.value.editingText)

        viewModel.undoClipboardEdit()
        assertEquals("black hair", viewModel.uiState.value.editingText)
    }

    @Test
    fun copyToClipboard_withEmptyTextShowsError() {
        val clipboardGateway = FakeClipboardGateway()
        val viewModel = viewModel(
            fileManager = FakeWildcardFileManager("empty.txt" to ""),
            clipboardGateway = clipboardGateway
        )

        viewModel.copyToClipboard()

        assertEquals("복사할 내용이 없습니다.", viewModel.uiState.value.error)
        assertEquals("", clipboardGateway.writtenText)
    }

    @Test
    fun canModifyFiles_falseDisablesWriteActions() {
        val viewModel = viewModel(
            fileManager = FakeWildcardFileManager("hair.txt" to "black hair"),
            canModifyFiles = false
        )

        assertFalse(viewModel.uiState.value.canCreateFile)
        assertFalse(viewModel.uiState.value.canSave)
        assertFalse(viewModel.uiState.value.canDelete)

        viewModel.onTextChange("silver hair")
        viewModel.saveCurrent()

        assertEquals("파일을 편집하려면 wildcard 폴더를 다시 선택해주세요.", viewModel.uiState.value.error)
    }

    @Test
    fun renameSelectedFile_updatesFileNameAndKeepsContent() {
        val fileManager = FakeWildcardFileManager("hair.txt" to "black hair")
        val viewModel = viewModel(fileManager = fileManager)

        viewModel.requestRenameSelectedFile()
        assertEquals("hair", viewModel.uiState.value.renameFileName)
        assertTrue(viewModel.uiState.value.showRenameDialog)

        viewModel.onRenameFileNameChange("new_hair")
        viewModel.renameSelectedFile()

        assertFalse(viewModel.uiState.value.showRenameDialog)
        assertEquals("new_hair.txt", viewModel.uiState.value.selectedFile?.fileName)
        assertEquals("black hair", viewModel.uiState.value.editingText)
        assertEquals("black hair", fileManager.contentOf("new_hair.txt"))
        assertEquals("", fileManager.contentOf("hair.txt"))
    }

    @Test
    fun renameSelectedFile_rejectsDuplicateFileName() {
        val fileManager = FakeWildcardFileManager(
            "hair.txt" to "black hair",
            "color.txt" to "blue"
        )
        val viewModel = viewModel(fileManager = fileManager)

        viewModel.requestRenameSelectedFile()
        viewModel.onRenameFileNameChange("hair")
        viewModel.renameSelectedFile()

        assertTrue(viewModel.uiState.value.showRenameDialog)
        assertEquals("이미 같은 이름의 파일이 있습니다.", viewModel.uiState.value.error)
    }

    @Test
    fun renameSelectedFile_rejectsEmptyName() {
        val fileManager = FakeWildcardFileManager("hair.txt" to "black hair")
        val viewModel = viewModel(fileManager = fileManager)

        viewModel.requestRenameSelectedFile()
        viewModel.onRenameFileNameChange("")
        viewModel.renameSelectedFile()

        assertTrue(viewModel.uiState.value.showRenameDialog)
        assertEquals("파일 이름을 입력해주세요.", viewModel.uiState.value.error)
    }

    @Test
    fun uiState_exposesDisplayValuesForScreen() {
        val viewModel = viewModel(
            fileManager = FakeWildcardFileManager("hair.txt" to "black hair")
        )

        viewModel.onTextChange("silver hair")

        assertEquals("hair.txt *", viewModel.uiState.value.selectedFileDisplayName)
        assertEquals("hair.txt *", viewModel.uiState.value.fileItems.single().displayName)
        assertTrue(viewModel.uiState.value.fileItems.single().isSelected)
    }

    @Test
    fun composeDynamicPrompt_copiesSelectedLinesToClipboard() {
        val clipboardGateway = FakeClipboardGateway()
        val viewModel = viewModel(
            fileManager = FakeWildcardFileManager(
                "hair.txt" to "black hair\nsilver hair\ngold hair"
            ),
            clipboardGateway = clipboardGateway
        )

        viewModel.enterLineSelectionMode()
        assertTrue(viewModel.uiState.value.isLineSelectionMode)
        assertEquals(
            listOf("black hair", "silver hair", "gold hair"),
            viewModel.uiState.value.selectableLines
        )

        viewModel.toggleLineSelection(0)
        viewModel.toggleLineSelection(2)
        viewModel.composeDynamicPromptToClipboard()

        assertEquals("<black hair|gold hair>", clipboardGateway.writtenText)
        assertEquals(
            "다이나믹 프롬프트를 클립보드에 복사했습니다.",
            viewModel.uiState.value.message
        )
        assertTrue(viewModel.uiState.value.isLineSelectionMode)
        assertEquals(setOf(0, 2), viewModel.uiState.value.selectedLineIndices)
    }

    @Test
    fun composeDynamicPrompt_allowsSingleLine() {
        val clipboardGateway = FakeClipboardGateway()
        val viewModel = viewModel(
            fileManager = FakeWildcardFileManager("hair.txt" to "only one"),
            clipboardGateway = clipboardGateway
        )

        viewModel.enterLineSelectionMode()
        viewModel.toggleLineSelection(0)
        viewModel.composeDynamicPromptToClipboard()

        assertEquals("<only one>", clipboardGateway.writtenText)
    }

    @Test
    fun composeDynamicPrompt_rejectsSyntaxChars() {
        val clipboardGateway = FakeClipboardGateway()
        val viewModel = viewModel(
            fileManager = FakeWildcardFileManager("hair.txt" to "a|b\nok"),
            clipboardGateway = clipboardGateway
        )

        viewModel.enterLineSelectionMode()
        viewModel.selectAllLines()
        viewModel.composeDynamicPromptToClipboard()

        assertEquals("| 또는 <> 가 있는 줄은 다이나믹에 넣을 수 없습니다.", viewModel.uiState.value.error)
        assertEquals("", clipboardGateway.writtenText)
    }

    @Test
    fun selectFile_exitsLineSelectionMode() {
        val fileManager = FakeWildcardFileManager(
            "a.txt" to "one",
            "b.txt" to "two"
        )
        val viewModel = viewModel(fileManager = fileManager)
        val aFile = viewModel.uiState.value.files.first { it.fileName == "a.txt" }
        val bFile = viewModel.uiState.value.files.first { it.fileName == "b.txt" }

        viewModel.selectFile(aFile)
        viewModel.enterLineSelectionMode()
        viewModel.toggleLineSelection(0)
        assertTrue(viewModel.uiState.value.isLineSelectionMode)

        viewModel.selectFile(bFile)

        assertFalse(viewModel.uiState.value.isLineSelectionMode)
        assertTrue(viewModel.uiState.value.selectedLineIndices.isEmpty())
        assertEquals("b.txt", viewModel.uiState.value.selectedFile?.fileName)
    }

    @Test
    fun enterLineSelectionMode_usesUnsavedEditingText() {
        val viewModel = viewModel(
            fileManager = FakeWildcardFileManager("hair.txt" to "old")
        )

        viewModel.onTextChange("old\nnew line")
        viewModel.enterLineSelectionMode()

        assertEquals(listOf("old", "new line"), viewModel.uiState.value.selectableLines)
    }

    @Test
    fun saveWildcardFolder_success_updatesUiStateAndRefreshesFiles() {
        val folderRepo = FakeWildcardFolderRepository()
        val fileManager = FakeWildcardFileManager("old.txt" to "content")
        val viewModel = viewModel(
            fileManager = fileManager,
            folderRepository = folderRepo
        )

        val result = viewModel.saveWildcardFolder("content://valid/folder")

        assertEquals(FolderSelectionResult.Success, result)
        assertEquals("content://valid/folder", folderRepo.lastSavedUri)
        assertEquals("wildcard 폴더를 선택했습니다.", viewModel.uiState.value.message)
        assertEquals("", viewModel.uiState.value.error)
        assertEquals("old.txt", viewModel.uiState.value.selectedFile?.fileName)
    }

    @Test
    fun saveWildcardFolder_failure_updatesErrorUiState() {
        val folderRepo = FakeWildcardFolderRepository(
            saveResult = FolderSelectionResult.Failure("권한 거부")
        )
        val viewModel = viewModel(folderRepository = folderRepo)

        val result = viewModel.saveWildcardFolder("content://invalid/folder")

        assertTrue(result is FolderSelectionResult.Failure)
        assertEquals("폴더 권한 저장 실패: 권한 거부", viewModel.uiState.value.error)
        assertEquals("", viewModel.uiState.value.message)
    }

    @Test
    fun getInitialWildcardFolderUri_returnsSavedUri() {
        val folderRepo = FakeWildcardFolderRepository(initialFolderUri = "content://initial/folder")
        val viewModel = viewModel(folderRepository = folderRepo)

        assertEquals("content://initial/folder", viewModel.getInitialWildcardFolderUri())
    }

    @Test
    fun decideWildcardFolderAction_delegatesBasedOnEnvironmentStatus() {
        val directEnv = EnvironmentReport(status = EnvironmentStatus(hasAllFilesAccess = true, isWildcardDirectoryAccessible = true))
        val directVm = viewModel(environmentGateway = FakeEnvironmentGateway(directEnv))
        assertEquals(WildcardFolderAction.OpenDirectFolder, directVm.decideWildcardFolderAction())

        val settingsEnv = EnvironmentReport(status = EnvironmentStatus(hasAllFilesAccess = false, isWildcardDirectoryAccessible = false))
        val settingsVm = viewModel(environmentGateway = FakeEnvironmentGateway(settingsEnv))
        assertEquals(WildcardFolderAction.OpenStorageSettings, settingsVm.decideWildcardFolderAction())

        val safEnv = EnvironmentReport(status = EnvironmentStatus(hasAllFilesAccess = false, isWildcardDirectoryAccessible = true))
        val safVm = viewModel(environmentGateway = FakeEnvironmentGateway(safEnv))
        assertEquals(WildcardFolderAction.LaunchSafPicker, safVm.decideWildcardFolderAction())
    }

    @Test
    fun saveWildcardFolderUseCase_validatesBlankFolder() {
        val repo = FakeWildcardFolderRepository()
        val useCase = SaveWildcardFolderUseCase(repo)

        val blankResult = useCase.save("   ")
        assertTrue(blankResult is FolderSelectionResult.Failure)
        assertEquals("폴더 경로가 비어 있습니다.", (blankResult as FolderSelectionResult.Failure).reason)
        assertEquals(0, repo.saveCallCount)

        val validResult = useCase.save("content://test")
        assertEquals(FolderSelectionResult.Success, validResult)
        assertEquals("content://test", repo.lastSavedUri)
        assertEquals(1, repo.saveCallCount)
    }

    @Test
    fun actions_fileSelect_opensSelectedFile() {
        val fileManager = FakeWildcardFileManager(
            "hair.txt" to "black hair",
            "color.txt" to "blue"
        )
        val viewModel = viewModel(fileManager = fileManager)
        val actions: WildcardScreenActions = viewModel
        val hairFile = viewModel.uiState.value.files.first { it.fileName == "hair.txt" }

        actions.onFileClick(hairFile)

        assertEquals("hair.txt", viewModel.uiState.value.selectedFile?.fileName)
        assertEquals("black hair", viewModel.uiState.value.editingText)
    }

    @Test
    fun actions_editorChangeTextAndSave_persistsContent() {
        val fileManager = FakeWildcardFileManager("hair.txt" to "black hair")
        val viewModel = viewModel(fileManager = fileManager)
        val actions: WildcardScreenActions = viewModel

        actions.onTextChanged("blonde hair")
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        actions.onSaveFile()
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertEquals("blonde hair", fileManager.contentOf("hair.txt"))
    }

    @Test
    fun actions_lineSelectionActions_enterAndToggle() {
        val fileManager = FakeWildcardFileManager("hair.txt" to "black hair\nblonde hair")
        val viewModel = viewModel(fileManager = fileManager)
        val actions: WildcardScreenActions = viewModel

        actions.onEnterLineSelectionMode()
        assertTrue(viewModel.uiState.value.isLineSelectionMode)

        actions.onToggleLineSelection(0)
        assertEquals(setOf(0), viewModel.uiState.value.selectedLineIndices)

        actions.onSelectAllLines()
        assertEquals(setOf(0, 1), viewModel.uiState.value.selectedLineIndices)

        actions.onExitLineSelectionMode()
        assertFalse(viewModel.uiState.value.isLineSelectionMode)
    }

    @Test
    fun wildcardScreenActions_classifyRequest_opensCriteriaDialog() {
        val fileManager = FakeWildcardFileManager("hair.txt" to "black hair\nblonde hair")
        val viewModel = viewModel(fileManager = fileManager)
        val actions: WildcardScreenActions = viewModel

        actions.requestClassify()
        // classifyWildcardLines가 null이면 에러를 띄움
        assertEquals("분류 기능을 사용할 수 없습니다.", viewModel.uiState.value.error)
    }

    private fun viewModel(
        fileManager: FakeWildcardFileManager = FakeWildcardFileManager(),
        clipboardText: String = "",
        clipboardGateway: FakeClipboardGateway = FakeClipboardGateway(clipboardText),
        canModifyFiles: Boolean = true,
        folderRepository: WildcardFolderRepository? = null,
        environmentGateway: EnvironmentGateway? = null
    ): WildcardViewModel {
        return WildcardViewModel(
            manageWildcardFiles = ManageWildcardFilesUseCase(
                repository = fileManager,
                dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
            ),
            wildcardClipboard = WildcardClipboardUseCase(
                clipboardGateway,
                dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
            ),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            wildcardFolderRepository = folderRepository,
            checkEnvironmentStatus = environmentGateway?.let { CheckEnvironmentStatusUseCase(it) }
        ).also {
            it.onFolderAccessChanged(canModifyFiles)
        }
    }

    private class FakeWildcardFileManager(
        vararg initialFiles: Pair<String, String>
    ) : WildcardFileRepository {
        private val files = linkedMapOf<String, String>()

        init {
            initialFiles.forEach { (fileName, content) ->
                files[fileName] = content
            }
        }

        override fun listFiles(): List<WildcardTextFile> {
            return files.keys
                .sortedBy { it.lowercase() }
                .map { fileName -> file(fileName) }
        }

        override fun readFile(file: WildcardTextFile): String {
            return files[file.fileName] ?: throw WildcardFileException("파일을 열지 못했습니다.")
        }

        override fun createFile(fileName: String): WildcardTextFile {
            val normalizedName = WildcardFileName.normalize(fileName)
                ?: throw WildcardFileException("파일명을 입력해주세요.")
            if (files.keys.any { it.equals(normalizedName, ignoreCase = true) }) {
                throw WildcardFileException("이미 같은 이름의 파일이 있습니다.")
            }

            files[normalizedName] = ""
            return file(normalizedName)
        }

        override fun writeFile(file: WildcardTextFile, text: String) {
            if (!files.containsKey(file.fileName)) {
                throw WildcardFileException("파일을 저장하지 못했습니다.")
            }
            files[file.fileName] = text
        }

        override fun deleteFile(file: WildcardTextFile) {
            files.remove(file.fileName)
        }

        override fun renameFile(file: WildcardTextFile, newName: String): WildcardTextFile {
            val normalizedName = WildcardFileName.normalize(newName)
                ?: throw WildcardFileException("파일 이름을 입력해주세요.")
            if (files.keys.any { it != file.fileName && it.equals(normalizedName, ignoreCase = true) }) {
                throw WildcardFileException("이미 같은 이름의 파일이 있습니다.")
            }

            val content = files.remove(file.fileName) ?: ""
            files[normalizedName] = content
            return file(normalizedName)
        }

        fun contentOf(fileName: String): String = files[fileName].orEmpty()

        private fun file(fileName: String): WildcardTextFile {
            return WildcardTextFile(
                id = fileName,
                fileName = fileName
            )
        }
    }

    private class FakeClipboardGateway(
        private val readableText: String = ""
    ) : ClipboardGateway {
        var writtenText: String = ""

        override fun readText(): String = readableText

        override fun writeText(text: String) {
            writtenText = text
        }
    }

    private class FakeWildcardFolderRepository(
        private var saveResult: FolderSelectionResult = FolderSelectionResult.Success,
        initialFolderUri: String? = null
    ) : WildcardFolderRepository {
        var storedFolderUri: String? = initialFolderUri
        var lastSavedUri: String? = null
        var saveCallCount: Int = 0

        override fun save(folderUri: String): FolderSelectionResult {
            saveCallCount++
            lastSavedUri = folderUri
            if (saveResult is FolderSelectionResult.Success) {
                this.storedFolderUri = folderUri
            }
            return saveResult
        }

        override fun getFolderUri(): String? = storedFolderUri
    }

    private class FakeEnvironmentGateway(
        var report: EnvironmentReport = EnvironmentReport()
    ) : EnvironmentGateway {
        override fun check(): EnvironmentReport = report
    }
}
