// 역할: 접근성 서비스 연결, 액션 바인딩 및 라이프사이클 이벤트 제어로 자동화 화면을 호스팅합니다.
package com.example.gemgemgen.ui.android

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import com.example.gemgemgen.analysis.ui.AnalysisScreenActions
import com.example.gemgemgen.analysis.ui.AnalysisViewModel
import com.example.gemgemgen.automation.android.FloatingAutomationBarController
import com.example.gemgemgen.automation.ui.AutomationScreenActions
import com.example.gemgemgen.automation.usecase.AutomationStartDecision
import com.example.gemgemgen.automation.ui.AutomationViewModel
import com.example.gemgemgen.core.android.AndroidExternalBrowserLauncher
import com.example.gemgemgen.ui.AutomationApp
import com.example.gemgemgen.ui.MainTab
import com.example.gemgemgen.wildcard.domain.WildcardFolderAction
import com.example.gemgemgen.wildcard.ui.WildcardScreenActions
import com.example.gemgemgen.wildcard.ui.WildcardViewModel
import com.example.gemgemgen.remote.domain.AutomationMode

@Composable
fun AndroidAutomationHost(container: AndroidAppContainer) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val windowInfo = LocalWindowInfo.current
    val browserLauncher = remember(context) { AndroidExternalBrowserLauncher(context) }
    val platformNavigator = remember(context) { AndroidHostPlatformNavigator(context) }
    val clearInputFocus = remember(focusManager) {
        { focusManager.clearFocus(force = true) }
    }
    val automationViewModel: AutomationViewModel = viewModel(factory = container.automationViewModelFactory)
    val mainUiState by automationViewModel.uiState.collectAsStateWithLifecycle()
    val automationBarUiState by automationViewModel.automationBarUiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.AUTOMATION) }
    val wildcardStoreOwner = remember { TabViewModelStoreOwner() }
    val analysisStoreOwner = remember { TabViewModelStoreOwner() }
    val analysisViewModel: AnalysisViewModel = viewModel(
        viewModelStoreOwner = analysisStoreOwner,
        factory = container.analysisViewModelFactory
    )
    val analysisUiState by analysisViewModel.uiState.collectAsStateWithLifecycle()
    val analysisPromptState = analysisViewModel.sourcePromptTextFieldState
    val wildcardViewModel: WildcardViewModel = viewModel(
        viewModelStoreOwner = wildcardStoreOwner,
        factory = container.wildcardViewModelFactory
    )
    val wildcardUiState by wildcardViewModel.uiState.collectAsStateWithLifecycle()
    val floatingBarController = remember(activity) {
        activity?.let(::FloatingAutomationBarController)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    DisposableEffect(Unit) {
        onDispose {
            wildcardStoreOwner.clear()
            analysisStoreOwner.clear()
        }
    }

    val wildcardFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            wildcardViewModel.saveWildcardFolder(uri.toString())
            automationViewModel.refreshStatus()
        }
    }

    fun launchWildcardFolderPicker() {
        val initialUri = wildcardViewModel.getInitialWildcardFolderUri()?.let { android.net.Uri.parse(it) }
        wildcardFolderLauncher.launch(initialUri)
    }

    fun openWildcardStorageSettings() {
        platformNavigator.openAllFilesAccessSettings()
    }

    fun selectSafWildcardFolder() {
        if (!wildcardViewModel.requestFolderSelection()) {
            selectedTab = MainTab.WILDCARD
            return
        }
        launchWildcardFolderPicker()
    }

    fun selectWildcardFolder() {
        when (wildcardViewModel.decideWildcardFolderAction(
            hasAllFilesAccess = mainUiState.environmentStatus.hasAllFilesAccess,
            isWildcardDirectoryAccessible = mainUiState.environmentStatus.isWildcardDirectoryAccessible
        )) {
            WildcardFolderAction.OpenDirectFolder -> {
                if (!wildcardViewModel.requestFolderSelection()) return
                wildcardViewModel.onFolderChanged()
                selectedTab = MainTab.WILDCARD
                automationViewModel.refreshStatus()
            }
            WildcardFolderAction.OpenStorageSettings -> openWildcardStorageSettings()
            WildcardFolderAction.LaunchSafPicker -> selectSafWildcardFolder()
        }
    }

    fun trimInactiveTabs(exceptTab: MainTab? = null) {
        if (exceptTab != MainTab.ANALYSIS) {
            // 결과·설정·타겟 구간 및 진행 중 AI 작업은 유지하고 일시적 다이얼로그 상태만 정리.
            analysisViewModel.trimForInactiveTab()
        }
        if (exceptTab != MainTab.WILDCARD) {
            // 미저장 여부와 무관하게 ViewModel과 텍스트 본문은 보존하고, 무거운 Undo 버퍼만 정리하여 재진입 시 0ms 즉시 표시
            wildcardViewModel.trimForInactiveTab()
        }
    }

    fun selectMainTab(tab: MainTab) {
        if (tab != MainTab.AUTOMATION) {
            automationViewModel.cancelParagraphSelection()
        }
        if (selectedTab != tab) {
            trimInactiveTabs(exceptTab = tab)
        }
        // 와일드카드 탭에서 파일 추가/이름변경 후 돌아와도 추천 목록이 갱신되게 한다.
        if (tab == MainTab.AUTOMATION) {
            automationViewModel.refreshWildcardTokenCandidates()
        }
        selectedTab = tab
    }

    fun bringMainActivityToFront() {
        platformNavigator.bringMainActivityToFront()
    }

    fun runAutomation() {
        trimInactiveTabs()
        when (automationViewModel.runAutomation()) {
            AutomationStartDecision.Started -> {
                if (!automationViewModel.uiState.value.isRunning) return
                floatingBarController?.showOrUpdate(
                    uiStateFlow = automationViewModel.automationBarUiState,
                    palette = mainUiState.selectedThemePalette,
                    themeMode = mainUiState.selectedThemeMode,
                    onCancelAutomation = automationViewModel::cancelAutomation,
                    onRepeatCountChange = automationViewModel::onRepeatCountChange,
                    onAutomationFinished = {
                        floatingBarController?.hide()
                        bringMainActivityToFront()
                    }
                )
            }
            AutomationStartDecision.RemoteStarted,
            AutomationStartDecision.Rejected -> Unit
            AutomationStartDecision.PermissionRequired -> platformNavigator.openOverlayPermissionSettings()
        }
    }

    fun selectAutomationMode(mode: AutomationMode) {
        if (mode == AutomationMode.RECEIVER && !mainUiState.environmentStatus.hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        automationViewModel.onAutomationModeSelected(mode)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                automationViewModel.refreshStatus()
                // 멀티윈도우에서는 RESUME만으로 포커스가 안 풀릴 수 있어 force clear.
                clearInputFocus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 스플릿/멀티윈도우: 다른 창을 탭해 우리 창이 포커스를 잃으면 커서·키보드 즉시 해제. (초기 시작 시점 중복 트리거 방지)
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }
            .distinctUntilChanged()
            .drop(1)
            .collect { focused ->
                if (!focused) {
                    clearInputFocus()
                }
            }
    }

    // 첫 화면 렌더링이 완전히 안착된 후 300ms 시차를 두어 플로팅 오버레이를 준비(Staggered Warm-up)하여 콜드 스타트 지연 제거
    LaunchedEffect(floatingBarController) {
        delay(300L)
        floatingBarController?.warmUp()
    }

    DisposableEffect(floatingBarController) {
        onDispose { floatingBarController?.hide() }
    }

    LaunchedEffect(
        wildcardViewModel,
        mainUiState.environmentStatus.canEditWildcardFiles
    ) {
        wildcardViewModel.onFolderAccessChanged(
            mainUiState.environmentStatus.canEditWildcardFiles
        )
    }

    LaunchedEffect(selectedTab, wildcardViewModel) {
        if (selectedTab == MainTab.WILDCARD) {
            wildcardViewModel.onTabEntered()
        }
    }

    AutomationApp(
        selectedTab = selectedTab,
        onSelectTab = ::selectMainTab,
        mainUiState = mainUiState,
        automationBarUiState = automationBarUiState,
        promptTemplateState = automationViewModel.promptTemplateTextFieldState,
        analysisUiState = analysisUiState,
        analysisPromptState = analysisPromptState,
        wildcardUiState = wildcardUiState,
        automationActions = remember(automationViewModel, platformNavigator, clearInputFocus) {
            createAutomationActions(
                automationViewModel = automationViewModel,
                platformNavigator = platformNavigator,
                clearInputFocus = clearInputFocus,
                selectWildcardFolder = ::selectWildcardFolder,
                selectSafWildcardFolder = ::selectSafWildcardFolder,
                openWildcardStorageSettings = ::openWildcardStorageSettings,
                runAutomation = ::runAutomation,
                selectAutomationMode = ::selectAutomationMode
            )
        },
        analysisActions = remember(analysisViewModel, platformNavigator, browserLauncher, clearInputFocus) {
            createAnalysisActions(
                analysisViewModel = analysisViewModel,
                platformNavigator = platformNavigator,
                browserLauncher = browserLauncher,
                clearInputFocus = clearInputFocus,
                onCompleteSave = { selectMainTab(MainTab.AUTOMATION) }
            )
        },
        wildcardActions = remember(wildcardViewModel) {
            createWildcardActions(
                wildcardViewModel = wildcardViewModel,
                selectWildcardFolder = ::selectWildcardFolder
            )
        }
    )
}

