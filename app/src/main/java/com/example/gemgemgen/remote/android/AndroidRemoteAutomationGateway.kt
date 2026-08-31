package com.example.gemgemgen.remote.android

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.core.content.ContextCompat
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.isTerminal
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.domain.RemoteAutomationRequest
import com.example.gemgemgen.remote.usecase.RemoteAutomationGateway
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidRemoteAutomationGateway(context: Context) : RemoteAutomationGateway {
    private val appContext = context.applicationContext
    private val store = RemoteAutomationStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val discovery = RemoteServiceDiscovery(
        context = appContext,
        onResolved = ::onServiceResolved,
        onLost = ::onServiceLost,
        onError = ::onDiscoveryError
    )
    private val activeRequestLock = Any()
    @Volatile private var endpoint: RemoteEndpoint? = null
    @Volatile private var activeRequestId: String? = null
    @Volatile private var activeSocket: Socket? = null

    override val status = RemoteAutomationStateHub.status

    init {
        selectMode(store.mode())
    }

    override fun selectMode(mode: AutomationMode) {
        store.saveMode(mode)
        RemoteAutomationStateHub.update {
            it.copy(
                mode = mode,
                isReceiverRunning = false,
                receiverPairingCode = "",
                discoveredDeviceName = "",
                isPaired = false,
                connectionMessage = when (mode) {
                    AutomationMode.NORMAL -> ""
                    AutomationMode.SENDER -> "S25 FE를 찾는 중입니다."
                    AutomationMode.RECEIVER -> ""
                },
                message = when (mode) {
                    AutomationMode.RECEIVER -> "수신 대기를 시작하는 중입니다."
                    else -> ""
                },
                automationState = AutomationRunState.Idle
            )
        }

        when (mode) {
            AutomationMode.NORMAL -> {
                discovery.stop()
                endpoint = null
                appContext.stopService(RemoteAutomationReceiverService.intent(appContext))
            }
            AutomationMode.SENDER -> {
                appContext.stopService(RemoteAutomationReceiverService.intent(appContext))
                discovery.start()
            }
            AutomationMode.RECEIVER -> {
                discovery.stop()
                endpoint = null
                ContextCompat.startForegroundService(
                    appContext,
                    RemoteAutomationReceiverService.intent(appContext)
                )
            }
        }
    }

    override suspend fun pair(pairingCode: String): RemoteActionResult = withContext(Dispatchers.IO) {
        val target = endpoint
            ?: return@withContext RemoteActionResult.Failure("연결할 수신 기기를 찾지 못했습니다.")
        val result = runCatching {
            openSocket(target).use { socket ->
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                writer.println(
                    RemoteAutomationProtocol.encode(
                        RemoteProtocolMessage.PairRequest(
                            senderId = store.installationId(),
                            pairingCode = pairingCode
                        )
                    )
                )
                RemoteAutomationProtocol.decode(reader.readLine().orEmpty())
                    as? RemoteProtocolMessage.PairResult
            }
        }.getOrNull()

        if (result?.success == true && result.token.isNotBlank()) {
            store.savePairedReceiver(
                receiverId = result.receiverId,
                receiverName = result.receiverName,
                token = result.token
            )
            RemoteAutomationStateHub.update {
                it.copy(
                    discoveredDeviceName = result.receiverName,
                    isPaired = true,
                    connectionMessage = "${result.receiverName} 연결됨"
                )
            }
            RemoteActionResult.Success
        } else {
            val message = result?.message?.takeIf(String::isNotBlank)
                ?: "S25 FE에 연결하지 못했습니다."
            RemoteAutomationStateHub.update {
                it.copy(isPaired = false, connectionMessage = message)
            }
            RemoteActionResult.Failure(message)
        }
    }

    override suspend fun send(
        request: RemoteAutomationRequest,
        onStateChange: (AutomationRunState) -> Unit
    ) = withContext(Dispatchers.IO) {
        val target = endpoint
        val paired = store.pairedReceiver()
        if (target == null || paired == null || paired.receiverId != target.receiverId) {
            val failure = AutomationRunState.Failure("연결된 수신 기기를 찾지 못했습니다.")
            RemoteAutomationStateHub.update { it.copy(automationState = failure) }
            onStateChange(failure)
            return@withContext
        }

        beginRequest(request.requestId)
        try {
            val socket = openSocket(target, timeoutMillis = 0)
            if (!attachSocket(request.requestId, socket)) {
                socket.close()
                return@withContext
            }
            socket.use {
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                writer.println(
                    RemoteAutomationProtocol.encode(
                        RemoteProtocolMessage.RunRequest(
                            senderId = store.installationId(),
                            token = paired.token,
                            request = request
                        )
                    )
                )

                var receivedTerminal = false
                while (true) {
                    val line = reader.readLine() ?: break
                    val update = RemoteAutomationProtocol.decode(line)
                        as? RemoteProtocolMessage.StateUpdate
                        ?: continue
                    if (update.requestId != request.requestId) continue
                    emitState(request.requestId, update.state, onStateChange)
                    if (update.state.isTerminal()) {
                        receivedTerminal = true
                        break
                    }
                }
                if (!receivedTerminal && isCurrentRequest(request.requestId)) {
                    emitState(
                        request.requestId,
                        AutomationRunState.Failure("S25 FE가 상태 전송을 종료했습니다."),
                        onStateChange
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrentRequest(request.requestId)) {
                emitState(
                    request.requestId,
                    AutomationRunState.Failure(
                        error.message?.let { "S25 FE 연결이 끊어졌습니다: $it" }
                            ?: "S25 FE 연결이 끊어졌습니다."
                    ),
                    onStateChange
                )
            }
        } finally {
            finishRequest(request.requestId)
        }
    }

    override fun forceStop(requestId: String?) {
        val stoppedRequest = synchronized(activeRequestLock) {
            val currentRequestId = activeRequestId
            if (requestId != null && currentRequestId != requestId) {
                null
            } else {
                activeRequestId = null
                val socket = activeSocket
                activeSocket = null
                socket?.let { runCatching { it.close() } }
                currentRequestId ?: requestId
            }
        }
        RemoteAutomationStateHub.update {
            it.copy(automationState = AutomationRunState.Stopped)
        }
        val cancelRequestId = stoppedRequest ?: return
        val target = endpoint ?: return
        val paired = store.pairedReceiver() ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                openSocket(target).use { socket ->
                    PrintWriter(socket.getOutputStream(), true).println(
                        RemoteAutomationProtocol.encode(
                            RemoteProtocolMessage.CancelRequest(
                                senderId = store.installationId(),
                                token = paired.token,
                                requestId = cancelRequestId
                            )
                        )
                    )
                }
            }
        }
    }

    private fun emitState(
        requestId: String,
        state: AutomationRunState,
        onStateChange: (AutomationRunState) -> Unit
    ) {
        if (!isCurrentRequest(requestId)) return
        RemoteAutomationStateHub.update { current ->
            current.copy(automationState = state)
        }
        onStateChange(state)
    }

    private fun beginRequest(requestId: String) {
        val previousSocket = synchronized(activeRequestLock) {
            val socket = activeSocket
            activeRequestId = requestId
            activeSocket = null
            socket
        }
        previousSocket?.let { runCatching { it.close() } }
    }

    private fun attachSocket(requestId: String, socket: Socket): Boolean {
        return synchronized(activeRequestLock) {
            if (activeRequestId != requestId) {
                false
            } else {
                activeSocket = socket
                true
            }
        }
    }

    private fun isCurrentRequest(requestId: String): Boolean {
        return activeRequestId == requestId
    }

    private fun finishRequest(requestId: String) {
        synchronized(activeRequestLock) {
            if (activeRequestId == requestId) {
                activeRequestId = null
                activeSocket = null
            }
        }
    }

    private fun onServiceResolved(resolved: RemoteEndpoint) {
        if (resolved.receiverId == store.installationId()) return
        val paired = store.pairedReceiver()
        endpoint = resolved
        val isPaired = paired?.receiverId == resolved.receiverId
        RemoteAutomationStateHub.update {
            it.copy(
                discoveredDeviceName = resolved.name,
                isPaired = isPaired,
                connectionMessage = if (isPaired) {
                    "${resolved.name} 연결됨"
                } else {
                    "${resolved.name} 발견됨 · 4자리 번호로 연결해주세요."
                }
            )
        }
    }

    private fun onServiceLost(serviceName: String) {
        val current = endpoint ?: return
        if (current.name != serviceName) return
        endpoint = null
        RemoteAutomationStateHub.update {
            it.copy(
                discoveredDeviceName = "",
                isPaired = false,
                connectionMessage = "S25 FE를 찾지 못했습니다. 수신 모드와 Wi-Fi를 확인해주세요."
            )
        }
    }

    private fun onDiscoveryError(message: String) {
        RemoteAutomationStateHub.update { it.copy(connectionMessage = message) }
    }

    private fun openSocket(
        target: RemoteEndpoint,
        timeoutMillis: Int = RemoteAutomationProtocol.SOCKET_TIMEOUT_MS
    ): Socket {
        return Socket().apply {
            connect(
                InetSocketAddress(target.address, target.port),
                RemoteAutomationProtocol.SOCKET_TIMEOUT_MS
            )
            soTimeout = timeoutMillis
        }
    }
}

