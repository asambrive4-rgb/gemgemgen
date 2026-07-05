package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.GeneratedPrompt
import com.example.gemgemgen.automation.domain.PromptGenerator
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.wildcard.domain.WildcardSet
import com.example.gemgemgen.wildcard.usecase.WildcardSetRepository
import kotlinx.coroutines.CancellationException

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
    private val clock: () -> Long,
    private val promptGatewayProvider: PromptAutomationGatewayProvider,
    private val targetAppLauncher: TargetAppLauncher,
    dispatchers: AppDispatchers = AppDispatchers(),
    promptGenerator: PromptGenerator = PromptGenerator(),
    private val generatePrompt: ((String, List<WildcardSet>, Int) -> GeneratedPrompt)? = null,
    private val runPreparer: AutomationRunPreparer = AutomationRunPreparer(
        lastRunSnapshotStore = lastRunSnapshotStore,
        clipboardGateway = clipboardGateway,
        wildcardSetRepository = wildcardSetRepository,
        dispatchers = dispatchers,
        promptGenerator = promptGenerator
    )
) {
    private var currentRun: CurrentRun? = null
    private var isPreparingRun = false

    suspend fun run(
        request: AutomationRunRequest,
        onStateChange: (AutomationRunState) -> Unit
    ) {
        if (currentRun != null || isPreparingRun) {
            onStateChange(AutomationRunState.Failure("이미 실행 중입니다."))
            return
        }

        isPreparingRun = true
        val preparedRun = try {
            runPreparer.prepare(request)
        } catch (error: CancellationException) {
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
            startedAtMillis = clock(),
            onStateChange = onStateChange
        )
    }

    fun cancel(onStateChange: (AutomationRunState) -> Unit) {
        val run = currentRun ?: return
        run.promptGateway.cancelCurrentRun()
        finishRun(
            run = run,
            state = AutomationRunState.Stopped,
            onStateChange = onStateChange
        )
    }

    private fun startPreparedRun(
        preparedRun: PreparedAutomationRun,
        startedAtMillis: Long,
        onStateChange: (AutomationRunState) -> Unit
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

        onStateChange(AutomationRunState.Running("Null Keyboard로 전환 중"))
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
            startedAtMillis = startedAtMillis,
            imeSession = (imeSwitchResult as ImeSwitchResult.Success).session,
            promptGateway = promptGateway,
            promptTemplate = request.promptTemplate,
            repeatCount = preparedRun.repeatCount,
            wildcards = preparedRun.wildcards,
            promptPlan = preparedRun.promptPlan,
            targetApp = request.targetApp
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
        onStateChange: (AutomationRunState) -> Unit
    ) {
        updateRunState(run, "세션 마커 전송 중", onStateChange)
        run.promptGateway.sendPrompt(
            prompt = MARKER_PROMPT,
            newChatMode = NewChatMode.Initial,
            onStateChange = childStateCallback(run, onStateChange),
            onDone = {
                run.markerStatus = "성공"
                sendNextPrompt(run, onStateChange)
            }
        )
    }

    private fun sendNextPrompt(
        run: CurrentRun,
        onStateChange: (AutomationRunState) -> Unit
    ) {
        if (run.successCount >= run.repeatCount) {
            finishRun(run, AutomationRunState.Success, onStateChange)
            return
        }

        val nextIndex = run.successCount + 1
        run.currentIndex = nextIndex
        val generatedPrompt = generatePrompt?.invoke(run.promptTemplate, run.wildcards, nextIndex)
            ?: run.promptPlan.generate(nextIndex)

        run.lastPrompt = generatedPrompt.finalPrompt
        updateRunState(run, "프롬프트 생성 완료", onStateChange)

        run.promptGateway.sendPrompt(
            prompt = generatedPrompt.finalPrompt,
            newChatMode = NewChatMode.Subsequent,
            onStateChange = childStateCallback(run, onStateChange),
            onDone = {
                run.successCount += 1
                run.completedCount += 1
                sendNextPrompt(run, onStateChange)
            }
        )
    }

    private fun childStateCallback(
        run: CurrentRun,
        onStateChange: (AutomationRunState) -> Unit
    ): (AutomationRunState) -> Unit {
        return { state ->
            when (state) {
                is AutomationRunState.Running -> updateRunState(run, state.step, onStateChange)
                is AutomationRunState.Failure -> {
                    run.failureCount += 1
                    finishRun(run, state, onStateChange)
                }
                AutomationRunState.Success -> Unit
                AutomationRunState.Stopped -> finishRun(run, state, onStateChange)
                AutomationRunState.Idle -> onStateChange(state)
            }
        }
    }

    private fun updateRunState(
        run: CurrentRun,
        step: String,
        onStateChange: (AutomationRunState) -> Unit
    ) {
        run.lastStep = step
        onStateChange(
            AutomationRunState.Running(
                step = step,
                currentIndex = run.currentIndex.coerceAtMost(run.repeatCount),
                totalCount = run.repeatCount,
                lastPrompt = run.lastPrompt
            )
        )
    }

    private fun finishRun(
        run: CurrentRun,
        state: AutomationRunState,
        onStateChange: (AutomationRunState) -> Unit
    ) {
        if (run.finished) return

        run.finished = true
        onStateChange(AutomationRunState.Running("원래 입력기로 복구 중"))

        val finalState = when (val restoreResult = imeManager.restore(run.imeSession)) {
            ImeRestoreResult.Success -> state
            is ImeRestoreResult.Failure -> AutomationRunState.Failure(
                "입력기 복구 실패. 원래 입력기 " +
                    "${restoreResult.originalImeId}, 현재 입력기 " +
                    "${restoreResult.currentImeId ?: "확인 불가"}"
            )
        }

        currentRun = null
        onStateChange(finalState)
    }

    private fun finishWithoutRun(
        state: AutomationRunState,
        onStateChange: (AutomationRunState) -> Unit
    ) {
        onStateChange(state)
    }

    private data class CurrentRun(
        val startedAtMillis: Long,
        val imeSession: ImeSwitchSession,
        val promptGateway: PromptAutomationGateway,
        val promptTemplate: String,
        val repeatCount: Int,
        val wildcards: List<WildcardSet>,
        val promptPlan: PromptGenerator.CompiledPrompt,
        val targetApp: AutomationTargetApp,
        var markerStatus: String = "실패",
        var currentIndex: Int = 0,
        var completedCount: Int = 0,
        var successCount: Int = 0,
        var failureCount: Int = 0,
        var lastStep: String = "시작",
        var lastPrompt: String = "",
        var finished: Boolean = false
    )

    companion object {
        const val MARKER_PROMPT = "자연수의 가장 첫 번째 숫자를 숫자 하나로만 답해줘"
    }
}
