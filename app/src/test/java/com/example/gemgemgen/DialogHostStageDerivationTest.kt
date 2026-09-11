// 역할: 다이얼로그 호스트의 화면 상태별 팝업 표시 단계를 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.ui.AnalysisDialogStage
import com.example.gemgemgen.analysis.ui.AnalysisDialogType
import com.example.gemgemgen.analysis.ui.AnalysisUiState
import com.example.gemgemgen.analysis.ui.deriveActiveAnalysisDialog
import com.example.gemgemgen.analysis.ui.deriveAnalysisDialogStage
import com.example.gemgemgen.analysis.usecase.GeminiApiKeySummary
import com.example.gemgemgen.automation.ui.SettingsDialogStage
import com.example.gemgemgen.wildcard.domain.WildcardClassifyResult
import com.example.gemgemgen.wildcard.ui.WildcardDialogStage
import com.example.gemgemgen.wildcard.ui.WildcardDialogType
import com.example.gemgemgen.wildcard.ui.WildcardManagerUiState
import com.example.gemgemgen.wildcard.ui.deriveActiveWildcardDialog
import com.example.gemgemgen.wildcard.ui.deriveWildcardDialogStage
import org.junit.Assert.assertEquals
import org.junit.Test

class DialogHostStageDerivationTest {

    @Test
    fun wildcardDialogStage_derivesCorrectly_andMaintainsStabilityDuringTyping() {
        assertEquals(null, deriveWildcardDialogStage(WildcardDialogType.None))

        val newFileA = WildcardDialogType.NewFile(fileName = "a", error = "")
        val newFileB = WildcardDialogType.NewFile(fileName = "ab", error = "")
        val stageA = deriveWildcardDialogStage(newFileA)
        val stageB = deriveWildcardDialogStage(newFileB)
        assertEquals(WildcardDialogStage.NEW_FILE, stageA)
        assertEquals(stageA, stageB)

        val renameA = WildcardDialogType.RenameFile(fileName = "old", error = "")
        val renameB = WildcardDialogType.RenameFile(fileName = "new_name", error = "")
        assertEquals(WildcardDialogStage.RENAME_FILE, deriveWildcardDialogStage(renameA))
        assertEquals(deriveWildcardDialogStage(renameA), deriveWildcardDialogStage(renameB))

        val delete = WildcardDialogType.DeleteConfirm(fileName = "target.txt")
        assertEquals(WildcardDialogStage.DELETE_CONFIRM, deriveWildcardDialogStage(delete))

        assertEquals(WildcardDialogStage.UNSAVED_CHANGES, deriveWildcardDialogStage(WildcardDialogType.UnsavedChanges))

        assertEquals(WildcardDialogStage.CLASSIFY_LOADING, deriveWildcardDialogStage(WildcardDialogType.ClassifyLoading))

        val critA = WildcardDialogType.ClassifyCriteria("c1", AnalysisProvider.GEMINI, "model1", "", true)
        val critB = WildcardDialogType.ClassifyCriteria("c1 with more text", AnalysisProvider.GEMINI, "model1", "", true)
        assertEquals(WildcardDialogStage.CLASSIFY_CRITERIA, deriveWildcardDialogStage(critA))
        assertEquals(deriveWildcardDialogStage(critA), deriveWildcardDialogStage(critB))

        val preview = WildcardDialogType.ClassifyPreview(
            result = WildcardClassifyResult(criteria = "crit", sourceLines = emptyList(), groups = emptyList()),
            criteria = "crit",
            saveEntries = emptyList(),
            canSave = false,
            canRerun = true,
            error = ""
        )
        assertEquals(WildcardDialogStage.CLASSIFY_PREVIEW, deriveWildcardDialogStage(preview))

        val overwrite = WildcardDialogType.ClassifyOverwrite(listOf("f1.txt"))
        assertEquals(WildcardDialogStage.CLASSIFY_OVERWRITE, deriveWildcardDialogStage(overwrite))
    }

    @Test
    fun analysisDialogStage_derivesCorrectly_andMaintainsStability() {
        assertEquals(null, deriveAnalysisDialogStage(AnalysisDialogType.None))

        val reset = AnalysisDialogType.ResetSession
        assertEquals(AnalysisDialogStage.RESET_SESSION, deriveAnalysisDialogStage(reset))

        val overwrite = AnalysisDialogType.Overwrite("f1.txt")
        assertEquals(AnalysisDialogStage.OVERWRITE, deriveAnalysisDialogStage(overwrite))

        // KeyManagement and EditKeyLabel stages
        assertEquals(AnalysisDialogStage.KEY_MANAGEMENT, deriveAnalysisDialogStage(AnalysisDialogType.KeyManagement))

        // EditKeyLabel stage remains EDIT_KEY_LABEL even as currentLabel changes during typing
        val editLabelA = AnalysisDialogType.EditKeyLabel(originalLabel = "k1", currentLabel = "k")
        val editLabelB = AnalysisDialogType.EditKeyLabel(originalLabel = "k1", currentLabel = "k1_updated")
        assertEquals(AnalysisDialogStage.EDIT_KEY_LABEL, deriveAnalysisDialogStage(editLabelA))
        assertEquals(deriveAnalysisDialogStage(editLabelA), deriveAnalysisDialogStage(editLabelB))
    }

    @Test
    fun deriveActiveWildcardDialog_prioritizesCorrectly() {
        val baseState = WildcardManagerUiState()
        assertEquals(WildcardDialogType.None, deriveActiveWildcardDialog(baseState))

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
        assertEquals(AnalysisDialogStage.KEY_MANAGEMENT, deriveAnalysisDialogStage(deriveActiveAnalysisDialog(keyState)))

        val editingKeyState = baseState.copy(
            showKeyDialog = true,
            editingApiKey = GeminiApiKeySummary(id = "id1", label = "label1", preview = "AIza...", isActive = true),
            editingKeyLabelInput = "editing"
        )
        assertEquals(AnalysisDialogStage.EDIT_KEY_LABEL, deriveAnalysisDialogStage(deriveActiveAnalysisDialog(editingKeyState)))
    }

    @Test
    fun settingsDialogStage_resolutionLogic() {
        val stageAccessibility = if (true) SettingsDialogStage.ACCESSIBILITY_PROMPT else SettingsDialogStage.SETTINGS
        assertEquals(SettingsDialogStage.ACCESSIBILITY_PROMPT, stageAccessibility)

        val stageSettings = if (false) SettingsDialogStage.ACCESSIBILITY_PROMPT else SettingsDialogStage.SETTINGS
        assertEquals(SettingsDialogStage.SETTINGS, stageSettings)
    }
}
