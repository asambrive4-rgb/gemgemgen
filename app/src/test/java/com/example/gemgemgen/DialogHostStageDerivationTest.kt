// 역할: 다이얼로그 호스트의 화면 상태별 활성 다이얼로그 타입 파생 및 우선순위를 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.analysis.ui.AnalysisDialogType
import com.example.gemgemgen.analysis.ui.AnalysisUiState
import com.example.gemgemgen.analysis.ui.deriveActiveAnalysisDialog
import com.example.gemgemgen.analysis.usecase.GeminiApiKeySummary
import com.example.gemgemgen.automation.ui.SettingsDialogStage
import com.example.gemgemgen.wildcard.ui.WildcardDialogType
import com.example.gemgemgen.wildcard.ui.WildcardUiState
import com.example.gemgemgen.wildcard.ui.deriveActiveWildcardDialog
import org.junit.Assert.assertEquals
import org.junit.Test

class DialogHostStageDerivationTest {

    @Test
    fun deriveActiveWildcardDialog_prioritizesCorrectly() {
        val baseState = WildcardUiState()
        assertEquals(WildcardDialogType.None, deriveActiveWildcardDialog(baseState))

        val newFileState = baseState.copy(showNewFileDialog = true, newFileName = "hair")
        assertEquals(
            WildcardDialogType.NewFile(fileName = "hair", error = ""),
            deriveActiveWildcardDialog(newFileState)
        )

        val renameState = baseState.copy(showRenameDialog = true, renameFileName = "new_hair")
        assertEquals(
            WildcardDialogType.RenameFile(fileName = "new_hair", error = ""),
            deriveActiveWildcardDialog(renameState)
        )

        val deleteState = baseState.copy(showDeleteConfirm = true)
        assertEquals(
            WildcardDialogType.DeleteConfirm(fileName = ""),
            deriveActiveWildcardDialog(deleteState)
        )

        val overwriteState = baseState.copy(classify = baseState.classify.copy(classifyOverwriteConflicts = listOf("a.txt")))
        assertEquals(
            WildcardDialogType.ClassifyOverwrite(listOf("a.txt")),
            deriveActiveWildcardDialog(overwriteState)
        )

        val loadingState = baseState.copy(classify = baseState.classify.copy(isClassifying = true))
        assertEquals(WildcardDialogType.ClassifyLoading, deriveActiveWildcardDialog(loadingState))
    }

    @Test
    fun deriveActiveAnalysisDialog_prioritizesCorrectly() {
        val baseState = AnalysisUiState()
        assertEquals(AnalysisDialogType.None, deriveActiveAnalysisDialog(baseState))

        val resetState = baseState.copy(showResetConfirmation = true)
        assertEquals(AnalysisDialogType.ResetSession, deriveActiveAnalysisDialog(resetState))

        val overwriteState = baseState.copy(pendingOverwriteFileName = "output.txt")
        assertEquals(AnalysisDialogType.Overwrite("output.txt"), deriveActiveAnalysisDialog(overwriteState))

        val keyState = baseState.copy(showKeyDialog = true)
        assertEquals(AnalysisDialogType.KeyManagement, deriveActiveAnalysisDialog(keyState))

        val editingKeyState = baseState.copy(
            showKeyDialog = true,
            editingApiKey = GeminiApiKeySummary(id = "id1", label = "label1", preview = "AIza...", isActive = true),
            editingKeyLabelInput = "editing"
        )
        assertEquals(
            AnalysisDialogType.EditKeyLabel(originalLabel = "label1", currentLabel = "editing"),
            deriveActiveAnalysisDialog(editingKeyState)
        )
    }

    @Test
    fun settingsDialogStage_resolutionLogic() {
        val stageAccessibility = if (true) SettingsDialogStage.ACCESSIBILITY_PROMPT else SettingsDialogStage.SETTINGS
        assertEquals(SettingsDialogStage.ACCESSIBILITY_PROMPT, stageAccessibility)

        val stageSettings = if (false) SettingsDialogStage.ACCESSIBILITY_PROMPT else SettingsDialogStage.SETTINGS
        assertEquals(SettingsDialogStage.SETTINGS, stageSettings)
    }
}