private fun createAutomationActions(
    automationViewModel: AutomationViewModel,
    platformNavigator: AndroidHostPlatformNavigator,
    clearInputFocus: () -> Unit,
    selectWildcardFolder: () -> Unit,
    selectSafWildcardFolder: () -> Unit,
    openWildcardStorageSettings: () -> Unit,
    runAutomation: () -> Unit,
    selectAutomationMode: (AutomationMode) -> Unit
): AutomationScreenActions = object : AutomationScreenActions by automationViewModel {
    override fun onPromptTemplateChange(value: String) {
        automationViewModel.onPromptTemplateFromEditor(value)
    }

    override fun onRunAutomation() {
        runAutomation()
    }

    override fun onAutomationModeSelected(mode: AutomationMode) {
        selectAutomationMode(mode)
    }

    override fun onConfirmAccessibilityPrompt() {
        automationViewModel.confirmAccessibilityPrompt()
        platformNavigator.openAccessibilitySettings()
    }

    override fun onSelectWildcardFolder() {
        selectWildcardFolder()
    }

    override fun onSelectSafWildcardFolder() {
        selectSafWildcardFolder()
    }

    override fun onOpenWildcardStorageSettings() {
        openWildcardStorageSettings()
    }

    override fun onOpenAccessibilitySettings() {
        platformNavigator.openAccessibilitySettings()
    }

    override fun onClearFocus() {
        clearInputFocus()
    }
}

