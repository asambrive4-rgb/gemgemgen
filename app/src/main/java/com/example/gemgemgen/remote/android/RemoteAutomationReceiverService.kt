// 역할: 백그라운드 포그라운드 서비스로 상주하며 원격 기기의 자동화 요청을 수신합니다.
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
import com.example.gemgemgen.automation.android.AndroidMemoryCleanupGateway
import com.example.gemgemgen.automation.android.GeminiAccessibilityService
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.usecase.MemoryCleanupResult
import com.example.gemgemgen.automation.domain.isTerminal
import com.example.gemgemgen.environment.android.AndroidEnvironmentGateway
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteExecutionConditions
import com.example.gemgemgen.remote.usecase.ExecuteRemoteAutomationUseCase
import com.example.gemgemgen.remote.usecase.CheckRemoteExecutionUseCase
import com.example.gemgemgen.remote.usecase.ManageReceivedAutomationUseCase
import com.example.gemgemgen.ui.MainActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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
    private var registrationListener: NsdManager.RegistrationListener? = null
    private val manageReceivedAutomation = ManageReceivedAutomationUseCase()
    private val sessionMutex = Mutex()
    @Volatile private var activeSession: ReceiverSession? = null
    private val pairingCode = "%04d".format(SecureRandom().nextInt(10_000))

    override fun onCreate() {
        super.onCreate()
        store = RemoteAutomationStore(this)
        executeRemoteAutomation = ExecuteRemoteAutomationUseCase(
            checkExecution = CheckRemoteExecutionUseCase(),
            automation = AndroidAutomationRuntimeProvider.get(this)
        )
        environmentGateway = AndroidEnvironmentGateway(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        val isPaired = store.pairedSender() != null && !store.isUserDisconnected()
        RemoteAutomationStateHub.update {
            it.copy(
                mode = AutomationMode.RECEIVER,
                isReceiverRunning = true,
                receiverPairingCode = pairingCode,
                isPaired = isPaired,
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
                receiverPairingCode = "",
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
                    serviceScope.launch(Dispatchers.IO) { handleClient(socket) }
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
        socket.use {
            socket.soTimeout = RemoteAutomationProtocol.SOCKET_TIMEOUT_MS
            if (!isLocalAddress(socket)) return
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)
            val line = reader.readLine() ?: return
            when (val message = RemoteAutomationProtocol.decode(line)) {
                is RemoteProtocolMessage.PairRequest -> handlePair(message, writer)
                is RemoteProtocolMessage.DisconnectRequest -> handleDisconnect(message, writer)
                is RemoteProtocolMessage.RunRequest -> handleRun(message, writer, socket)
                is RemoteProtocolMessage.CancelRequest -> handleCancel(message)
                is RemoteProtocolMessage.CleanMemoryRequest -> handleCleanMemory(message, writer)
                is RemoteProtocolMessage.SwitchGeminiAccountRequest -> handleSwitchGeminiAccount(message, writer)
                else -> Unit
            }
        }
    }

    private fun handlePair(message: RemoteProtocolMessage.PairRequest, writer: PrintWriter) {
        if (message.pairingCode != pairingCode || message.senderId.isBlank()) {
            writer.println(
                RemoteAutomationProtocol.encode(
                    RemoteProtocolMessage.PairResult(
                        success = false,
                        message = "연결 번호가 일치하지 않습니다."
                    )
                )
            )
            return
        }

        val existing = store.pairedSender()
        val token = if (existing?.senderId == message.senderId) {
            existing.token
        } else {
            UUID.randomUUID().toString() + UUID.randomUUID().toString()
        }
        store.savePairedSender(message.senderId, token)
        store.saveUserDisconnected(false)
        val receiverName = deviceName()
        writer.println(
            RemoteAutomationProtocol.encode(
                RemoteProtocolMessage.PairResult(
                    success = true,
                    receiverId = store.installationId(),
                    receiverName = receiverName,
                    token = token
                )
            )
        )
        RemoteAutomationStateHub.update {
            it.copy(isPaired = true, message = "태블릿 연결됨")
        }
        serverSocket?.localPort?.let(::reregisterService)
    }

    private fun handleDisconnect(
        message: RemoteProtocolMessage.DisconnectRequest,
        writer: PrintWriter
    ) {
        if (!isAuthenticated(message.senderId, message.token)) {
            writer.println(
                RemoteAutomationProtocol.encode(
                    RemoteProtocolMessage.DisconnectResult(
                        success = false,
                        message = "등록되지 않은 송신 기기입니다."
                    )
                )
            )
            return
        }
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
        if (!isAuthenticated(message.senderId, message.token)) {
            writeState(
                writer,
                message.request.requestId,
                AutomationRunState.Failure("등록되지 않은 송신 기기입니다.")
            )
            return
        }
        val requestId = message.request.requestId
        socket.soTimeout = 0
        val states = Channel<AutomationRunState>(Channel.UNLIMITED)
        val executionJob = sessionMutex.withLock {
            manageReceivedAutomation.acceptLatest(requestId)

            val previousSession = activeSession
            previousSession?.executionJob?.cancelAndJoin()
            withContext(Dispatchers.Main.immediate) {
                executeRemoteAutomation.cancel()
            }

            RemoteAutomationStateHub.update {
                it.copy(
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
        try {
            while (true) {
                val state = states.receive()
                if (manageReceivedAutomation.canPublish(requestId)) {
                    RemoteAutomationStateHub.update {
                        it.copy(automationState = state, message = stateMessage(state))
                    }
                }
                writeState(writer, requestId, state)
                if (state.isTerminal()) break
            }
        } finally {
            executionJob.cancel()
            states.close()
            sessionMutex.withLock {
                if (manageReceivedAutomation.finishIfCurrent(requestId)) {
                    if (activeSession?.requestId == requestId) {
                        activeSession = null
                    }
                }
            }
        }
    }

    private suspend fun handleCancel(message: RemoteProtocolMessage.CancelRequest) {
        if (!isAuthenticated(message.senderId, message.token)) return
        sessionMutex.withLock {
            if (!manageReceivedAutomation.stopIfCurrent(message.requestId)) return
            val session = activeSession?.takeIf { it.requestId == message.requestId }
            activeSession = null
            session?.executionJob?.cancelAndJoin()
            withContext(Dispatchers.Main.immediate) {
                executeRemoteAutomation.cancel()
            }
        }
    }

    private suspend fun handleCleanMemory(
        message: RemoteProtocolMessage.CleanMemoryRequest,
        writer: PrintWriter
    ) {
        if (!isAuthenticated(message.senderId, message.token)) {
            writer.println(
                RemoteAutomationProtocol.encode(
                    RemoteProtocolMessage.CleanMemoryResult(
                        success = false,
                        message = "등록되지 않은 송신 기기입니다."
                    )
                )
            )
            return
        }

        val runState = AndroidAutomationRuntimeProvider.get(this).runState.value
        if (runState is AutomationRunState.Running || activeSession != null) {
            writer.println(
                RemoteAutomationProtocol.encode(
                    RemoteProtocolMessage.CleanMemoryResult(
                        success = false,
                        message = "수신 기기에서 자동화 실행 중에는 메모리를 정리할 수 없습니다."
                    )
                )
            )
            return
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

    private suspend fun handleSwitchGeminiAccount(
        message: RemoteProtocolMessage.SwitchGeminiAccountRequest,
        writer: PrintWriter
    ) {
        if (!isAuthenticated(message.senderId, message.token)) {
            writer.println(
                RemoteAutomationProtocol.encode(
                    RemoteProtocolMessage.SwitchGeminiAccountResult(
                        success = false,
                        message = "등록되지 않은 송신 기기입니다.",
                        activeAccountId = message.accountId
                    )
                )
            )
            return
        }

        val runState = AndroidAutomationRuntimeProvider.get(this).runState.value
        if (runState is AutomationRunState.Running || activeSession != null) {
            writer.println(
                RemoteAutomationProtocol.encode(
                    RemoteProtocolMessage.SwitchGeminiAccountResult(
                        success = false,
                        message = "수신 기기에서 자동화 실행 중에는 계정을 교체할 수 없습니다.",
                        activeAccountId = message.accountId
                    )
                )
            )
            return
        }

        val conditions = withContext(Dispatchers.IO) {
            executionConditions(com.example.gemgemgen.automation.domain.AutomationTargetApp.GEMINI)
        }
        val decision = CheckRemoteExecutionUseCase().decide(conditions, requiresWildcardDirectory = false)
        if (decision is com.example.gemgemgen.remote.domain.RemoteExecutionDecision.Rejected) {
            writer.println(
                RemoteAutomationProtocol.encode(
                    RemoteProtocolMessage.SwitchGeminiAccountResult(
                        success = false,
                        message = decision.message,
                        activeAccountId = message.accountId
                    )
                )
            )
            return
        }

        val service = GeminiAccessibilityService.activeService
        if (service == null) {
            writer.println(
                RemoteAutomationProtocol.encode(
                    RemoteProtocolMessage.SwitchGeminiAccountResult(
                        success = false,
                        message = "수신 기기의 접근성 서비스가 켜져 있지 않습니다.",
                        activeAccountId = message.accountId
                    )
                )
            )
            return
        }

        RemoteAutomationStateHub.update {
            it.copy(message = "원격 요청으로 Gemini 계정 교체 중: [${message.accountAlias}]")
        }

        val result = service.switchGeminiAccount(
            identifier = message.accountIdentifier,
            alias = message.accountAlias
        )

        when (result) {
            is com.example.gemgemgen.automation.android.GeminiAccountSwitchResult.Success -> {
                RemoteAutomationStateHub.update {
                    it.copy(message = "Gemini 계정을 [${message.accountAlias}]로 교체했습니다.")
                }
                writer.println(
                    RemoteAutomationProtocol.encode(
                        RemoteProtocolMessage.SwitchGeminiAccountResult(
                            success = true,
                            message = result.message,
                            activeAccountId = message.accountId
                        )
                    )
                )
            }
            is com.example.gemgemgen.automation.android.GeminiAccountSwitchResult.Failure -> {
                RemoteAutomationStateHub.update {
                    it.copy(message = "Gemini 계정 교체 실패: ${result.message}")
                }
                writer.println(
                    RemoteAutomationProtocol.encode(
                        RemoteProtocolMessage.SwitchGeminiAccountResult(
                            success = false,
                            message = result.message,
                            activeAccountId = message.accountId
                        )
                    )
                )
            }
            com.example.gemgemgen.automation.android.GeminiAccountSwitchResult.Unavailable -> {
                writer.println(
                    RemoteAutomationProtocol.encode(
                        RemoteProtocolMessage.SwitchGeminiAccountResult(
                            success = false,
                            message = "수신 기기 접근성 서비스를 사용할 수 없습니다.",
                            activeAccountId = message.accountId
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

    private fun isAuthenticated(senderId: String, token: String): Boolean {
        val paired = store.pairedSender() ?: return false
        return paired.senderId == senderId && paired.token == token
    }

    private fun writeState(
        writer: PrintWriter,
        requestId: String,
        state: AutomationRunState
    ) {
        writer.println(
            RemoteAutomationProtocol.encode(
                RemoteProtocolMessage.StateUpdate(requestId, state)
            )
        )
    }

    private fun registerService(port: Int) {
        val nsdManager = getSystemService(NsdManager::class.java)
        val isPaired = store.pairedSender() != null && !store.isUserDisconnected()
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "GemGemGen ${deviceName()}".take(55)
            serviceType = RemoteAutomationProtocol.SERVICE_TYPE
            this.port = port
            setAttribute("receiverId", store.installationId())
            setAttribute("isPaired", isPaired.toString())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                RemoteAutomationStateHub.update {
                    it.copy(message = receiverReadyMessage(serviceInfo.serviceName))
                }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                RemoteAutomationStateHub.update {
                    it.copy(message = "Wi-Fi 수신 서비스를 알리지 못했습니다. ($errorCode)")
                }
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun unregisterService() {
        val listener = registrationListener ?: return
        registrationListener = null
        runCatching {
            getSystemService(NsdManager::class.java).unregisterService(listener)
        }
    }

    private fun reregisterService(port: Int) {
        unregisterService()
        registerService(port)
    }

    private fun receiverReadyMessage(registeredName: String = deviceName()): String {
        return if (store.pairedSender() == null || store.isUserDisconnected()) {
            "$registeredName 수신 대기 중 · 연결 번호 $pairingCode"
        } else {
            "$registeredName 수신 대기 중 · 태블릿 연결됨"
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

        fun intent(context: Context): Intent {
            return Intent(context, RemoteAutomationReceiverService::class.java)
        }
    }

    private data class ReceiverSession(
        val requestId: String,
        val executionJob: Job
    )
}
