// 역할: 포그라운드 서비스로 상주하며 NSD 수신 등록 및 동일 와이파이 내 원격 송신 기기의 명령(자동화 선점 실행, 취소, 메모리 정리, 계정 전환, 새로고침 복구)을 무인증으로 수신·조율합니다.
package com.example.gemgemgen.remote.android

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.gemgemgen.R
import com.example.gemgemgen.automation.android.AndroidAutomationRuntimeProvider
import com.example.gemgemgen.automation.android.AndroidGeminiAccountSwitcher
import com.example.gemgemgen.automation.android.AndroidMemoryCleanupGateway
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.usecase.MemoryCleanupResult
import com.example.gemgemgen.automation.usecase.SwitchGeminiAccountResult
import com.example.gemgemgen.automation.domain.isTerminal
import com.example.gemgemgen.environment.android.AndroidEnvironmentGateway
import com.example.gemgemgen.automation.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteExecutionConditions
import com.example.gemgemgen.remote.usecase.ExecuteRemoteAutomationUseCase

import com.example.gemgemgen.ui.MainActivity
import com.example.gemgemgen.usage.android.DataStoreMultiGeminiUsageDataSource
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RemoteAutomationReceiverService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var store: RemoteAutomationStore
    private lateinit var executeRemoteAutomation: ExecuteRemoteAutomationUseCase
    private lateinit var environmentGateway: AndroidEnvironmentGateway
    private var serverSocket: ServerSocket? = null
    private val nsdLock = Any()
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var isRegistering = false
    private var pendingRegisterPort: Int? = null
    private val sessionMutex = Mutex()
    @Volatile private var activeSession: ReceiverSession? = null

    override fun onCreate() {
        super.onCreate()
        store = RemoteAutomationStore(this)
        executeRemoteAutomation = ExecuteRemoteAutomationUseCase(
            automation = AndroidAutomationRuntimeProvider.get(this)
        )
        environmentGateway = AndroidEnvironmentGateway(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        val isReady = !store.isUserDisconnected()
        RemoteAutomationStateHub.update {
            it.copy(
                mode = AutomationMode.RECEIVER,
                isReceiverRunning = true,
                isPaired = isReady,
                message = receiverReadyMessage()
            )
        }
        startServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                store.saveMode(AutomationMode.NORMAL)
                stopSelf()
            }
            ACTION_DISCONNECT -> {
                store.saveUserDisconnected(true)
                RemoteAutomationStateHub.update {
                    it.copy(
                        isPaired = false,
                        message = "원격 연결을 끊었습니다."
                    )
                }
                serverSocket?.localPort?.let(::reregisterService)
            }
            ACTION_REREGISTER -> {
                store.saveUserDisconnected(false)
                serviceScope.launch {
                    sessionMutex.withLock {
                        val session = activeSession
                        activeSession = null
                        session?.executionJob?.cancelAndJoin()
                        withContext(Dispatchers.Main.immediate) {
                            executeRemoteAutomation.cancelAndAwait()
                        }
                    }
                    val port = serverSocket?.localPort
                    if (port != null && port > 0) {
                        reregisterService(port)
                    } else {
                        startServer()
                    }
                    RemoteAutomationStateHub.update {
                        it.copy(
                            isReceiverRunning = true,
                            isPaired = true,
                            automationState = AutomationRunState.Idle,
                            message = receiverReadyMessage()
                        )
                    }
                }
            }
            else -> {
                val port = serverSocket?.localPort
                if (port != null && port > 0) {
                    val isReady = !store.isUserDisconnected()
                    RemoteAutomationStateHub.update {
                        it.copy(
                            mode = AutomationMode.RECEIVER,
                            isReceiverRunning = true,
                            isPaired = isReady,
                            message = receiverReadyMessage()
                        )
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        activeSession?.executionJob?.cancel()
        executeRemoteAutomation.cancel()
        unregisterService()
        runCatching { serverSocket?.close() }
        serviceScope.cancel()
        RemoteAutomationStateHub.update {
            it.copy(
                mode = store.mode(),
                isReceiverRunning = false,
                isPaired = false,
                automationState = AutomationRunState.Idle,
                message = if (store.mode() == AutomationMode.RECEIVER) {
                    "수신 대기가 중단되었습니다."
                } else {
                    ""
                }
            )
        }
        super.onDestroy()
    }

    private fun startServer() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(0).also {
                    it.reuseAddress = true
                    serverSocket = it
                }
                withContext(Dispatchers.Main.immediate) {
                    registerService(server.localPort)
                }
                while (!server.isClosed) {
                    val socket = server.accept()
                    serviceScope.launch(Dispatchers.IO) {
                        runCatching { handleClient(socket) }
                    }
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (error: Exception) {
                if (serverSocket?.isClosed == true) return@launch
                RemoteAutomationStateHub.update {
                    it.copy(message = "수신 대기를 시작하지 못했습니다: ${error.message ?: "네트워크 오류"}")
                }
                stopSelf()
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        runCatching {
            socket.use {
                socket.soTimeout = RemoteAutomationProtocol.SOCKET_TIMEOUT_MS
                if (!isLocalAddress(socket)) return
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)
                val line = reader.readLine() ?: return
                when (val message = RemoteAutomationProtocol.decode(line)) {
                    is RemoteProtocolMessage.DisconnectRequest -> handleDisconnect(writer)
                    is RemoteProtocolMessage.RunRequest -> handleRun(message, writer, socket)
                    is RemoteProtocolMessage.CancelRequest -> handleCancel()
                    is RemoteProtocolMessage.UpdateRepeatCountRequest -> handleUpdateRepeatCount(message)
                    is RemoteProtocolMessage.CleanMemoryRequest -> handleCleanMemory(writer)
                    is RemoteProtocolMessage.SwitchAccountRequest -> handleSwitchAccount(message, writer)
                    else -> Unit
                }
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
        }
    }

    private fun markConnectedFromSender() {
        val wasDisconnected = store.isUserDisconnected()
        if (wasDisconnected) {
            store.saveUserDisconnected(false)
            serverSocket?.localPort?.let(::reregisterService)
        }
        if (!RemoteAutomationStateHub.status.value.isPaired) {
            RemoteAutomationStateHub.update {
                it.copy(isPaired = true, message = receiverReadyMessage())
            }
        }
    }

    private fun handleDisconnect(writer: PrintWriter) {
        store.saveUserDisconnected(true)
        RemoteAutomationStateHub.update {
            it.copy(isPaired = false, message = "송신 기기와의 연결이 끊어졌습니다.")
        }
        writer.println(
            RemoteAutomationProtocol.encode(
                RemoteProtocolMessage.DisconnectResult(success = true)
            )
        )
        serverSocket?.localPort?.let(::reregisterService)
    }

    private suspend fun handleRun(
        message: RemoteProtocolMessage.RunRequest,
        writer: PrintWriter,
        socket: Socket
    ) {
        markConnectedFromSender()
        val requestId = message.request.requestId
        socket.soTimeout = 0
        val states = Channel<AutomationRunState>(Channel.UNLIMITED)
        val executionJob = sessionMutex.withLock {
            val previousSession = activeSession
            previousSession?.executionJob?.cancelAndJoin()
            withContext(Dispatchers.Main.immediate) {
                executeRemoteAutomation.cancelAndAwait()
            }

            RemoteAutomationStateHub.update {
                it.copy(
                    isPaired = true,
                    automationState = AutomationRunState.Running("새 요청을 수락하는 중"),
                    message = "새 요청을 수락하는 중"
                )
            }

            val job = serviceScope.launch(
                context = Dispatchers.Main.immediate,
                start = CoroutineStart.LAZY
            ) {
                try {
                    val conditions = withContext(Dispatchers.IO) {
                        executionConditions(message.request.targetApp)
                    }
                    executeRemoteAutomation.execute(
                        request = message.request,
                        conditions = conditions,
                        onStateChange = { states.trySend(it) }
                    )
                } catch (_: CancellationException) {
                    states.trySend(AutomationRunState.Stopped)
                } catch (error: Exception) {
                    states.trySend(
                        AutomationRunState.Failure(
                            error.message ?: "S25 FE에서 자동화를 시작하지 못했습니다."
                        )
                    )
                }
            }
            activeSession = ReceiverSession(requestId, job)
            job.start()
            job
        }
        val disconnectJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val input = socket.getInputStream()
                while (true) {
                    val byte = input.read()
                    if (byte == -1) break
                }
            } catch (_: Exception) {
            } finally {
                states.close()
            }
        }
        try {
            while (true) {
                val result = states.receiveCatching()
                if (result.isClosed) {
                    break
                }
                val state = result.getOrNull() ?: break
                if (activeSession?.requestId == requestId) {
                    RemoteAutomationStateHub.update {
                        it.copy(automationState = state, message = stateMessage(state))
                    }
                }
                val writeOk = writeState(writer, requestId, state)
                if (!writeOk) {
                    RemoteAutomationStateHub.update {
                        it.copy(message = "송신 기기와의 연결이 끊겨 자동화를 중단했습니다.")
                    }
                    break
                }
                if (state.isTerminal()) break
            }
        } finally {
            disconnectJob.cancel()
            executionJob.cancel()
            withContext(NonCancellable) {
                withContext(Dispatchers.Main.immediate) {
                    executeRemoteAutomation.cancelAndAwait()
                }
            }
            states.close()
            sessionMutex.withLock {
                if (activeSession?.requestId == requestId) {
                    activeSession = null
                }
            }
        }
    }

    private suspend fun handleCancel() {
        markConnectedFromSender()
        sessionMutex.withLock {
            val session = activeSession
            activeSession = null
            session?.executionJob?.cancelAndJoin()
            withContext(Dispatchers.Main.immediate) {
                executeRemoteAutomation.cancelAndAwait()
            }
            RemoteAutomationStateHub.update {
                it.copy(
                    automationState = AutomationRunState.Stopped,
                    message = "송신 기기 요청으로 자동화를 중지했습니다."
                )
            }
        }
    }

    private suspend fun handleUpdateRepeatCount(message: RemoteProtocolMessage.UpdateRepeatCountRequest) {
        markConnectedFromSender()
        if (activeSession?.requestId != message.requestId) return
        withContext(Dispatchers.Main.immediate) {
            executeRemoteAutomation.updateRepeatCount(message.repeatCount)
        }
    }

    private suspend fun handleCleanMemory(
        writer: PrintWriter
    ) {
        markConnectedFromSender()
        sessionMutex.withLock {
            val session = activeSession
            activeSession = null
            session?.executionJob?.cancelAndJoin()
            withContext(Dispatchers.Main.immediate) {
                executeRemoteAutomation.cancelAndAwait()
            }
        }

        val memoryGateway = AndroidMemoryCleanupGateway(this)
        val result = memoryGateway.cleanMemory()
        when (result) {
            MemoryCleanupResult.Success -> {
                RemoteAutomationStateHub.update {
                    it.copy(message = "원격 요청으로 메모리를 정리했습니다.")
                }
                writer.println(
                    RemoteAutomationProtocol.encode(
                        RemoteProtocolMessage.CleanMemoryResult(
                            success = true,
                            message = "수신 기기 메모리를 정리했습니다."
                        )
                    )
                )
            }
            MemoryCleanupResult.AccessibilityUnavailable -> {
                writer.println(
                    RemoteAutomationProtocol.encode(
                        RemoteProtocolMessage.CleanMemoryResult(
                            success = false,
                            message = "수신 기기의 접근성 서비스가 꺼져 있습니다."
                        )
                    )
                )
            }
            MemoryCleanupResult.InProgress -> {
                writer.println(
                    RemoteAutomationProtocol.encode(
                        RemoteProtocolMessage.CleanMemoryResult(
                            success = false,
                            message = "수신 기기에서 메모리 정리가 이미 진행 중입니다."
                        )
                    )
                )
            }
            is MemoryCleanupResult.Failure -> {
                writer.println(
                    RemoteAutomationProtocol.encode(
                        RemoteProtocolMessage.CleanMemoryResult(
                            success = false,
                            message = "수신 기기 메모리 정리 실패: ${result.message}"
                        )
                    )
                )
            }
        }
    }

    private suspend fun handleSwitchAccount(
        message: RemoteProtocolMessage.SwitchAccountRequest,
        writer: PrintWriter
    ) {
        markConnectedFromSender()
        sessionMutex.withLock {
            val session = activeSession
            activeSession = null
            session?.executionJob?.cancelAndJoin()
            withContext(Dispatchers.Main.immediate) {
                executeRemoteAutomation.cancelAndAwait()
            }
        }

        val displayLabel = message.alias.trim().ifBlank { message.email.trim().ifBlank { "선택한 계정" } }
        runCatching {
            DataStoreMultiGeminiUsageDataSource(this)
                .selectAccountByEmailOrAlias(email = message.email, alias = message.alias)
        }
        val switcher = AndroidGeminiAccountSwitcher(this)
        val result = switcher.switchAccount(
            email = message.email,
            alias = message.alias,
            // 취지: 수신 기기(S25 FE 등)에서는 원격 계정 전환 요청을 처리한 후 GemGemGen으로 복귀하지 않고
            // Gemini 앱 화면에 그대로 잔류시킵니다. 이를 통해 사용자가 보조 기기 화면을 직접 보며
            // 실제로 제미나이 앱에서 대상 Google 계정으로 정상 전환되었는지 육안으로 즉시 확인할 수 있습니다.
            returnToHostApp = false
        )
        when (result) {
            SwitchGeminiAccountResult.Success -> {
                RemoteAutomationStateHub.update {
                    it.copy(message = "원격 요청으로 Gemini 계정을 '$displayLabel'(으)로 전환했습니다.")
                }
                writer.println(
                    RemoteAutomationProtocol.encode(
                        RemoteProtocolMessage.SwitchAccountResult(
                            success = true,
                            message = "수신 기기 Gemini 계정을 '$displayLabel'(으)로 전환했습니다."
                        )
                    )
                )
            }
            SwitchGeminiAccountResult.AccessibilityUnavailable -> {
                writer.println(
                    RemoteAutomationProtocol.encode(
                        RemoteProtocolMessage.SwitchAccountResult(
                            success = false,
                            message = "수신 기기의 접근성 서비스가 꺼져 있습니다."
                        )
                    )
                )
            }
            SwitchGeminiAccountResult.InProgress -> {
                writer.println(
                    RemoteAutomationProtocol.encode(
                        RemoteProtocolMessage.SwitchAccountResult(
                            success = false,
                            message = "수신 기기에서 계정 전환이 이미 진행 중입니다."
                        )
                    )
                )
            }
            is SwitchGeminiAccountResult.Failure -> {
                writer.println(
                    RemoteAutomationProtocol.encode(
                        RemoteProtocolMessage.SwitchAccountResult(
                            success = false,
                            message = result.message
                        )
                    )
                )
            }
        }
    }

    private fun executionConditions(
        targetApp: com.example.gemgemgen.automation.domain.AutomationTargetApp
    ): RemoteExecutionConditions {
        val report = environmentGateway.check()
        val powerManager = getSystemService(PowerManager::class.java)
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        val runState = AndroidAutomationRuntimeProvider.get(this).runState.value
        return RemoteExecutionConditions(
            isWifiConnected = isWifiConnected(),
            isScreenInteractive = powerManager.isInteractive,
            isDeviceLocked = keyguardManager.isDeviceLocked,
            isTargetAppInstalled = report.status.isTargetAppInstalled(targetApp),
            isAccessibilityServiceEnabled = report.status.isAccessibilityServiceEnabled,
            hasWriteSecureSettingsPermission = report.status.hasWriteSecureSettingsPermission,
            isWildcardDirectoryAccessible = report.status.isWildcardDirectoryAccessible,
            hasOverlayPermission = report.status.hasOverlayPermission,
            isAutomationBusy = runState is AutomationRunState.Running
        )
    }

    private fun isWifiConnected(): Boolean {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun isLocalAddress(socket: Socket): Boolean {
        val address = socket.inetAddress
        return address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress
    }

    private fun writeState(
        writer: PrintWriter,
        requestId: String,
        state: AutomationRunState
    ): Boolean {
        writer.println(
            RemoteAutomationProtocol.encode(
                RemoteProtocolMessage.StateUpdate(requestId, state)
            )
        )
        return !writer.checkError()
    }

    private fun registerService(port: Int) {
        synchronized(nsdLock) {
            registerServiceLocked(port)
        }
    }

    private fun registerServiceLocked(port: Int) {
        val nsdManager = getSystemService(NsdManager::class.java)
        if (registrationListener != null || isRegistering) {
            pendingRegisterPort = port
            unregisterServiceLocked()
            return
        }
        val isReady = !store.isUserDisconnected()
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "GemGemGen ${deviceName()}".take(55)
            serviceType = RemoteAutomationProtocol.SERVICE_TYPE
            this.port = port
            setAttribute("receiverId", store.installationId())
            setAttribute("isPaired", isReady.toString())
        }
        isRegistering = true
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                val nextPort = synchronized(nsdLock) {
                    isRegistering = false
                    val pending = pendingRegisterPort
                    if (pending != null) {
                        pendingRegisterPort = null
                        unregisterServiceLocked()
                    }
                    pending
                }
                if (nextPort == null) {
                    RemoteAutomationStateHub.update {
                        it.copy(message = receiverReadyMessage(serviceInfo.serviceName))
                    }
                }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                val nextPort = synchronized(nsdLock) {
                    isRegistering = false
                    registrationListener = null
                    val pending = pendingRegisterPort
                    pendingRegisterPort = null
                    pending
                }
                if (nextPort != null) {
                    registerService(nextPort)
                } else {
                    RemoteAutomationStateHub.update {
                        it.copy(message = "Wi-Fi 수신 서비스를 알리지 못했습니다. ($errorCode)")
                    }
                }
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                val nextPort = synchronized(nsdLock) {
                    registrationListener = null
                    isRegistering = false
                    val pending = pendingRegisterPort
                    pendingRegisterPort = null
                    pending
                }
                if (nextPort != null) {
                    registerService(nextPort)
                }
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                val nextPort = synchronized(nsdLock) {
                    registrationListener = null
                    isRegistering = false
                    val pending = pendingRegisterPort
                    pendingRegisterPort = null
                    pending
                }
                if (nextPort != null) {
                    registerService(nextPort)
                }
            }
        }
        registrationListener = listener
        runCatching {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { error ->
            isRegistering = false
            registrationListener = null
            RemoteAutomationStateHub.update {
                it.copy(message = "Wi-Fi 수신 서비스 등록 실패: ${error.message ?: "알 수 없는 오류"}")
            }
        }
    }

    private fun unregisterService() {
        synchronized(nsdLock) {
            pendingRegisterPort = null
            unregisterServiceLocked()
        }
    }

    private fun unregisterServiceLocked() {
        val listener = registrationListener ?: return
        runCatching {
            getSystemService(NsdManager::class.java).unregisterService(listener)
        }
    }

    private fun reregisterService(port: Int) {
        synchronized(nsdLock) {
            registerServiceLocked(port)
        }
    }

    private fun receiverReadyMessage(registeredName: String = deviceName()): String {
        return if (store.isUserDisconnected()) {
            "$registeredName 연결 해제됨 · 설정에서 연결 새로고침을 눌러주세요."
        } else {
            "$registeredName 수신 대기 중 · 자동 연결 활성화됨"
        }
    }

    private fun stateMessage(state: AutomationRunState): String {
        return when (state) {
            AutomationRunState.Idle -> receiverReadyMessage()
            is AutomationRunState.Running -> state.step
            AutomationRunState.Success -> "자동화가 완료되었습니다."
            AutomationRunState.Stopped -> "자동화를 중지했습니다."
            is AutomationRunState.Failure -> state.message
        }
    }

    private fun deviceName(): String {
        val configured = Settings.Global.getString(contentResolver, "device_name")
        return configured?.takeIf(String::isNotBlank) ?: Build.MODEL
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "원격 자동화 수신",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun buildNotification(): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            intent(this).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("원격 자동화 수신 대기 중")
            .setContentText("같은 Wi-Fi의 요청을 기다리고 있습니다.")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, "수신 중지", stopIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "remote_automation_receiver"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_STOP = "com.example.gemgemgen.remote.STOP_RECEIVER"
        const val ACTION_DISCONNECT = "com.example.gemgemgen.remote.DISCONNECT_RECEIVER"
        const val ACTION_REREGISTER = "com.example.gemgemgen.remote.REREGISTER_RECEIVER"

        fun intent(context: Context): Intent {
            return Intent(context, RemoteAutomationReceiverService::class.java)
        }
    }

    private data class ReceiverSession(
        val requestId: String,
        val executionJob: Job
    )
}
