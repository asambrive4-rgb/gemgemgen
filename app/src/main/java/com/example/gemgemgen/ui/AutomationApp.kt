// 역할: 상단 탭 전환과 각 화면별 상태·액션을 탭 단위로 분리 바인딩하여 조율하는 최상위 UI 진입점입니다.
package com.example.gemgemgen.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.example.gemgemgen.automation.ui.AutomationBarUiState
import com.example.gemgemgen.automation.ui.AutomationScreen
import com.example.gemgemgen.automation.ui.AutomationScreenActions
import com.example.gemgemgen.automation.ui.AutomationUiState
import com.example.gemgemgen.automation.ui.SettingsDialogHost

@Composable
internal fun AutomationApp(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    mainUiState: AutomationUiState,
    automationBarUiState: AutomationBarUiState,
    promptTemplateState: TextFieldState,
    automationActions: AutomationScreenActions,
    analysisContent: @Composable () -> Unit,
    wildcardContent: @Composable () -> Unit
) {
    val currentMainUiState by rememberUpdatedState(mainUiState)
    val currentAutomationBarUiState by rememberUpdatedState(automationBarUiState)
    val currentPromptTemplateState by rememberUpdatedState(promptTemplateState)
    val currentAutomationActions by rememberUpdatedState(automationActions)
    val currentAnalysisContent by rememberUpdatedState(analysisContent)
    val currentWildcardContent by rememberUpdatedState(wildcardContent)

    val tabs = remember {
        listOf(
            MainTabPage(MainTab.AUTOMATION) {
                AutomationScreen(
                    uiState = currentMainUiState,
                    automationBarUiState = currentAutomationBarUiState,
                    promptTemplateState = currentPromptTemplateState,
                    actions = currentAutomationActions
                )
            },
            MainTabPage(MainTab.ANALYSIS) {
                currentAnalysisContent()
            },
            MainTabPage(MainTab.WILDCARD) {
                currentWildcardContent()
            }
        )
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