private fun createAnalysisActions(
    analysisViewModel: AnalysisViewModel,
    platformNavigator: AndroidHostPlatformNavigator,
    browserLauncher: AndroidExternalBrowserLauncher,
    clearInputFocus: () -> Unit,
    onCompleteSave: () -> Unit
): AnalysisScreenActions = object : AnalysisScreenActions by analysisViewModel {
    override fun onClearFocus() {
        clearInputFocus()
    }

    override fun onSaveResults() {
        analysisViewModel.saveGeneratedResults()
        onCompleteSave()
    }

    override fun onConfirmOverwrite() {
        analysisViewModel.confirmOverwrite()
        onCompleteSave()
    }

    override fun onOpenGrokLoginUrl(url: String) {
        platformNavigator.openUrlPreferFirefox(browserLauncher, url)
    }
}

private fun createWildcardActions(
    wildcardViewModel: WildcardViewModel,
    selectWildcardFolder: () -> Unit
): WildcardScreenActions = object : WildcardScreenActions by wildcardViewModel {
    override fun onSelectFolder() {
        selectWildcardFolder()
    }

    override fun onConfirmPendingSave() {
        wildcardViewModel.confirmPendingWithSave {
            selectWildcardFolder()
        }
    }

    override fun onConfirmPendingDiscard() {
        if (wildcardViewModel.confirmPendingWithDiscard()) {
            selectWildcardFolder()
        }
    }
}
