// 역할: Android NSD(mDNS)를 통해 동일 와이파이 내 원격 자동화 수신 기기(태블릿)를 자동 탐색합니다.
package com.example.gemgemgen.remote.android

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

internal class RemoteServiceDiscovery(
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
                            val isPairedAttr = serviceInfo.attributes[ATTRIBUTE_IS_PAIRED]
                                ?.toString(Charsets.UTF_8)
                            val isReceiverPaired = isPairedAttr?.toBooleanStrictOrNull() ?: true
                            @Suppress("DEPRECATION")
                            val address = serviceInfo.host ?: return
                            if (receiverId.isBlank() || serviceInfo.port <= 0) return
                            onResolved(
                                RemoteEndpoint(
                                    receiverId = receiverId,
                                    name = serviceInfo.serviceName,
                                    address = address,
                                    port = serviceInfo.port,
                                    isPaired = isReceiverPaired
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
        const val ATTRIBUTE_IS_PAIRED = "isPaired"
    }
}
