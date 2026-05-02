package ai.opencode.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class MainActivity : AppCompatActivity() {

    internal lateinit var webView: WebView
    private lateinit var bridgeController: BridgeController

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        webView = WebView(this)
        setContentView(webView)

        bridgeController = BridgeController(this, webView)

        setupBackHandler()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(arrayOf("android.permission.POST_NOTIFICATIONS"))
            }
        }

        loadContent()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadContent() {
        WebView.setWebContentsDebuggingEnabled(true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        }

        webView.addJavascriptInterface(bridgeController.bridgeInterface, "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val scheme = request.url.scheme
                if (scheme == "http" || scheme == "https" || scheme == "file") {
                    return false
                }
                try {
                    val intent = Intent(Intent.ACTION_VIEW, request.url)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (_: Exception) {}
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(
                    "WebView",
                    "${consoleMessage.messageLevel()}: ${consoleMessage.message()} " +
                        "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                )
                return true
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, true, false)
            }
        }

        val useDevServer = BuildConfig.DEBUG &&
            System.getenv("WHISPERCODE_USE_DEV_SERVER") == "1"

        if (useDevServer) {
            webView.loadUrl("http://localhost:1422")
        } else {
            webView.loadUrl("file:///android_asset/WebAssets/index.html")
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        bridgeController.sendEvent("appLifecycle", mapOf("state" to "active"))
    }

    override fun onPause() {
        super.onPause()
        bridgeController.sendEvent("appLifecycle", mapOf("state" to "inactive"))
    }

    override fun onDestroy() {
        bridgeController.destroy()
        webView.destroy()
        super.onDestroy()
    }
}
