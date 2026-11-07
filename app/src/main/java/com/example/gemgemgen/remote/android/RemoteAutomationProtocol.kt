// 역할: 원격 기기 간 주고받는 무인증 메시지 형식(실행, 취소, 횟수 변경, 메모리 정리, Gemini 계정 전환 포함)과 패킷 프로토콜을 정의합니다.
package com.example.gemgemgen.remote.android

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.remote.domain.RemoteAutomationRequest
import com.example.gemgemgen.wildcard.domain.WildcardSet
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator

internal sealed interface RemoteProtocolMessage {
    data class RunRequest(
        val senderId: String,
        val request: RemoteAutomationRequest
    ) : RemoteProtocolMessage
    data class CancelRequest(
        val senderId: String,
        val requestId: String
    ) : RemoteProtocolMessage
    data class UpdateRepeatCountRequest(
        val senderId: String,
        val requestId: String,
        val repeatCount: Int
    ) : RemoteProtocolMessage
    data class DisconnectRequest(
        val senderId: String
    ) : RemoteProtocolMessage
    data class DisconnectResult(
        val success: Boolean,
        val message: String = ""
    ) : RemoteProtocolMessage
    data class StateUpdate(
        val requestId: String,
        val state: AutomationRunState
    ) : RemoteProtocolMessage
    data class CleanMemoryRequest(
        val senderId: String
    ) : RemoteProtocolMessage
    data class CleanMemoryResult(
        val success: Boolean,
        val message: String = ""
    ) : RemoteProtocolMessage
    data class SwitchAccountRequest(
        val senderId: String,
        val email: String,
        val alias: String
    ) : RemoteProtocolMessage
    data class SwitchAccountResult(
        val success: Boolean,
        val message: String = ""
    ) : RemoteProtocolMessage
}

@OptIn(ExperimentalSerializationApi::class)
private val protocolJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
private sealed interface ProtocolPacketDto {
    fun toDomain(): RemoteProtocolMessage

    @Serializable
    @SerialName("run")
    data class RunReq(
        val senderId: String = "",
        val requestId: String = "",
        val prompt: String = "",
        val repeatCount: String = "",
        val targetApp: String = "",
        val flowImageCount: Int = AppDefaults.DEFAULT_FLOW_IMAGE_COUNT,
        val wildcards: List<WildcardDto> = emptyList()
    ) : ProtocolPacketDto {
        override fun toDomain(): RemoteProtocolMessage =
            RemoteProtocolMessage.RunRequest(
                senderId = senderId,
                request = RemoteAutomationRequest(
                    requestId = requestId,
                    promptTemplate = prompt,
                    repeatCountText = repeatCount,
                    targetApp = AutomationTargetApp.fromStorageValue(targetApp),
                    flowImageCount = flowImageCount,
                    wildcards = wildcards.map { it.toDomain() }
                )
            )

        companion object {
            fun from(req: RemoteProtocolMessage.RunRequest): RunReq = RunReq(
                senderId = req.senderId,
                requestId = req.request.requestId,
                prompt = req.request.promptTemplate,
                repeatCount = req.request.repeatCountText,
                targetApp = req.request.targetApp.storageValue,
                flowImageCount = req.request.flowImageCount,
                wildcards = req.request.wildcards.map { WildcardDto.from(it) }
            )
        }
    }

    @Serializable
    @SerialName("cancel")
    data class CancelReq(
        val senderId: String = "",
        val requestId: String = ""
    ) : ProtocolPacketDto {
        override fun toDomain(): RemoteProtocolMessage =
            RemoteProtocolMessage.CancelRequest(
                senderId = senderId,
                requestId = requestId
            )
    }

    @Serializable
    @SerialName("updateRepeatCount")
    data class UpdateRepeatCountReq(
        val senderId: String = "",
        val requestId: String = "",
        val repeatCount: Int
    ) : ProtocolPacketDto {
        override fun toDomain(): RemoteProtocolMessage =
            RemoteProtocolMessage.UpdateRepeatCountRequest(
                senderId = senderId,
                requestId = requestId,
                repeatCount = repeatCount
            )
    }

    @Serializable
    @SerialName("disconnect")
    data class DisconnectReq(
        val senderId: String = ""
    ) : ProtocolPacketDto {
        override fun toDomain(): RemoteProtocolMessage =
            RemoteProtocolMessage.DisconnectRequest(
                senderId = senderId
            )
    }

    @Serializable
    @SerialName("disconnectResult")
    data class DisconnectRes(
        val success: Boolean = false,
        val message: String = ""
    ) : ProtocolPacketDto {
        override fun toDomain(): RemoteProtocolMessage =
            RemoteProtocolMessage.DisconnectResult(
                success = success,
                message = message
            )
    }

