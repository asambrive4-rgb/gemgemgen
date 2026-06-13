package com.example.gemgemgen

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

data class AutomationRunRequest(
    val promptTemplate: String,
    val repeatCountText: String
)

interface WildcardSetLoader {
    fun load(): List<WildcardSet>
}

class RunGeminiAutomationUseCase(
    private val imeManager: ImeManager,
    private val runLogger: RunLogger,
    private val lastRunSnapshotStore: LastRunSnapshotStore,
    private val clipboardTextWriter: ClipboardTextWriter,
    private val wildcardSetLoader: WildcardSetLoader,
    private val clock: () -> Long,
    private val promptGatewayProvider: () -> GeminiPromptGateway?,
    private val launchGeminiApp: () -> Boolean,
    private val dispatchers: AppDispatchers = AppDispatchers(),
    private val promptGenerator: PromptGenerator = PromptGenerator(),
    private val generatePrompt: ((String, List<WildcardSet>, Int) -> GeneratedPrompt)? = null
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
        val startedAtMillis = clock()
        val repeatCount = RepeatCountParser.parse(request.repeatCountText)
        val wildcards = try {
            withContext(dispatchers.io) {
                lastRunSnapshotStore.save(
                    LastRunSnapshot(
                        promptTemplate = request.promptTemplate,
                        repeatCountText = request.repeatCountText
                    )
                )
                clipboardTextWriter.writeText(request.promptTemplate)
                traceSection("automation.prepare") {
                    wildcardSetLoader.load()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            finishWithoutRun(
                startedAtMillis = startedAtMillis,
                repeatCount = repeatCount,
                lastStep = "와일드카드 파일 로드 중",
                state = AutomationRunState.Failure(
                    "와일드카드 파일을 읽지 못했습니다: ${
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
            request = request,
            startedAtMillis = startedAtMillis,
            repeatCount = repeatCount,
            wildcards = wildcards,
            onStateChange = onStateChange
        )
    }

    private fun startPreparedRun(
        request: AutomationRunRequest,
        startedAtMillis: Long,
        repeatCount: Int,
        wildcards: List<WildcardSet>,
        onStateChange: (AutomationRunState) -> Unit
    ) {
        val promptGateway = promptGatewayProvider()
        if (promptGateway == null) {
            finishWithoutRun(
                startedAtMillis = startedAtMillis,
                repeatCount = repeatCount,
                lastStep = "접근성 서비스 확인",
                state = AutomationRunState.Failure("접근성 서비스가 켜져 있지 않습니다."),
                onStateChange = onStateChange
            )
            return
        }

        if (request.promptTemplate.isBlank()) {
            finishWithoutRun(
                startedAtMillis = startedAtMillis,
                repeatCount = repeatCount,
                lastStep = "프롬프트 확인",
                state = AutomationRunState.Failure("원본 프롬프트를 입력하거나 클립보드에서 가져오세요."),
                onStateChange = onStateChange
            )
            return
        }

        val promptPlan = promptGenerator.compile(request.promptTemplate, wildcards)

        onStateChange(AutomationRunState.Running("Null Keyboard로 전환 중"))
        val imeSwitchResult = imeManager.switchToNullKeyboard()
        if (imeSwitchResult is ImeSwitchResult.Failure) {
            finishWithoutRun(
                startedAtMillis = startedAtMillis,
                repeatCount = repeatCount,
                lastStep = "Null Keyboard로 전환 중",
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
            repeatCount = repeatCount,
            wildcards = wildcards,
            promptPlan = promptPlan
        )
        currentRun = run

        updateRunState(run, "Gemini 앱 실행 중", onStateChange)
        if (!launchGeminiApp()) {
            finishRun(
                run = run,
                state = AutomationRunState.Failure("Gemini 앱을 찾지 못했습니다."),
                onStateChange = onStateChange
            )
            return
        }

        sendMarker(run, onStateChange)
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

    private fun sendMarker(
        run: CurrentRun,
        onStateChange: (AutomationRunState) -> Unit
    ) {
        updateRunState(run, "세션 마커 전송 중", onStateChange)
        run.promptGateway.sendPrompt(
            prompt = MARKER_PROMPT,
            newChatMode = GeminiNewChatMode.SidebarThenNearestToSearch,
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
        val generatedPrompt = traceSection("prompt.generate") {
            generatePrompt?.invoke(run.promptTemplate, run.wildcards, nextIndex)
                ?: run.promptPlan.generate(nextIndex)
        }

        run.lastPrompt = generatedPrompt.finalPrompt
        updateRunState(run, "프롬프트 생성 완료", onStateChange)

        run.promptGateway.sendPrompt(
            prompt = generatedPrompt.finalPrompt,
            newChatMode = GeminiNewChatMode.DirectVisibleButton,
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

        val restoreResult = imeManager.restore(run.imeSession)
        val imeRestoreMessage = restoreResult.message()
        val finalState = when (restoreResult) {
            ImeRestoreResult.Success -> state
            is ImeRestoreResult.Failure -> AutomationRunState.Failure(
                "입력기 복구 실패. 원래 입력기: " +
                    "${restoreResult.originalImeId}, 현재 입력기: " +
                    "${restoreResult.currentImeId ?: "확인 불가"}"
            )
        }

        runLogger.append(run.toLog(finalState, imeRestoreMessage, clock()))
        currentRun = null
        onStateChange(finalState)
    }

    private fun finishWithoutRun(
        startedAtMillis: Long,
        repeatCount: Int,
        lastStep: String,
        state: AutomationRunState,
        onStateChange: (AutomationRunState) -> Unit
    ) {
        runLogger.append(
            AutomationRunLog(
                startedAtMillis = startedAtMillis,
                finishedAtMillis = clock(),
                status = AutomationRunLogStatus.fromState(state),
                lastStep = lastStep,
                message = state.message(),
                imeRestoreMessage = "해당 없음",
                repeatCount = repeatCount
            )
        )
        onStateChange(state)
    }

    private fun CurrentRun.toLog(
        state: AutomationRunState,
        imeRestoreMessage: String,
        finishedAtMillis: Long
    ): AutomationRunLog {
        return AutomationRunLog(
            startedAtMillis = startedAtMillis,
            finishedAtMillis = finishedAtMillis,
            status = AutomationRunLogStatus.fromState(state),
            lastStep = lastStep,
            message = state.message(),
            imeRestoreMessage = imeRestoreMessage,
            repeatCount = repeatCount,
            completedCount = completedCount,
            successCount = successCount,
            failureCount = failureCount,
            markerStatus = markerStatus
        )
    }

    private fun ImeRestoreResult.message(): String {
        return when (this) {
            ImeRestoreResult.Success -> "성공"
            is ImeRestoreResult.Failure -> "실패: 원래 입력기 ${originalImeId}, 현재 입력기 ${currentImeId ?: "확인 불가"}"
        }
    }

    private fun AutomationRunState.message(): String {
        return when (this) {
            AutomationRunState.Idle -> ""
            is AutomationRunState.Running -> step
            AutomationRunState.Success -> "성공"
            AutomationRunState.Stopped -> "사용자 중지"
            is AutomationRunState.Failure -> message
        }
    }

    private data class CurrentRun(
        val startedAtMillis: Long,
        val imeSession: ImeSwitchSession,
        val promptGateway: GeminiPromptGateway,
        val promptTemplate: String,
        val repeatCount: Int,
        val wildcards: List<WildcardSet>,
        val promptPlan: PromptGenerator.CompiledPrompt,
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
        const val MARKER_PROMPT = "자연수의 가장 첫 번째 숫자는? 숫자 하나로만 답변해."
    }
}
