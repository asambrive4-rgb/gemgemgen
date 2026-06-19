package com.example.gemgemgen.automation.domain

data class AutomationRunLog(
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val status: String,
    val lastStep: String,
    val message: String,
    val imeRestoreMessage: String,
    val repeatCount: Int = 0,
    val completedCount: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val markerStatus: String = "",
    val targetApp: String = ""
)

object AutomationRunLogStatus {
    const val SUCCESS = "success"
    const val FAILURE = "failure"
    const val STOPPED = "stopped"

    fun fromState(state: AutomationRunState): String {
        return when (state) {
            AutomationRunState.Success -> SUCCESS
            AutomationRunState.Stopped -> STOPPED
            is AutomationRunState.Failure -> FAILURE
            AutomationRunState.Idle,
            is AutomationRunState.Running -> FAILURE
        }
    }
}
