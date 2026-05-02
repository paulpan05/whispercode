package ai.opencode.app

import android.webkit.JavascriptInterface

class BridgeInterface(
    private val controller: BridgeController
) {
    @JavascriptInterface
    fun postMessage(json: String) {
        controller.handleMessage(json)
    }
}
