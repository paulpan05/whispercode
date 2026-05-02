package ai.opencode.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class StorageBridge(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("opencode_storage", Context.MODE_PRIVATE)

    fun getDefaultServerUrl(callback: BridgeCallback) {
        val url = prefs.getString("defaultServerUrl", null)
        callback(url, null)
    }

    fun setDefaultServerUrl(params: JSONObject, callback: BridgeCallback) {
        val url = params.optString("url", "")
        if (url.isNotEmpty()) {
            prefs.edit().putString("defaultServerUrl", url).apply()
        } else {
            prefs.edit().remove("defaultServerUrl").apply()
        }
        callback(null, null)
    }

    fun get(params: JSONObject, callback: BridgeCallback) {
        val name = params.optString("name", "default.dat")
        val key = params.optString("key", "")
        val value = prefs.getString(storageKey(name, key), null)
        callback(value, null)
    }

    fun set(params: JSONObject, callback: BridgeCallback) {
        val name = params.optString("name", "default.dat")
        val key = params.optString("key", "")
        val value = params.optString("value", "")
        prefs.edit().putString(storageKey(name, key), value).apply()
        callback(null, null)
    }

    fun remove(params: JSONObject, callback: BridgeCallback) {
        val name = params.optString("name", "default.dat")
        val key = params.optString("key", "")
        prefs.edit().remove(storageKey(name, key)).apply()
        callback(null, null)
    }

    fun clear(params: JSONObject, callback: BridgeCallback) {
        val name = params.optString("name", "default.dat")
        val prefix = "$name:"
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith(prefix)) {
                editor.remove(key)
            }
        }
        editor.apply()
        callback(null, null)
    }

    fun key(params: JSONObject, callback: BridgeCallback) {
        val name = params.optString("name", "default.dat")
        val index = params.optInt("index", -1)
        if (index < 0) {
            callback(null, null)
            return
        }
        val prefix = "$name:"
        val keys = prefs.all.keys.filter { it.startsWith(prefix) }.sorted()
        val result = if (index < keys.size) {
            keys[index].removePrefix(prefix)
        } else null
        callback(result, null)
    }

    fun length(params: JSONObject, callback: BridgeCallback) {
        val name = params.optString("name", "default.dat")
        val prefix = "$name:"
        val count = prefs.all.keys.count { it.startsWith(prefix) }
        callback(count, null)
    }

    private fun storageKey(name: String, key: String): String {
        return "$name:$key"
    }
}
