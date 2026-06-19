package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationRunLog
import java.util.Base64

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
    }
}

interface RunLogStorage {
    fun read(): String
    fun write(value: String)
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
                encodeText(log.markerStatus),
                encodeText(log.targetApp)
            ).joinToString(FIELD_SEPARATOR)
        }
    }

    fun decode(raw: String): List<AutomationRunLog> {
        if (raw.isBlank()) return emptyList()

        return raw.lineSequence().mapNotNull { line ->
            val fields = line.split(FIELD_SEPARATOR)
            if (fields.size != 6 && fields.size != 11 && fields.size != 12) {
                return@mapNotNull null
            }

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
                markerStatus = fields.getOrNull(10)?.let(::decodeText).orEmpty(),
                targetApp = fields.getOrNull(11)?.let(::decodeText).orEmpty()
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

