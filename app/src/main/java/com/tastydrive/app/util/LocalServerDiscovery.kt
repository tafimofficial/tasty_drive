package com.tastydrive.app.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object LocalServerDiscovery {
    private const val TAG = "LocalDiscovery"
    const val DEFAULT_PORT = 8081

    data class DiscoveredServer(
        val ip: String,
        val port: Int,
        val url: String,
        val isVerified: Boolean = true
    )

    private val fastHttpClient = OkHttpClient.Builder()
        .connectTimeout(800, TimeUnit.MILLISECONDS)
        .readTimeout(800, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Finds the active private IPv4 address of this Android device.
     */
    fun getLocalDeviceIp(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses ?: continue
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("192.168.") || host.startsWith("10.") ||
                            host.startsWith("172.16.") || host.startsWith("172.17.") ||
                            host.startsWith("172.18.") || host.startsWith("172.19.") ||
                            host.startsWith("172.2") || host.startsWith("172.30.") ||
                            host.startsWith("172.31.")
                        ) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error obtaining local IP: ${e.message}")
        }
        return null
    }

    /**
     * Scans the subnet for Docker Telegram Bot API server.
     * Uses 30 parallel coroutines with socket connect probes to complete within ~1-2 seconds.
     */
    suspend fun discoverServer(
        preferredIp: String? = null,
        targetPort: Int = DEFAULT_PORT,
        botToken: String? = null,
        onProgress: ((checked: Int, total: Int) -> Unit)? = null
    ): DiscoveredServer? = withContext(Dispatchers.IO) {
        try {
            val rawDeviceIp = getLocalDeviceIp() ?: preferredIp
            if (rawDeviceIp.isNullOrBlank()) {
                Log.w(TAG, "Cannot determine local subnet; device not connected to Wi-Fi/LAN.")
                return@withContext null
            }

            val deviceIp = rawDeviceIp.trim().removePrefix("http://").removePrefix("https://").substringBefore(":")

            // 1. Try preferred/last-known IP first if provided
            if (!preferredIp.isNullOrBlank()) {
                val cleanIp = preferredIp.trim().removePrefix("http://").removePrefix("https://").substringBefore(":")
                try {
                    if (verifyServer(cleanIp, targetPort, botToken)) {
                        return@withContext DiscoveredServer(
                            ip = cleanIp,
                            port = targetPort,
                            url = "http://$cleanIp:$targetPort"
                        )
                    }
                } catch (e: Throwable) {
                    // ignore
                }
            }

            val lastDot = deviceIp.lastIndexOf('.')
            if (lastDot == -1) return@withContext null
            val subnetPrefix = deviceIp.substring(0, lastDot + 1)
            val myLastOctet = deviceIp.substring(lastDot + 1).toIntOrNull() ?: 100

            // Prioritize: Gateway (1), nearby IPs around phone, lower IPs, then rest
            val ipList = ArrayList<String>()
            val gateway = "${subnetPrefix}1"
            ipList.add(gateway)

            // Add 10 IPs around device IP
            for (offset in -10..10) {
                val octet = myLastOctet + offset
                if (octet in 2..254 && octet != myLastOctet) {
                    val ip = "$subnetPrefix$octet"
                    if (!ipList.contains(ip)) ipList.add(ip)
                }
            }

            // Add common host IPs (2 to 50)
            for (octet in 2..50) {
                val ip = "$subnetPrefix$octet"
                if (!ipList.contains(ip)) ipList.add(ip)
            }

            // Add remaining IPs in subnet
            for (octet in 51..254) {
                val ip = "$subnetPrefix$octet"
                if (!ipList.contains(ip)) ipList.add(ip)
            }

            val total = ipList.size
            val counter = AtomicInteger(0)
            val foundServer = AtomicBoolean(false)
            var resultServer: DiscoveredServer? = null

            val semaphore = Semaphore(30)

            val jobs = ipList.map { ip ->
                async {
                    if (foundServer.get()) return@async

                    semaphore.acquire()
                    try {
                        if (foundServer.get()) return@async

                        val isOpen = isPortOpen(ip, targetPort, 180)
                        if (isOpen) {
                            Log.i(TAG, "Port $targetPort is open on $ip! Verifying Telegram Bot API...")
                            if (verifyServer(ip, targetPort, botToken)) {
                                if (foundServer.compareAndSet(false, true)) {
                                    resultServer = DiscoveredServer(
                                        ip = ip,
                                        port = targetPort,
                                        url = "http://$ip:$targetPort"
                                    )
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        // ignore individual socket failure
                    } finally {
                        semaphore.release()
                        val c = counter.incrementAndGet()
                        try {
                            onProgress?.invoke(c, total)
                        } catch (e: Throwable) {
                            // ignore progress reporting error
                        }
                    }
                }
            }

            jobs.awaitAll()
            return@withContext resultServer
        } catch (fatal: Throwable) {
            fatal.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Tests if TCP port is accepting connections with a brief timeout.
     */
    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Verifies that the endpoint is indeed an active Telegram Bot API server.
     */
    suspend fun verifyServer(ip: String, port: Int, botToken: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanToken = botToken?.trim() ?: ""
            val testUrl = if (cleanToken.isNotEmpty()) {
                "http://$ip:$port/bot$cleanToken/getMe"
            } else {
                "http://$ip:$port/"
            }

            val request = Request.Builder()
                .url(testUrl)
                .get()
                .build()

            fastHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (body.contains("\"ok\":")) {
                    return@withContext true
                }
                val serverHeader = response.header("Server") ?: ""
                if (serverHeader.contains("Telegram", ignoreCase = true)) {
                    return@withContext true
                }
            }
        } catch (e: Throwable) {
            // ignore network/connect errors during probing
        }
        return@withContext false
    }

    /**
     * Fast health check on configured URL.
     */
    suspend fun pingServer(serverUrl: String, botToken: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            if (serverUrl.isBlank()) return@withContext false
            val clean = serverUrl.trim().removeSuffix("/")
            val url = if (!clean.startsWith("http://") && !clean.startsWith("https://")) "http://$clean" else clean

            val probeUrl = if (!botToken.isNullOrBlank()) {
                val token = botToken.trim()
                if (url.endsWith("/bot")) "$url$token/getMe" else "$url/bot$token/getMe"
            } else {
                "$url/"
            }

            val request = Request.Builder().url(probeUrl).get().build()
            fastHttpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                return@withContext body.contains("\"ok\":") || resp.isSuccessful
            }
        } catch (e: Throwable) {
            return@withContext false
        }
    }
}
