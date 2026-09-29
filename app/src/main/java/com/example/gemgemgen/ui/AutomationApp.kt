// 역할: 상단 탭 전환과 각 화면별 상태·액션을 탭 단위로 분리 바인딩하여 조율하는 최상위 UI 진입점입니다.
package com.example.gemgemgen.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.gemgemgen.analysis.ui.AnalysisScreen
import com.example.gemgemgen.analysis.ui.AnalysisScreenActions
import com.example.gemgemgen.analysis.ui.AnalysisUiState
import com.example.gemgemgen.automation.ui.AutomationBarUiState
import com.example.gemgemgen.automation.ui.AutomationScreen
import com.example.gemgemgen.automation.ui.AutomationScreenActions
import com.example.gemgemgen.automation.ui.AutomationUiState
import com.example.gemgemgen.automation.ui.SettingsDialogHost
import com.example.gemgemgen.wildcard.ui.WildcardScreen
import com.example.gemgemgen.wildcard.ui.WildcardScreenActions
import com.example.gemgemgen.wildcard.ui.WildcardUiState

@Composable
internal fun AutomationApp(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    mainUiState: AutomationUiState,
    automationBarUiState: AutomationBarUiState,
    promptTemplateState: TextFieldState,
    analysisUiState: AnalysisUiState,
    analysisPromptState: TextFieldState,
    wildcardUiState: WildcardUiState,
    automationActions: AutomationScreenActions,
    analysisActions: AnalysisScreenActions,
    wildcardActions: WildcardScreenActions
) {
    val automationTabPage = remember(
        mainUiState,
        automationBarUiState,
        promptTemplateState,
        automationActions
    ) {
        MainTabPage(MainTab.AUTOMATION) {
            AutomationScreen(
                uiState = mainUiState,
                automationBarUiState = automationBarUiState,
                promptTemplateState = promptTemplateState,
                actions = automationActions
            )
        }
    }

    val analysisTabPage = remember(
        analysisUiState,
        analysisPromptState,
        analysisActions
    ) {
        MainTabPage(MainTab.ANALYSIS) {
            AnalysisScreen(
                uiState = analysisUiState,
                sourcePromptState = analysisPromptState,
                actions = analysisActions
            )
        }
    }

    val environmentStatus = mainUiState.environmentStatus
    val environmentSetupInfo = mainUiState.environmentSetupInfo
    val wildcardTabPage = remember(
        wildcardUiState,
        environmentStatus,
        environmentSetupInfo,
        wildcardActions
    ) {
        MainTabPage(MainTab.WILDCARD) {
            WildcardScreen(
                uiState = wildcardUiState,
                environmentStatus = environmentStatus,
                environmentSetupInfo = environmentSetupInfo,
                actions = wildcardActions
            )
        }
    }

    val tabs = remember(automationTabPage, analysisTabPage, wildcardTabPage) {
        listOf(automationTabPage, analysisTabPage, wildcardTabPage)
    }

    MainTabbedScreen(
        selectedTab = selectedTab,
        onSelectTab = onSelectTab,
        onShowSettings = automationActions::onShowSettings,
        tabs = tabs
    )

    SettingsDialogHost(
        uiState = mainUiState,
        actions = automationActions
    )
}
