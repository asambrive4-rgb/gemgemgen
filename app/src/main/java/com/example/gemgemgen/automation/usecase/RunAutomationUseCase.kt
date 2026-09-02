package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptGenerator
import com.example.gemgemgen.automation.domain.RepeatCountParser
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.wildcard.domain.WildcardSet
import com.example.gemgemgen.wildcard.usecase.WildcardSetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AutomationRunRequest(
    val promptTemplate: String,
    val repeatCountText: String,
    val targetApp: AutomationTargetApp
)

class RunAutomationUseCase(
    private val imeManager: ImeManager,
    lastRunSnapshotStore: LastRunSnapshotStore,
    clipboardGateway: ClipboardGateway,
    wildcardSetRepository: WildcardSetRepository,
    private val promptGatewayProvider: PromptAutomationGatewayProvider,
    private val targetAppLauncher: TargetAppLauncher,
    dispatchers: AppDispatchers = AppDispatchers(),
    promptGenerator: PromptGenerator = PromptGenerator(),
    private val generateFinalPrompt: ((String, List<WildcardSet>, Int) -> String)? = null,
    private val runPreparer: AutomationRunPreparer = AutomationRunPreparer(
        automationStartRecorder = RecordAutomationStartUseCase(
            lastRunSnapshotStore = lastRunSnapshotStore,
            clipboardGateway = clipboardGateway,
            dispatchers = dispatchers
        ),
        wildcardSetRepository = wildcardSetRepository,
        dispatchers = dispatchers,
        promptGenerator = promptGenerator
    )
) {
    private var currentRun: CurrentRun? = null
    private var isPreparingRun = false
    /** 준비 중이거나 실행 중일 때 목표 회차. 종료 시 null. */
    private var sessionRepeatCount: Int? = null
    private val _runState = MutableStateFlow<AutomationRunState>(AutomationRunState.Idle)
    val runState: StateFlow<AutomationRunState> = _runState.asStateFlow()

    suspend fun run(
        request: AutomationRunRequest,
        onStateChange: ((AutomationRunState) -> Unit)? = null
    ) {
        if (currentRun != null || isPreparingRun) {
            // Do not overwrite active runState (still Running); only notify optional callback.
            onStateChange?.invoke(AutomationRunState.Failure("이미 실행 중입니다."))
            return
        }

        isPreparingRun = true
        sessionRepeatCount = RepeatCountParser.parse(request.repeatCountText)
        val preparedRun = try {
            runPreparer.prepare(request)
        } catch (error: CancellationException) {
            clearSessionRepeatCount()
            throw error
        } catch (error: Exception) {
            finishWithoutRun(
                state = AutomationRunState.Failure(
                    "wildcard 파일을 읽지 못했습니다. ${
                        error.message ?: "폴더를 다시 선택해주세요."
                    }"
                ),
                onStateChange = onStateChange
            )
            return
        } finally {
            isPreparingRun = false
        }

        startPreparedRun(
            preparedRun = preparedRun,
            onStateChange = onStateChange
        )
    }

    /**
     * 실행(또는 준비) 중 목표 생성 개수를 바꾼다.
     * 이미 성공한 회차 미만으로는 줄이지 않으며, 적용된 개수를 반환한다.
     * 세션이 없으면 null.
     */
    fun updateRepeatCount(requestedCount: Int): Int? {
        val run = currentRun
        if (run != null) {
            val applied = requestedCount.coerceIn(
                minimumValue = maxOf(1, run.successCount),
                maximumValue = 999
            )
            if (run.repeatCount != applied) {
                run.repeatCount = applied
                sessionRepeatCount = applied
                val step = (_runState.value as? AutomationRunState.Running)?.step
                    ?.ifBlank { null }
                    ?: "실행 중"
                updateRunState(run, step, onStateChange = null)
            } else {
                sessionRepeatCount = applied
            }
            return applied
        }

        if (sessionRepeatCount == null) return null
        val applied = requestedCount.coerceIn(1, 999)
        sessionRepeatCount = applied
        return applied
    }

    fun cancel(onStateChange: ((AutomationRunState) -> Unit)? = null) {
        val run = currentRun ?: return
        run.promptGateway.cancelCurrentRun()
        finishRun(
            run = run,
            state = AutomationRunState.Stopped,
            onStateChange = onStateChange
        )
    }

    fun onAccessibilityLost() {
        val run = currentRun ?: return
        run.promptGateway.cancelCurrentRun()
        finishRun(
            run = run,
            state = AutomationRunState.Failure("접근성 서비스가 중단되었습니다."),
            onStateChange = null
        )
    }

    private fun startPreparedRun(
        preparedRun: PreparedAutomationRun,
        onStateChange: ((AutomationRunState) -> Unit)?
    ) {
        val request = preparedRun.request
        val promptGateway = promptGatewayProvider.current(request.targetApp)
        if (promptGateway == null) {
            finishWithoutRun(
                state = AutomationRunState.Failure("접근성 서비스가 켜져 있지 않습니다."),
                onStateChange = onStateChange
            )
            return
        }

        if (request.promptTemplate.isBlank()) {
            finishWithoutRun(
                state = AutomationRunState.Failure("원본 프롬프트를 입력하거나 클립보드에서 가져오세요."),
                onStateChange = onStateChange
            )
            return
        }

        emitState(AutomationRunState.Running("Null Keyboard로 전환 중"), onStateChange)
        val imeSwitchResult = imeManager.switchToNullKeyboard()
        if (imeSwitchResult is ImeSwitchResult.Failure) {
            finishWithoutRun(
                state = AutomationRunState.Failure(
                    "${imeSwitchResult.message} WRITE_SECURE_SETTINGS 권한과 Null Keyboard 설치 상태를 확인해주세요."
                ),
                onStateChange = onStateChange
            )
            return
        }

        val run = CurrentRun(
            imeSession = (imeSwitchResult as ImeSwitchResult.Success).session,
            promptGateway = promptGateway,
            promptTemplate = request.promptTemplate,
            repeatCount = sessionRepeatCount ?: preparedRun.repeatCount,
            wildcards = preparedRun.wildcards,
            promptPlan = preparedRun.promptPlan
        )
        currentRun = run

        updateRunState(run, "${request.targetApp.displayName} 앱 실행 중", onStateChange)
        if (!targetAppLauncher.launch(request.targetApp)) {
            finishRun(
                run = run,
                state = AutomationRunState.Failure(
                    "${request.targetApp.displayName} 앱을 찾지 못했습니다."
                ),
                onStateChange = onStateChange
            )
            return
        }

        sendMarker(run, onStateChange)
    }

    private fun sendMarker(
        run: CurrentRun,
        onStateChange: ((AutomationRunState) -> Unit)?
    ) {
        updateRunState(run, "세션 마커 전송 중", onStateChange)
        run.promptGateway.sendPrompt(
            prompt = MARKER_PROMPT,
            newChatMode = NewChatMode.Initial,
            onStateChange = childStateCallback(run, onStateChange),
            onDone = {
                sendNextPrompt(run, onStateChange)
            }
        )
    }

    private fun sendNextPrompt(
        run: CurrentRun,
        onStateChange: ((AutomationRunState) -> Unit)?
    ) {
        if (run.successCount >= run.repeatCount) {
            finishRun(run, AutomationRunState.Success, onStateChange)
            return
        }

        val nextIndex = run.successCount + 1
        run.currentIndex = nextIndex
        val finalPrompt = generateFinalPrompt?.invoke(run.promptTemplate, run.wildcards, nextIndex)
            ?: run.promptPlan.generateFinalPrompt(nextIndex)

        updateRunState(run, "프롬프트 생성 완료", onStateChange)

        run.promptGateway.sendPrompt(
            prompt = finalPrompt,
            newChatMode = NewChatMode.Subsequent,
            onStateChange = childStateCallback(run, onStateChange),
            onDone = {
                run.successCount += 1
                sendNextPrompt(run, onStateChange)
            }
        )
    }

    private fun childStateCallback(
        run: CurrentRun,
        onStateChange: ((AutomationRunState) -> Unit)?
    ): (AutomationRunState) -> Unit {
        return { state ->
            when (state) {
                is AutomationRunState.Running -> updateRunState(run, state.step, onStateChange)
                is AutomationRunState.Failure -> finishRun(run, state, onStateChange)
                AutomationRunState.Success -> Unit
                AutomationRunState.Stopped -> finishRun(run, state, onStateChange)
                AutomationRunState.Idle -> emitState(state, onStateChange)
            }
        }
    }

    private fun updateRunState(
        run: CurrentRun,
        step: String,
        onStateChange: ((AutomationRunState) -> Unit)?
    ) {
        emitState(
            AutomationRunState.Running(
                step = step,
                currentIndex = run.currentIndex.coerceAtMost(run.repeatCount),
                totalCount = run.repeatCount
            ),
            onStateChange
        )
    }

    private fun finishRun(
        run: CurrentRun,
        state: AutomationRunState,
        onStateChange: ((AutomationRunState) -> Unit)?
    ) {
        if (run.finished) return

        run.finished = true
        emitState(AutomationRunState.Running("원래 입력기로 복구 중"), onStateChange)

        val finalState = when (val restoreResult = imeManager.restore(run.imeSession)) {
            ImeRestoreResult.Success -> state
            is ImeRestoreResult.Failure -> AutomationRunState.Failure(
                "입력기 복구 실패. 원래 입력기 " +
                    "${restoreResult.originalImeId}, 현재 입력기 " +
                    "${restoreResult.currentImeId ?: "확인 불가"}"
            )
        }

        currentRun = null
        clearSessionRepeatCount()
        emitState(finalState, onStateChange)
    }

    private fun finishWithoutRun(
        state: AutomationRunState,
        onStateChange: ((AutomationRunState) -> Unit)?
    ) {
        clearSessionRepeatCount()
        emitState(state, onStateChange)
    }

    private fun clearSessionRepeatCount() {
        sessionRepeatCount = null
    }

    private fun emitState(
        state: AutomationRunState,
        onStateChange: ((AutomationRunState) -> Unit)?
    ) {
        _runState.value = state
        onStateChange?.invoke(state)
    }

    private data class CurrentRun(
        val imeSession: ImeSwitchSession,
        val promptGateway: PromptAutomationGateway,
        val promptTemplate: String,
        var repeatCount: Int,
        val wildcards: List<WildcardSet>,
        val promptPlan: PromptGenerator.CompiledPrompt,
        var currentIndex: Int = 0,
        var successCount: Int = 0,
        var finished: Boolean = false
    )

    companion object {
        const val MARKER_PROMPT = "자연수의 가장 첫 번째 숫자를 숫자 하나로만 답해줘"
    }
}
