package com.example.gemgemgen.ui

import com.example.gemgemgen.automation.domain.AutomationRunLog
import com.example.gemgemgen.automation.domain.AutomationRunLogStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RunLogUiText {
    fun title(log: AutomationRunLog): String {
        return "${formatTime(log.finishedAtMillis)} · ${statusLabel(log.status)}"
    }

    fun statusLabel(status: String): String {
        return when (status) {
            AutomationRunLogStatus.SUCCESS -> "성공"
            AutomationRunLogStatus.STOPPED -> "중지"
            AutomationRunLogStatus.FAILURE -> "실패"
            else -> status
        }
    }

    private fun formatTime(timeMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(timeMillis))
    }
}

