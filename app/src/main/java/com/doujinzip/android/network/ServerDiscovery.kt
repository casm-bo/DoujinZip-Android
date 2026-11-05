package com.doujinzip.android.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

data class DiscoveredServer(val id: String, val name: String, val baseUrl: String)

internal fun discoveryIpv4(value: String): Boolean {
    val parts = value.split('.').map { it.toIntOrNull() ?: return false }
    if (parts.size != 4 || parts.any { it !in 0..255 }) return false
    return parts[0] == 10 || (parts[0] == 192 && parts[1] == 168) || (parts[0] == 172 && parts[1] in 16..31)
}

class ServerDiscovery(context: Context, private val client: CompanionClient) {
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    suspend fun scan(onFound: (DiscoveredServer) -> Unit) = coroutineScope {
        val found = Channel<NsdServiceInfo>(32)
        val seen = hashSetOf<String>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { found.close(IllegalStateException("NSD $code")) }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) { if (info.serviceType.contains("_doujinzip._tcp")) found.trySend(info) }
            override fun onServiceLost(info: NsdServiceInfo) = Unit
        }
        try {
            manager.discoverServices("_doujinzip._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
            withTimeoutOrNull(12_000) {
                while (isActive) {
                    val info = found.receive()
                    if (!seen.add(info.serviceName)) continue
                    val resolved = withTimeoutOrNull(2_000) { resolve(info) } ?: continue
                    val id = resolved.attributes["id"]?.toString(Charsets.UTF_8) ?: continue
                    if (!id.matches(Regex("[a-fA-F0-9-]{36}")) || resolved.port !in 1..65535) continue
                    val advertised = runCatching {
                        val array = JSONArray(resolved.attributes["addresses"]?.toString(Charsets.UTF_8) ?: "[]")
                        (0 until array.length()).map { array.getString(it) }.filter(::discoveryIpv4).take(8)
                    }.getOrDefault(emptyList())
                    val resolvedHosts = {
                        if (Build.VERSION.SDK_INT >= 34) resolved.hostAddresses.mapNotNull { it.hostAddress }.filter(::discoveryIpv4)
                        else listOfNotNull(resolved.host?.hostAddress).filter(::discoveryIpv4)
                    }
                    val hosts = advertised + resolvedHosts()
                    for (host in hosts.distinct()) {
                        val url = "http://$host:${resolved.port}"
                        val status = runCatching { client.getStatus(url, 800) }.getOrNull() ?: continue
                        if (status.service != "doujin-menu-companion" || status.serverId != id) continue
                        onFound(DiscoveredServer(id, status.serverName ?: info.serviceName, url))
                        break
                    }
                }
            }
        } finally {
            runCatching { manager.stopServiceDiscovery(listener) }
            found.close()
        }
    }
    private suspend fun resolve(info: NsdServiceInfo): NsdServiceInfo? = suspendCancellableCoroutine { continuation ->
        manager.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) { if (continuation.isActive) continuation.resume(null) }
            override fun onServiceResolved(info: NsdServiceInfo) { if (continuation.isActive) continuation.resume(info) }
        })
    }
}
