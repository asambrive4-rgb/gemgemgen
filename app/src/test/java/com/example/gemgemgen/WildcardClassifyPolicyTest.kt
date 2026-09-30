// 역할: 와일드카드 AI 분류 실행 및 저장 조건에 대한 UI/도메인 상태 규칙을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.wildcard.domain.WildcardClassifyResult
import com.example.gemgemgen.wildcard.domain.WildcardClassifySaveEntry
import com.example.gemgemgen.wildcard.domain.WildcardEditorSession
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.ui.WildcardClassifyUiState
import com.example.gemgemgen.wildcard.ui.WildcardUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WildcardClassifyPolicyTest {

    private val sampleFile = WildcardTextFile("hair.txt", "hair.txt")
    private val samplePreview = WildcardClassifyResult(
        criteria = "분위기별",
        sourceLines = listOf("white hair"),
        groups = emptyList()
    )

    private fun uiStateForRequest(
        canModifyFiles: Boolean = true,
        hasSelectedFile: Boolean = true,
        hasSelectableLines: Boolean = true,
        isFileOperationInProgress: Boolean = false,
        isLineSelectionMode: Boolean = false,
        isClassifyBusy: Boolean = false
    ): WildcardUiState {
        return WildcardUiState(
            canModifyFiles = canModifyFiles,
            editor = WildcardEditorSession(
                selectedFile = if (hasSelectedFile) sampleFile else null,
                editingText = if (hasSelectableLines) "white hair\nblack hair" else "   "
            ),
            isFileOperationInProgress = isFileOperationInProgress,
            isLineSelectionMode = isLineSelectionMode,
            classify = WildcardClassifyUiState(isClassifying = isClassifyBusy)
        )
    }

    // --- canRequestClassify 테스트 ---

    @Test
    fun canRequestClassify_allConditionsMet_returnsTrue() {
        assertTrue(uiStateForRequest().canRequestClassify)
    }

    @Test
    fun canRequestClassify_withoutModifyPermission_returnsFalse() {
        assertFalse(uiStateForRequest(canModifyFiles = false).canRequestClassify)
    }

    @Test
    fun canRequestClassify_withoutSelectedFile_returnsFalse() {
        assertFalse(uiStateForRequest(hasSelectedFile = false).canRequestClassify)
    }

    @Test
    fun canRequestClassify_withEmptyLines_returnsFalse() {
        assertFalse(uiStateForRequest(hasSelectableLines = false).canRequestClassify)
    }

    @Test
    fun canRequestClassify_whenFileOperationInProgress_returnsFalse() {
        assertFalse(uiStateForRequest(isFileOperationInProgress = true).canRequestClassify)
    }

    @Test
    fun canRequestClassify_whenLineSelectionMode_returnsFalse() {
        assertFalse(uiStateForRequest(isLineSelectionMode = true).canRequestClassify)
    }

    @Test
    fun canRequestClassify_whenClassifyBusy_returnsFalse() {
        assertFalse(uiStateForRequest(isClassifyBusy = true).canRequestClassify)
    }

    // --- canRunClassify 테스트 ---

    @Test
    fun canRunClassify_withValidCriteriaAndDialogOpened_returnsTrue() {
        val state = WildcardClassifyUiState(
            classifyCriteria = "분위기별",
            isClassifying = false,
            showClassifyCriteriaDialog = true,
            classifyPreview = null
        )
        assertTrue(state.canRunClassify(isFileOperationInProgress = false))
    }

    @Test
    fun canRunClassify_withValidCriteriaAndPreviewPresent_returnsTrue() {
        val state = WildcardClassifyUiState(
            classifyCriteria = "분위기별",
            isClassifying = false,
            showClassifyCriteriaDialog = false,
            classifyPreview = samplePreview
        )
        assertTrue(state.canRunClassify(isFileOperationInProgress = false))
    }

    @Test
    fun canRunClassify_withBlankCriteria_returnsFalse() {
        val state = WildcardClassifyUiState(
            classifyCriteria = "   ",
            isClassifying = false,
            showClassifyCriteriaDialog = true,
            classifyPreview = null
        )
        assertFalse(state.canRunClassify(isFileOperationInProgress = false))
    }

    @Test
    fun canRunClassify_whenClassifying_returnsFalse() {
        val state = WildcardClassifyUiState(
            classifyCriteria = "분위기별",
            isClassifying = true,
            showClassifyCriteriaDialog = true,
            classifyPreview = null
        )
        assertFalse(state.canRunClassify(isFileOperationInProgress = false))
    }

    @Test
    fun canRunClassify_whenFileOperationInProgress_returnsFalse() {
        val state = WildcardClassifyUiState(
            classifyCriteria = "분위기별",
            isClassifying = false,
            showClassifyCriteriaDialog = true,
            classifyPreview = null
        )
        assertFalse(state.canRunClassify(isFileOperationInProgress = true))
    }

    @Test
    fun canRunClassify_whenNeitherDialogNorPreview_returnsFalse() {
        val state = WildcardClassifyUiState(
            classifyCriteria = "분위기별",
            isClassifying = false,
            showClassifyCriteriaDialog = false,
            classifyPreview = null
        )
        assertFalse(state.canRunClassify(isFileOperationInProgress = false))
    }

    // --- canSaveClassifyResult 테스트 ---

    @Test
    fun canSaveClassifyResult_allConditionsMet_returnsTrue() {
        val entries = listOf(
            WildcardClassifySaveEntry(
                groupName = "밝은색",
                items = listOf("white hair", "blonde hair"),
                fileNameInput = "light_hair"
            )
        )
        val state = WildcardClassifyUiState(
            classifyPreview = samplePreview,
            classifySaveEntries = entries,
            isClassifying = false,
            classifyOverwriteConflicts = emptyList()
        )
        assertTrue(state.canSaveClassifyResult(canModifyFiles = true, isFileOperationInProgress = false))
    }

    @Test
    fun canSaveClassifyResult_withoutPreview_returnsFalse() {
        val entries = listOf(WildcardClassifySaveEntry("밝은색", listOf("white"), "light"))
        val state = WildcardClassifyUiState(
            classifyPreview = null,
            classifySaveEntries = entries
        )
        assertFalse(state.canSaveClassifyResult(canModifyFiles = true, isFileOperationInProgress = false))
    }

    @Test
    fun canSaveClassifyResult_withEmptyEntries_returnsFalse() {
        val state = WildcardClassifyUiState(
            classifyPreview = samplePreview,
            classifySaveEntries = emptyList()
        )
        assertFalse(state.canSaveClassifyResult(canModifyFiles = true, isFileOperationInProgress = false))
    }

    @Test
    fun canSaveClassifyResult_withInvalidFileName_returnsFalse() {
        val entries = listOf(WildcardClassifySaveEntry("밝은색", listOf("white"), "   "))
        val state = WildcardClassifyUiState(
            classifyPreview = samplePreview,
            classifySaveEntries = entries
        )
        assertFalse(state.canSaveClassifyResult(canModifyFiles = true, isFileOperationInProgress = false))
    }

    @Test
    fun canSaveClassifyResult_withoutModifyPermission_returnsFalse() {
        val entries = listOf(WildcardClassifySaveEntry("밝은색", listOf("white"), "light"))
        val state = WildcardClassifyUiState(
            classifyPreview = samplePreview,
            classifySaveEntries = entries
        )
        assertFalse(state.canSaveClassifyResult(canModifyFiles = false, isFileOperationInProgress = false))
    }

    @Test
    fun canSaveClassifyResult_whenClassifying_returnsFalse() {
        val entries = listOf(WildcardClassifySaveEntry("밝은색", listOf("white"), "light"))
        val state = WildcardClassifyUiState(
            classifyPreview = samplePreview,
            classifySaveEntries = entries,
            isClassifying = true
        )
        assertFalse(state.canSaveClassifyResult(canModifyFiles = true, isFileOperationInProgress = false))
    }

    @Test
    fun canSaveClassifyResult_whenFileOperationInProgress_returnsFalse() {
        val entries = listOf(WildcardClassifySaveEntry("밝은색", listOf("white"), "light"))
        val state = WildcardClassifyUiState(
            classifyPreview = samplePreview,
            classifySaveEntries = entries
        )
        assertFalse(state.canSaveClassifyResult(canModifyFiles = true, isFileOperationInProgress = true))
    }

    @Test
    fun canSaveClassifyResult_withOverwriteConflicts_returnsFalse() {
        val entries = listOf(WildcardClassifySaveEntry("밝은색", listOf("white"), "light"))
        val state = WildcardClassifyUiState(
            classifyPreview = samplePreview,
            classifySaveEntries = entries,
            classifyOverwriteConflicts = listOf("light.txt")
        )
        assertFalse(state.canSaveClassifyResult(canModifyFiles = true, isFileOperationInProgress = false))
    }
}
