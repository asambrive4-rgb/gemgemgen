package com.example.gemgemgen

import android.content.Context
import android.content.SharedPreferences
import java.util.Base64

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
    val markerStatus: String = ""
)

class RunLogger(
    private val storage: RunLogStorage,
    private val maxEntries: Int = MAX_ENTRIES
) {
    fun append(log: AutomationRunLog) {
        val logs = (listOf(log) + loadRecent()).take(maxEntries)
        storage.write(RunLogCodec.encode(logs))
    }

    fun loadRecent(): List<AutomationRunLog> {
        return RunLogCodec.decode(storage.read())
    }

    companion object {
        private const val MAX_ENTRIES = 10

        fun android(context: Context): RunLogger {
            return RunLogger(
                SharedPreferencesRunLogStorage(
                    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                )
            )
        }
    }
}

interface RunLogStorage {
    fun read(): String
    fun write(value: String)
}

object AutomationRunLogStatus {
    const val SUCCESS = "success"
    const val FAILURE = "failure"
    const val STOPPED = "stopped"

    fun fromState(state: AutomationUiState): String {
        return when (state) {
            AutomationUiState.Success -> SUCCESS
            AutomationUiState.Stopped -> STOPPED
            is AutomationUiState.Failure -> FAILURE
            AutomationUiState.Idle,
            is AutomationUiState.Running -> FAILURE
        }
    }
}

private class SharedPreferencesRunLogStorage(
    private val preferences: SharedPreferences
) : RunLogStorage {
    override fun read(): String {
        return preferences.getString(KEY_LOGS, "").orEmpty()
    }

    override fun write(value: String) {
        preferences.edit().putString(KEY_LOGS, value).apply()
    }
}

private object RunLogCodec {
    private const val ENTRY_SEPARATOR = "\n"
    private const val FIELD_SEPARATOR = "\t"

    fun encode(logs: List<AutomationRunLog>): String {
        return logs.joinToString(ENTRY_SEPARATOR) { log ->
            listOf(
                log.startedAtMillis.toString(),
                log.finishedAtMillis.toString(),
                encodeText(log.status),
                encodeText(log.lastStep),
                encodeText(log.message),
                encodeText(log.imeRestoreMessage),
                log.repeatCount.toString(),
                log.completedCount.toString(),
                log.successCount.toString(),
                log.failureCount.toString(),
                encodeText(log.markerStatus)
            ).joinToString(FIELD_SEPARATOR)
        }
    }

    fun decode(raw: String): List<AutomationRunLog> {
        if (raw.isBlank()) return emptyList()

        return raw.lineSequence().mapNotNull { line ->
            val fields = line.split(FIELD_SEPARATOR)
            if (fields.size != 6 && fields.size != 11) return@mapNotNull null

            val startedAtMillis = fields[0].toLongOrNull() ?: return@mapNotNull null
            val finishedAtMillis = fields[1].toLongOrNull() ?: return@mapNotNull null
            AutomationRunLog(
                startedAtMillis = startedAtMillis,
                finishedAtMillis = finishedAtMillis,
                status = decodeText(fields[2]) ?: return@mapNotNull null,
                lastStep = decodeText(fields[3]) ?: return@mapNotNull null,
                message = decodeText(fields[4]) ?: return@mapNotNull null,
                imeRestoreMessage = decodeText(fields[5]) ?: return@mapNotNull null,
                repeatCount = fields.getOrNull(6)?.toIntOrNull() ?: 0,
                completedCount = fields.getOrNull(7)?.toIntOrNull() ?: 0,
                successCount = fields.getOrNull(8)?.toIntOrNull() ?: 0,
                failureCount = fields.getOrNull(9)?.toIntOrNull() ?: 0,
                markerStatus = fields.getOrNull(10)?.let(::decodeText).orEmpty()
            )
        }.toList()
    }

    private fun encodeText(value: String): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))
    }

    private fun decodeText(value: String): String? {
        return try {
            String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

private const val PREFERENCES_NAME = "automation_run_logs"
private const val KEY_LOGS = "logs"
