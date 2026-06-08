package com.example.gemgemgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WildcardManagerViewModelTest {
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
        val writer = FakeClipboardTextWriter()
        val viewModel = viewModel(
            fileManager = FakeWildcardFileManager("empty.txt" to ""),
            clipboardTextWriter = writer
        )

        viewModel.copyToClipboard()

        assertEquals("복사할 내용이 없습니다.", viewModel.uiState.value.error)
        assertEquals("", writer.writtenText)
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

    private fun viewModel(
        fileManager: FakeWildcardFileManager = FakeWildcardFileManager(),
        clipboardText: String = "",
        clipboardTextWriter: FakeClipboardTextWriter = FakeClipboardTextWriter(),
        canModifyFiles: Boolean = true
    ): WildcardManagerViewModel {
        return WildcardManagerViewModel(
            fileManager = fileManager,
            clipboardTextProvider = FakeClipboardTextProvider(clipboardText),
            clipboardTextWriter = clipboardTextWriter
        ).also {
            it.onFolderAccessChanged(canModifyFiles)
        }
    }

    private class FakeWildcardFileManager(
        vararg initialFiles: Pair<String, String>
    ) : WildcardFileManager {
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
                fileName = fileName,
                documentUri = "content://wildcard/$fileName"
            )
        }
    }

    private class FakeClipboardTextProvider(
        private val text: String
    ) : ClipboardTextProvider {
        override fun readText(): String = text
    }

    private class FakeClipboardTextWriter : ClipboardTextWriter {
        var writtenText: String = ""

        override fun writeText(text: String) {
            writtenText = text
        }
    }
}
