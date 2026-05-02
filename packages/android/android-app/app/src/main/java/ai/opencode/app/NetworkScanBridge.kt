package ai.opencode.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.Future

private data class ScanEntry(val host: String, val port: Int, val url: String)
private data class WifiAddressInfo(val address: String, val prefixLength: Int)

class NetworkScanBridge(
    private val context: Context,
    private val controller: BridgeController
) {
    private val main = Handler(Looper.getMainLooper())
    private val scanExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var scanCancelled = false

    @Volatile
    private var scanTask: Future<*>? = null

    @Volatile
    private var scanGeneration = 0

    fun scan(callback: BridgeCallback) {
        val gen = scanGeneration + 1
        scanGeneration = gen
        scanCancelled = false
        scanTask?.cancel(true)

        scanTask = scanExecutor.submit {
            val results = runScan(gen)
            if (isScanStale(gen)) {
                main.post { callback(ArrayList<JSONObject>(), null) }
                return@submit
            }
            main.post {
                callback(results, null)
                controller.sendEvent("scanComplete", JSONObject())
            }
        }
    }

    fun cancel(callback: BridgeCallback) {
        scanCancelled = true
        scanGeneration += 1
        scanTask?.cancel(true)
        callback(null, null)
    }

    fun destroy() {
        scanCancelled = true
        scanTask?.cancel(true)
        scanExecutor.shutdownNow()
    }

    private fun runScan(gen: Int): ArrayList<JSONObject> {
        val info = wifiAddress() ?: return ArrayList()

        val hosts = subnetHosts(info.address, info.prefixLength)
        if (hosts.isEmpty()) return ArrayList()

        val pool = Executors.newFixedThreadPool(48)
        val futures = ArrayList<Future<ScanEntry?>>()

        for (host in hosts) {
            if (isScanStale(gen)) break
            futures.add(pool.submit<ScanEntry?> { probeHost(host, gen) })
        }

        val found = ArrayList<JSONObject>()
        for (future in futures) {
            if (isScanStale(gen)) break
            val item = try { future.get() } catch (_: Throwable) { null }
            if (item != null) {
                val value = JSONObject().apply {
                    put("host", item.host)
                    put("port", item.port)
                    put("url", item.url)
                }
                found.add(value)
                main.post {
                    if (!isScanStale(gen)) controller.sendEvent("scanResult", value)
                }
            }
        }

        pool.shutdownNow()
        return found
    }

    private fun probeHost(host: String, gen: Int): ScanEntry? {
        if (isScanStale(gen)) return null

        val port = 4096
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(host, port), 500)
            socket.close()

            val base = "http://$host:$port"
            if (!checkHealth("$base/global/health", gen) && !checkHealth("$base/health", gen)) return null
            ScanEntry(host = host, port = port, url = base)
        } catch (_: Throwable) {
            null
        } finally {
            try { socket.close() } catch (_: Throwable) {}
        }
    }

    private fun checkHealth(url: String, gen: Int): Boolean {
        repeat(2) {
            if (isScanStale(gen)) return false
            val connection = try {
                URL(url).openConnection() as HttpURLConnection
            } catch (_: Throwable) {
                return false
            }

            val healthy = try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 1200
                connection.readTimeout = 1200
                connection.connect()
                connection.responseCode in 200..299
            } catch (_: Throwable) {
                false
            } finally {
                connection.disconnect()
            }

            if (healthy) return true
        }
        return false
    }

    private fun wifiAddress(): WifiAddressInfo? {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val network = cm.activeNetwork
                if (network != null) {
                    val caps = cm.getNetworkCapabilities(network)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        val props = cm.getLinkProperties(network)
                        if (props != null) {
                            for (la in props.linkAddresses) {
                                val addr = la.address
                                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                                    val host = addr.hostAddress ?: continue
                                    return WifiAddressInfo(host, la.prefixLength)
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (ni in interfaces) {
                if (!ni.isUp || ni.isLoopback) continue
                val name = ni.name ?: continue
                if (!name.startsWith("wlan")) continue
                for (ia in ni.interfaceAddresses) {
                    val addr = ia.address
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        return WifiAddressInfo(host, ia.networkPrefixLength.toInt())
                    }
                }
            }
        } catch (_: Throwable) {}

        return null
    }

    private fun subnetHosts(ip: String, prefixLength: Int): List<String> {
        val parts = ip.split('.')
        if (parts.size != 4) return emptyList()

        val ipInt = (parts[0].toInt() shl 24) or
            (parts[1].toInt() shl 16) or
            (parts[2].toInt() shl 8) or
            parts[3].toInt()

        val prefix = prefixLength.coerceIn(20, 30)
        val mask = (-1 shl (32 - prefix))
        val network = ipInt and mask
        val broadcast = network or mask.inv()

        val local24 = ip.substringBeforeLast('.', missingDelimiterValue = "")
        val primary = ArrayList<String>()
        val secondary = ArrayList<String>()
        for (addr in (network + 1) until broadcast) {
            val host = "${(addr ushr 24) and 0xFF}." +
                "${(addr ushr 16) and 0xFF}." +
                "${(addr ushr 8) and 0xFF}." +
                "${addr and 0xFF}"
            if (host.startsWith("$local24.")) {
                primary.add(host)
            } else {
                secondary.add(host)
            }
        }
        val hosts = ArrayList<String>(primary.size + secondary.size)
        hosts.addAll(primary)
        hosts.addAll(secondary)
        return hosts
    }

    private fun isScanStale(gen: Int): Boolean {
        return scanCancelled || scanGeneration != gen || Thread.currentThread().isInterrupted
    }
}
