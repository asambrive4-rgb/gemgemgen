// 역할: Flow 앱을 대상으로 코루틴 비차단 방식을 통해 프롬프트 입력과 전송 동작을 자동 수행합니다.
package com.example.gemgemgen.automation.android

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.usecase.NewChatMode
import com.example.gemgemgen.automation.usecase.FlowConfigurableGateway
import com.example.gemgemgen.core.AppDefaults
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

internal class FlowPromptAutomation(
    coroutineScope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    rootProvider: () -> AccessibilityNodeInfo?,
    private val tapAtCoordinates: ((Float, Float, (() -> Unit)?) -> Unit)? = null
) : AccessibilityPromptAutomation(
    coroutineScope = coroutineScope,
    dispatcher = dispatcher,
    mainDispatcher = mainDispatcher,
    targetAppName = "Flow"
), FlowConfigurableGateway {
    private var targetImageCount: Int = AppDefaults.DEFAULT_FLOW_IMAGE_COUNT

    override fun setFlowImageCount(count: Int) {
        targetImageCount = count
    }
    private val nodeFinder = FlowAccessibilityNodeFinder(rootProvider)

    override fun onRunFinished() {
        nodeFinder.invalidateCache()
    }

    override suspend fun openNewChat(
        newChatMode: NewChatMode,
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        // 매 자동화의 가장 첫 번째(Initial) 프롬프트에서만 모델을 확인/설정하고,
        // 2회차 이후(FollowUp)는 확인 과정 없이 즉시 건너뜀
        if (newChatMode != NewChatMode.Initial) {
            return true
        }

        return ensureModelPro(notifyState)
    }

    override fun findInputNode(): AccessibilityNodeInfo? {
        return nodeFinder.findInputNode()
    }

    override fun findSendNode(): AccessibilityNodeInfo? {
        return nodeFinder.findSendNode()
    }

    override suspend fun applyPromptText(
        inputNode: AccessibilityNodeInfo,
        prompt: String
    ): Boolean {
        val bounds = Rect()
        inputNode.getBoundsInScreen(bounds)
        val tapX = if (bounds.width() > 0) bounds.exactCenterX() else 437f
        // EditText 컨테이너 상단에서 하단 버튼 행을 제외한 텍스트 영역 중앙을 터치
        val tapY = if (bounds.height() > 0) (bounds.top + 45f) else 1315f

        if (tapAtCoordinates != null && tapX > 0 && tapY > 0) {
            // 1. 입력창 영역을 먼저 물리 탭하여 Flutter 텍스트 엔진을 활성화
            tapAtCoordinates.invoke(tapX, tapY, null)
            delay(INPUT_TAP_SETTLE_MS)
        }

        inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                prompt
            )
        }
        return inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    override fun performSendClick(sendNode: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        sendNode.getBoundsInScreen(bounds)
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()

        // 1. 접근성 ACTION_CLICK 시도
        sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        // 2. Flutter 캔버스 엔진을 위한 물리 터치 제스처 탭 전송
        if (centerX > 0 && centerY > 0 && tapAtCoordinates != null) {
            tapAtCoordinates.invoke(centerX, centerY, null)
            return true
        }

        return sendNode.isEnabled
    }

    override fun isSendConfirmed(prompt: String): Boolean {
        val inputNode = findInputNode()
        val inputText = inputNode?.text?.toString() ?: ""
        val isInputCleared = !inputText.contains(prompt) ||
            inputText == PLACEHOLDER_TEXT ||
            inputText.isBlank()

        val sendNode = findSendNode()
        val isSendDisabled = sendNode == null || !sendNode.isEnabled

        return isInputCleared || isSendDisabled
    }

    private suspend fun ensureModelPro(
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        return retryUntilFound(
            actionName = "Nano Banana Pro 모델 설정 중",
            failureMessage = "Flow Nano Banana Pro 모델을 설정하지 못했습니다.",
            notifyState = notifyState
        ) {
            nodeFinder.invalidateCache()

            // 1. 모델 선택 바텀시트가 열려 있는 경우: 'Nano Banana Pro'를 터치하여 확정
            if (nodeFinder.isModelSheetOpen()) {
                val optionInSheet = nodeFinder.findModelOptionInList(FlowAccessibilityNodeFinder.NANO_BANANA_PRO)
                if (optionInSheet != null && tapNodeOrPerformClick(optionInSheet)) {
                    notifyState(AutomationRunState.Running("Nano Banana Pro 모델 선택 완료"))
                    delay(MODEL_SELECT_WAIT_MS)
                    return@retryUntilFound true
                }
            }

            // 2. 옵션 패널이 이미 열려 있는 경우 (하단 모델 바가 보이는 상태): 모델 바 클릭하여 목록 열기
            val modelBar = nodeFinder.findCurrentModelSelectorButton()
            if (modelBar != null) {
                val countOption = nodeFinder.findImageCountOption(targetImageCount)
                if (countOption != null && !countOption.isSelected) {
                    tapNodeOrPerformClick(countOption)
                    notifyState(AutomationRunState.Running("이미지 생성 수 ${targetImageCount}장 선택 중"))
                    delay(COUNT_SELECT_WAIT_MS)
                    return@retryUntilFound null
                }
                if (tapNodeOrPerformClick(modelBar)) {
                    delay(PANEL_TOGGLE_WAIT_MS)
                    return@retryUntilFound null
                }
            }

            // 3. 옵션 패널이 닫혀 있는 경우: '이미지' 토글 버튼을 눌러 옵션 패널 펼치기
            val toggleButton = nodeFinder.findOptionPanelToggle()
            if (toggleButton != null && tapNodeOrPerformClick(toggleButton)) {
                delay(PANEL_TOGGLE_WAIT_MS)
                return@retryUntilFound null
            }

            null
        } == true
    }

    private fun tapNodeOrPerformClick(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        if (centerX > 0 && centerY > 0 && tapAtCoordinates != null) {
            tapAtCoordinates.invoke(centerX, centerY, null)
            return true
        }
        return clickNodeOrParent(node)
    }

    private companion object {
        const val PLACEHOLDER_TEXT = "무엇을 만들고 싶으신가요?"
        const val MODEL_SELECT_WAIT_MS = 300L
        const val PANEL_TOGGLE_WAIT_MS = 400L
        const val COUNT_SELECT_WAIT_MS = 250L
        const val INPUT_TAP_SETTLE_MS = 150L
    }
}