    @Serializable
    @SerialName("state")
    data class StateUpd(
        val requestId: String = "",
        val state: String = "idle",
        val step: String = "",
        val currentIndex: Int? = null,
        val totalCount: Int? = null,
        val message: String = ""
    ) : ProtocolPacketDto {
        override fun toDomain(): RemoteProtocolMessage {
            val runState = when (state) {
                "running" -> AutomationRunState.Running(
                    step = step,
                    currentIndex = currentIndex,
                    totalCount = totalCount
                )
                "success" -> AutomationRunState.Success
                "stopped" -> AutomationRunState.Stopped
                "failure" -> AutomationRunState.Failure(message)
                else -> AutomationRunState.Idle
            }
            return RemoteProtocolMessage.StateUpdate(requestId = requestId, state = runState)
        }

        companion object {
            fun from(requestId: String, runState: AutomationRunState): StateUpd = when (runState) {
                AutomationRunState.Idle -> StateUpd(requestId = requestId, state = "idle")
                is AutomationRunState.Running -> StateUpd(
                    requestId = requestId,
                    state = "running",
                    step = runState.step,
                    currentIndex = runState.currentIndex,
                    totalCount = runState.totalCount
                )
                AutomationRunState.Success -> StateUpd(requestId = requestId, state = "success")
                AutomationRunState.Stopped -> StateUpd(requestId = requestId, state = "stopped")
                is AutomationRunState.Failure -> StateUpd(
                    requestId = requestId,
                    state = "failure",
                    message = runState.message
                )
            }
        }
    }

    @Serializable
    @SerialName("cleanMemory")
    data class CleanMemReq(
        val senderId: String = ""
    ) : ProtocolPacketDto {
        override fun toDomain(): RemoteProtocolMessage =
            RemoteProtocolMessage.CleanMemoryRequest(
                senderId = senderId
            )
    }

    @Serializable
    @SerialName("cleanMemoryResult")
    data class CleanMemRes(
        val success: Boolean = false,
        val message: String = ""
    ) : ProtocolPacketDto {
        override fun toDomain(): RemoteProtocolMessage =
            RemoteProtocolMessage.CleanMemoryResult(
                success = success,
                message = message
            )
    }

    @Serializable
    @SerialName("switchAccount")
    data class SwitchAccReq(
        val senderId: String = "",
        val email: String = "",
        val alias: String = ""
    ) : ProtocolPacketDto {
        override fun toDomain(): RemoteProtocolMessage =
            RemoteProtocolMessage.SwitchAccountRequest(
                senderId = senderId,
                email = email,
                alias = alias
            )
    }

    @Serializable
    @SerialName("switchAccountResult")
    data class SwitchAccRes(
        val success: Boolean = false,
        val message: String = ""
    ) : ProtocolPacketDto {
        override fun toDomain(): RemoteProtocolMessage =
            RemoteProtocolMessage.SwitchAccountResult(
                success = success,
                message = message
            )
    }

    companion object {
        fun fromDomain(message: RemoteProtocolMessage): ProtocolPacketDto = when (message) {
            is RemoteProtocolMessage.RunRequest -> RunReq.from(message)
            is RemoteProtocolMessage.CancelRequest -> CancelReq(
                senderId = message.senderId,
                requestId = message.requestId
            )
            is RemoteProtocolMessage.UpdateRepeatCountRequest -> UpdateRepeatCountReq(
                senderId = message.senderId,
                requestId = message.requestId,
                repeatCount = message.repeatCount
            )
            is RemoteProtocolMessage.DisconnectRequest -> DisconnectReq(
                senderId = message.senderId
            )
            is RemoteProtocolMessage.DisconnectResult -> DisconnectRes(
                success = message.success,
                message = message.message
            )
            is RemoteProtocolMessage.StateUpdate -> StateUpd.from(
                requestId = message.requestId,
                runState = message.state
            )
            is RemoteProtocolMessage.CleanMemoryRequest -> CleanMemReq(
                senderId = message.senderId
            )
            is RemoteProtocolMessage.CleanMemoryResult -> CleanMemRes(
                success = message.success,
                message = message.message
            )
            is RemoteProtocolMessage.SwitchAccountRequest -> SwitchAccReq(
                senderId = message.senderId,
                email = message.email,
                alias = message.alias
            )
            is RemoteProtocolMessage.SwitchAccountResult -> SwitchAccRes(
                success = message.success,
                message = message.message
            )
        }
    }
}

@Serializable
private data class WildcardDto(
    val token: String = "",
    val fileName: String = "",
    val items: List<String> = emptyList()
) {
    fun toDomain(): WildcardSet = WildcardSet(
        token = token,
        fileName = fileName,
        items = items
    )

    companion object {
        fun from(set: WildcardSet): WildcardDto = WildcardDto(
            token = set.token,
            fileName = set.fileName,
            items = set.items
        )
    }
}

internal object RemoteAutomationProtocol {
    const val SERVICE_TYPE = "_gemgemgen._tcp."
    const val MAX_MESSAGE_CHARS = 1_000_000
    const val SOCKET_TIMEOUT_MS = 10_000
    const val STREAM_SOCKET_TIMEOUT_MS = 30_000

    fun encode(message: RemoteProtocolMessage): String {
        val dto = ProtocolPacketDto.fromDomain(message)
        return protocolJson.encodeToString(dto)
    }

    fun decode(text: String): RemoteProtocolMessage? {
        if (text.length > MAX_MESSAGE_CHARS) return null
        return runCatching {
            protocolJson.decodeFromString<ProtocolPacketDto>(text).toDomain()
        }.getOrNull()
    }
}
