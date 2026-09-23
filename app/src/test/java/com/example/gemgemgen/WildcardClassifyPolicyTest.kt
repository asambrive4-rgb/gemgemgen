// 역할: 와일드카드 AI 분류 실행 및 저장 조건에 대한 도메인 정책 비즈니스 규칙을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.wildcard.domain.WildcardClassifyPolicy
import com.example.gemgemgen.wildcard.domain.WildcardClassifySaveEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WildcardClassifyPolicyTest {

    // --- canRequestClassify 테스트 ---

    @Test
    fun canRequestClassify_allConditionsMet_returnsTrue() {
        val result = WildcardClassifyPolicy.canRequestClassify(
            canModifyFiles = true,
            hasSelectedFile = true,
            hasSelectableLines = true,
            isFileOperationInProgress = false,
            isLineSelectionMode = false,
            isClassifyBusy = false
        )
        assertTrue(result)
    }

    @Test
    fun canRequestClassify_withoutModifyPermission_returnsFalse() {
        val result = WildcardClassifyPolicy.canRequestClassify(
            canModifyFiles = false,
            hasSelectedFile = true,
            hasSelectableLines = true,
            isFileOperationInProgress = false,
            isLineSelectionMode = false,
            isClassifyBusy = false
        )
        assertFalse(result)
    }

    @Test
    fun canRequestClassify_withoutSelectedFile_returnsFalse() {
        val result = WildcardClassifyPolicy.canRequestClassify(
            canModifyFiles = true,
            hasSelectedFile = false,
            hasSelectableLines = true,
            isFileOperationInProgress = false,
            isLineSelectionMode = false,
            isClassifyBusy = false
        )
        assertFalse(result)
    }

    @Test
    fun canRequestClassify_withEmptyLines_returnsFalse() {
        val result = WildcardClassifyPolicy.canRequestClassify(
            canModifyFiles = true,
            hasSelectedFile = true,
            hasSelectableLines = false,
            isFileOperationInProgress = false,
            isLineSelectionMode = false,
            isClassifyBusy = false
        )
        assertFalse(result)
    }

    @Test
    fun canRequestClassify_whenFileOperationInProgress_returnsFalse() {
        val result = WildcardClassifyPolicy.canRequestClassify(
            canModifyFiles = true,
            hasSelectedFile = true,
            hasSelectableLines = true,
            isFileOperationInProgress = true,
            isLineSelectionMode = false,
            isClassifyBusy = false
        )
        assertFalse(result)
    }

    @Test
    fun canRequestClassify_whenLineSelectionMode_returnsFalse() {
        val result = WildcardClassifyPolicy.canRequestClassify(
            canModifyFiles = true,
            hasSelectedFile = true,
            hasSelectableLines = true,
            isFileOperationInProgress = false,
            isLineSelectionMode = true,
            isClassifyBusy = false
        )
        assertFalse(result)
    }

    @Test
    fun canRequestClassify_whenClassifyBusy_returnsFalse() {
        val result = WildcardClassifyPolicy.canRequestClassify(
            canModifyFiles = true,
            hasSelectedFile = true,
            hasSelectableLines = true,
            isFileOperationInProgress = false,
            isLineSelectionMode = false,
            isClassifyBusy = true
        )
        assertFalse(result)
    }

    // --- canRunClassify 테스트 ---

    @Test
    fun canRunClassify_withValidCriteriaAndDialogOpened_returnsTrue() {
        val result = WildcardClassifyPolicy.canRunClassify(
            criteria = "분위기별",
            isClassifying = false,
            isFileOperationInProgress = false,
            showCriteriaDialog = true,
            hasPreview = false
        )
        assertTrue(result)
    }

    @Test
    fun canRunClassify_withValidCriteriaAndPreviewPresent_returnsTrue() {
        val result = WildcardClassifyPolicy.canRunClassify(
            criteria = "분위기별",
            isClassifying = false,
            isFileOperationInProgress = false,
            showCriteriaDialog = false,
            hasPreview = true
        )
        assertTrue(result)
    }

    @Test
    fun canRunClassify_withBlankCriteria_returnsFalse() {
        val result = WildcardClassifyPolicy.canRunClassify(
            criteria = "   ",
            isClassifying = false,
            isFileOperationInProgress = false,
            showCriteriaDialog = true,
            hasPreview = false
        )
        assertFalse(result)
    }

    @Test
    fun canRunClassify_whenClassifying_returnsFalse() {
        val result = WildcardClassifyPolicy.canRunClassify(
            criteria = "분위기별",
            isClassifying = true,
            isFileOperationInProgress = false,
            showCriteriaDialog = true,
            hasPreview = false
        )
        assertFalse(result)
    }

    @Test
    fun canRunClassify_whenFileOperationInProgress_returnsFalse() {
        val result = WildcardClassifyPolicy.canRunClassify(
            criteria = "분위기별",
            isClassifying = false,
            isFileOperationInProgress = true,
            showCriteriaDialog = true,
            hasPreview = false
        )
        assertFalse(result)
    }

    @Test
    fun canRunClassify_whenNeitherDialogNorPreview_returnsFalse() {
        val result = WildcardClassifyPolicy.canRunClassify(
            criteria = "분위기별",
            isClassifying = false,
            isFileOperationInProgress = false,
            showCriteriaDialog = false,
            hasPreview = false
        )
        assertFalse(result)
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
        val result = WildcardClassifyPolicy.canSaveClassifyResult(
            hasPreview = true,
            saveEntries = entries,
            canModifyFiles = true,
            isClassifying = false,
            isFileOperationInProgress = false,
            hasOverwriteConflicts = false
        )
        assertTrue(result)
    }

    @Test
    fun canSaveClassifyResult_withoutPreview_returnsFalse() {
        val entries = listOf(
            WildcardClassifySaveEntry("밝은색", listOf("white"), "light")
        )
        val result = WildcardClassifyPolicy.canSaveClassifyResult(
            hasPreview = false,
            saveEntries = entries,
            canModifyFiles = true,
            isClassifying = false,
            isFileOperationInProgress = false,
            hasOverwriteConflicts = false
        )
        assertFalse(result)
    }

    @Test
    fun canSaveClassifyResult_withEmptyEntries_returnsFalse() {
        val result = WildcardClassifyPolicy.canSaveClassifyResult(
            hasPreview = true,
            saveEntries = emptyList(),
            canModifyFiles = true,
            isClassifying = false,
            isFileOperationInProgress = false,
            hasOverwriteConflicts = false
        )
        assertFalse(result)
    }

    @Test
    fun canSaveClassifyResult_withInvalidFileName_returnsFalse() {
        val entries = listOf(
            WildcardClassifySaveEntry("밝은색", listOf("white"), "   ")
        )
        val result = WildcardClassifyPolicy.canSaveClassifyResult(
            hasPreview = true,
            saveEntries = entries,
            canModifyFiles = true,
            isClassifying = false,
            isFileOperationInProgress = false,
            hasOverwriteConflicts = false
        )
        assertFalse(result)
    }

    @Test
    fun canSaveClassifyResult_withoutModifyPermission_returnsFalse() {
        val entries = listOf(
            WildcardClassifySaveEntry("밝은색", listOf("white"), "light")
        )
        val result = WildcardClassifyPolicy.canSaveClassifyResult(
            hasPreview = true,
            saveEntries = entries,
            canModifyFiles = false,
            isClassifying = false,
            isFileOperationInProgress = false,
            hasOverwriteConflicts = false
        )
        assertFalse(result)
    }

    @Test
    fun canSaveClassifyResult_whenClassifying_returnsFalse() {
        val entries = listOf(
            WildcardClassifySaveEntry("밝은색", listOf("white"), "light")
        )
        val result = WildcardClassifyPolicy.canSaveClassifyResult(
            hasPreview = true,
            saveEntries = entries,
            canModifyFiles = true,
            isClassifying = true,
            isFileOperationInProgress = false,
            hasOverwriteConflicts = false
        )
        assertFalse(result)
    }

    @Test
    fun canSaveClassifyResult_whenFileOperationInProgress_returnsFalse() {
        val entries = listOf(
            WildcardClassifySaveEntry("밝은색", listOf("white"), "light")
        )
        val result = WildcardClassifyPolicy.canSaveClassifyResult(
            hasPreview = true,
            saveEntries = entries,
            canModifyFiles = true,
            isClassifying = false,
            isFileOperationInProgress = true,
            hasOverwriteConflicts = false
        )
        assertFalse(result)
    }

    @Test
    fun canSaveClassifyResult_withOverwriteConflicts_returnsFalse() {
        val entries = listOf(
            WildcardClassifySaveEntry("밝은색", listOf("white"), "light")
        )
        val result = WildcardClassifyPolicy.canSaveClassifyResult(
            hasPreview = true,
            saveEntries = entries,
            canModifyFiles = true,
            isClassifying = false,
            isFileOperationInProgress = false,
            hasOverwriteConflicts = true
        )
        assertFalse(result)
    }
}
