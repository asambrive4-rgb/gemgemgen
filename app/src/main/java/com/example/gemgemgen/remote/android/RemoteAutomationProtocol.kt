package com.example.gemgemgen.remote.android

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.remote.domain.RemoteAutomationRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal sealed interface RemoteProtocolMessage {
    data class PairRequest(val senderId: String, val pairingCode: String) : RemoteProtocolMessage
    data class PairResult(
        val success: Boolean,
        val receiverId: String = "",
        val receiverName: String = "",
        val token: String = "",
        val message: String = ""
    ) : RemoteProtocolMessage
    data class RunRequest(
        val senderId: String,
        val token: String,
        val request: RemoteAutomationRequest
    ) : RemoteProtocolMessage
    data class CancelRequest(
        val senderId: String,
        val token: String,
        val requestId: String
    ) : RemoteProtocolMessage
    data class StateUpdate(
        val requestId: String,
        val state: AutomationRunState
    ) : RemoteProtocolMessage
}

internal object RemoteAutomationProtocol {
    const val SERVICE_TYPE = "_gemgemgen._tcp."
    const val MAX_MESSAGE_CHARS = 1_000_000
    const val SOCKET_TIMEOUT_MS = 10_000

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(message: RemoteProtocolMessage): String {
        return when (message) {
            is RemoteProtocolMessage.PairRequest -> buildJsonObject {
                put("type", "pair")
                put("senderId", message.senderId)
                put("code", message.pairingCode)
            }
            is RemoteProtocolMessage.PairResult -> buildJsonObject {
                put("type", "pairResult")
                put("success", message.success)
                put("receiverId", message.receiverId)
                put("receiverName", message.receiverName)
                put("token", message.token)
                put("message", message.message)
            }
            is RemoteProtocolMessage.RunRequest -> buildJsonObject {
                put("type", "run")
                put("senderId", message.senderId)
                put("token", message.token)
                put("requestId", message.request.requestId)
                put("prompt", message.request.promptTemplate)
                put("repeatCount", message.request.repeatCountText)
                put("targetApp", message.request.targetApp.storageValue)
            }
            is RemoteProtocolMessage.CancelRequest -> buildJsonObject {
                put("type", "cancel")
                put("senderId", message.senderId)
                put("token", message.token)
                put("requestId", message.requestId)
            }
            is RemoteProtocolMessage.StateUpdate -> buildJsonObject {
                put("type", "state")
                put("requestId", message.requestId)
                putState(message.state)
            }
        }.toString()
    }

    fun decode(text: String): RemoteProtocolMessage? {
        if (text.length > MAX_MESSAGE_CHARS) return null
        val value = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return null
        return when (value.string("type")) {
            "pair" -> RemoteProtocolMessage.PairRequest(
                senderId = value.string("senderId"),
                pairingCode = value.string("code")
            )
            "pairResult" -> RemoteProtocolMessage.PairResult(
                success = value.boolean("success"),
                receiverId = value.string("receiverId"),
                receiverName = value.string("receiverName"),
                token = value.string("token"),
                message = value.string("message")
            )
            "run" -> RemoteProtocolMessage.RunRequest(
                senderId = value.string("senderId"),
                token = value.string("token"),
                request = RemoteAutomationRequest(
                    requestId = value.string("requestId"),
                    promptTemplate = value.string("prompt"),
                    repeatCountText = value.string("repeatCount"),
                    targetApp = AutomationTargetApp.fromStorageValue(value.string("targetApp"))
                )
            )
            "cancel" -> RemoteProtocolMessage.CancelRequest(
                senderId = value.string("senderId"),
                token = value.string("token"),
                requestId = value.string("requestId")
            )
            "state" -> RemoteProtocolMessage.StateUpdate(
                requestId = value.string("requestId"),
                state = value.toRunState()
            )
            else -> null
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putState(state: AutomationRunState) {
        when (state) {
            AutomationRunState.Idle -> put("state", "idle")
            is AutomationRunState.Running -> {
                put("state", "running")
                put("step", state.step)
                state.currentIndex?.let { put("currentIndex", it) }
                state.totalCount?.let { put("totalCount", it) }
            }
            AutomationRunState.Success -> put("state", "success")
            AutomationRunState.Stopped -> put("state", "stopped")
            is AutomationRunState.Failure -> {
                put("state", "failure")
                put("message", state.message)
            }
        }
    }

    private fun JsonObject.toRunState(): AutomationRunState {
        return when (string("state")) {
            "running" -> AutomationRunState.Running(
                step = string("step"),
                currentIndex = intOrNull("currentIndex"),
                totalCount = intOrNull("totalCount")
            )
            "success" -> AutomationRunState.Success
            "stopped" -> AutomationRunState.Stopped
            "failure" -> AutomationRunState.Failure(string("message"))
            else -> AutomationRunState.Idle
        }
    }

    private fun JsonObject.string(key: String): String {
        return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun JsonObject.boolean(key: String): Boolean {
        return string(key).toBooleanStrictOrNull() ?: false
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        return this[key]?.jsonPrimitive?.intOrNull
    }
}
