package ai.opencode.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

class PlatformBridge(
    private val context: Context,
    private val controller: BridgeController
) {
    private val main = Handler(Looper.getMainLooper())
    private val voice = VoiceBridge(context, controller)
    private val network = NetworkScanBridge(context, controller)
    private val haptics = HapticBridge(context)
    private val notifications = NotificationBridge(context, controller)
    private val storage = StorageBridge(context)

    var onEvent: ((type: String, payload: Any?) -> Unit)? = null

    fun handle(id: String, method: String, params: JSONObject, callback: BridgeCallback) {
        when (method) {
            "openLink" -> handleOpenLink(params, callback)
            "notify" -> notifications.notify(params, callback)
            "haptic" -> haptics.haptic(params, callback)
            "reload" -> handleReload(callback)
            "share" -> handleShare(params, callback)

            "isWhisperReady" -> voice.isReady(callback)
            "startRecording" -> voice.start(callback)
            "stopRecording" -> voice.stop(callback)

            "scanNetwork" -> network.scan(callback)
            "cancelScan" -> network.cancel(callback)

            "getDefaultServerUrl" -> storage.getDefaultServerUrl(callback)
            "setDefaultServerUrl" -> storage.setDefaultServerUrl(params, callback)

            "storageGet" -> storage.get(params, callback)
            "storageSet" -> storage.set(params, callback)
            "storageRemove" -> storage.remove(params, callback)
            "storageClear" -> storage.clear(params, callback)
            "storageKey" -> storage.key(params, callback)
            "storageLength" -> storage.length(params, callback)

            else -> callback(null, "Unknown method: $method")
        }
    }

    private fun handleOpenLink(params: JSONObject, callback: BridgeCallback) {
        val url = params.optString("url", "")
        if (url.isEmpty()) {
            callback(null, "Missing url")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            callback(null, null)
        } catch (e: Exception) {
            callback(null, e.message)
        }
    }

    private fun handleReload(callback: BridgeCallback) {
        main.post {
            controller.sendEvent("appLifecycle", mapOf("state" to "reload"))
            try {
                val activity = context as? MainActivity
                activity?.webView?.reload()
            } catch (_: Throwable) {}
        }
        callback(null, null)
    }

    private fun handleShare(params: JSONObject, callback: BridgeCallback) {
        val text = params.optString("text", "").trim()
        val url = params.optString("url", "").trim()
        val parts = listOfNotNull(text.takeIf { it.isNotEmpty() }, url.takeIf { it.isNotEmpty() })

        if (parts.isEmpty()) {
            callback(false, null)
            return
        }

        val content = parts.joinToString("\n")
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, content)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(sendIntent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            callback(true, null)
        } catch (e: Exception) {
            callback(false, null)
        }
    }

    fun destroy() {
        voice.destroy()
        network.destroy()
    }
}
