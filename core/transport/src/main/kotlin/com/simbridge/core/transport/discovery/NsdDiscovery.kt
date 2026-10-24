package com.simbridge.core.transport.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import java.util.concurrent.Executors
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val NSD_SERVICE_TYPE = "_simbridge._tcp"
private const val TXT_FINGERPRINT = "fp"
const val FINGERPRINT_PREFIX_LENGTH = 16

data class DiscoveredHost(
    val serviceName: String,
    val address: String,
    val port: Int,
    val fingerprintPrefix: String?,
)

/** Advertises the Host on the local network. The TXT record carries only a fingerprint prefix. */
class NsdAdvertiser(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var listener: NsdManager.RegistrationListener? = null

    @Synchronized
    fun register(serviceName: String, port: Int, hostFingerprint: String) {
        unregister()
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            serviceType = NSD_SERVICE_TYPE
            this.port = port
            setAttribute(TXT_FINGERPRINT, hostFingerprint.take(FINGERPRINT_PREFIX_LENGTH))
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        listener = l
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
    }

    @Synchronized
    fun unregister() {
        listener?.let { runCatching { nsd.unregisterService(it) } }
        listener = null
    }
}

/** Discovers Hosts on the local network, resolving each to an IPv4 address and port. */
class NsdHostFinder(private val context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)

    fun discover(): Flow<DiscoveredHost> = callbackFlow {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val multicastLock = wifi?.createMulticastLock("simbridge-nsd")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        val executor = Executors.newSingleThreadExecutor()

        fun emitResolved(info: NsdServiceInfo) {
            val address = hostAddress(info) ?: return
            val fp = info.attributes[TXT_FINGERPRINT]?.decodeToString()
            trySend(DiscoveredHost(info.serviceName, address, info.port, fp))
        }

        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IllegalStateException("NSD discovery failed: $errorCode"))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                resolve(info, executor, ::emitResolved)
            }
        }
        nsd.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(discovery) }
            runCatching { multicastLock?.release() }
            executor.shutdown()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo, executor: java.util.concurrent.Executor, onResolved: (NsdServiceInfo) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    onResolved(serviceInfo)
                    runCatching { nsd.unregisterServiceInfoCallback(this) }
                }
                override fun onServiceLost() = Unit
                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            nsd.registerServiceInfoCallback(info, executor, callback)
        } else {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) = onResolved(serviceInfo)
            })
        }
    }

    @Suppress("DEPRECATION")
    private fun hostAddress(info: NsdServiceInfo): String? {
        val candidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) info.hostAddresses else listOfNotNull(info.host)
        return candidates.firstOrNull { it is Inet4Address }?.hostAddress
    }
}
