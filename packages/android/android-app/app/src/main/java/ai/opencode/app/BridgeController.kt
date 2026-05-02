package ai.opencode.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

typealias BridgeCallback = (result: Any?, error: String?) -> Unit

class BridgeController(
    private val context: Context,
    private val webView: WebView
) {
    private val main = Handler(Looper.getMainLooper())
    private val platform = PlatformBridge(context, this)

    val bridgeInterface = BridgeInterface(this)

    fun handleMessage(json: String) {
        try {
            val msg = JSONObject(json)
            val id = msg.getString("id")
            val method = msg.getString("method")
            val params = msg.optJSONObject("params") ?: JSONObject()

            main.post {
                platform.handle(id, method, params) { result, error ->
                    sendResponse(id, result, error)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Bridge", "Failed to parse message: ${e.message}")
        }
    }

    fun sendResponse(id: String, result: Any?, error: String?) {
        val args = JSONArray().apply {
            put(id)
            put(result ?: JSONObject.NULL)
            put(error ?: JSONObject.NULL)
        }
        evaluateJS("window.__OPENCODE_BRIDGE__ && window.__OPENCODE_BRIDGE__.onResponse.apply(null, $args)")
    }

    fun sendEvent(type: String, payload: Any?) {
        val args = JSONArray().apply {
            put(type)
            when (payload) {
                is JSONObject -> put(payload)
                is JSONArray -> put(payload)
                is String -> put(payload)
                is Number -> put(payload)
                is Boolean -> put(payload)
                else -> put(payload ?: JSONObject.NULL)
            }
        }
        evaluateJS("window.__OPENCODE_BRIDGE__ && window.__OPENCODE_BRIDGE__.onEvent.apply(null, $args)")
    }

    private fun evaluateJS(script: String) {
        main.post {
            webView.evaluateJavascript(script, null)
        }
    }

    fun destroy() {
        platform.destroy()
    }
}