internal data class RemoteEndpoint(
    val receiverId: String,
    val name: String,
    val address: InetAddress,
    val port: Int
)

private class RemoteServiceDiscovery(
    context: Context,
    private val onResolved: (RemoteEndpoint) -> Unit,
    private val onLost: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private var listener: NsdManager.DiscoveryListener? = null
    private val resolvingNames = mutableSetOf<String>()

    fun start() {
        if (listener != null) return
        val created = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != RemoteAutomationProtocol.SERVICE_TYPE) return
                synchronized(resolvingNames) {
                    if (!resolvingNames.add(serviceInfo.serviceName)) return
                }
                @Suppress("DEPRECATION")
                nsdManager.resolveService(
                    serviceInfo,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            synchronized(resolvingNames) { resolvingNames.remove(serviceInfo.serviceName) }
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            synchronized(resolvingNames) { resolvingNames.remove(serviceInfo.serviceName) }
                            val receiverId = serviceInfo.attributes[ATTRIBUTE_RECEIVER_ID]
                                ?.toString(Charsets.UTF_8)
                                .orEmpty()
                            @Suppress("DEPRECATION")
                            val address = serviceInfo.host ?: return
                            if (receiverId.isBlank() || serviceInfo.port <= 0) return
                            onResolved(
                                RemoteEndpoint(
                                    receiverId = receiverId,
                                    name = serviceInfo.serviceName,
                                    address = address,
                                    port = serviceInfo.port
                                )
                            )
                        }
                    }
                )
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                onLost(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                listener = null
                onError("같은 Wi-Fi의 수신 기기를 검색하지 못했습니다. ($errorCode)")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                listener = null
            }
        }
        listener = created
        runCatching {
            nsdManager.discoverServices(
                RemoteAutomationProtocol.SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                created
            )
        }.onFailure {
            listener = null
            onError("같은 Wi-Fi의 수신 기기를 검색하지 못했습니다.")
        }
    }

    fun stop() {
        val current = listener ?: return
        listener = null
        runCatching { nsdManager.stopServiceDiscovery(current) }
        synchronized(resolvingNames) { resolvingNames.clear() }
    }

    companion object {
        const val ATTRIBUTE_RECEIVER_ID = "receiverId"
    }
}
