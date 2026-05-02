package ai.opencode.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.Locale
import android.content.Intent

class VoiceBridge(
    private val context: Context,
    private val controller: BridgeController
) : RecognitionListener {

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var latestText = ""
    private var recording = false
    private var pendingCallback: BridgeCallback? = null
    private var stopTimeout: Runnable? = null

    private var voiceState = "prewarming"
    private var voiceMessage: String? = null

    fun isReady(callback: BridgeCallback) {
        callback(voicePayload(), null)
    }

    fun start(callback: BridgeCallback) {
        if (recording || pendingCallback != null) {
            callback(fail("already_recording", "Voice input is already active."), null)
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            callback(fail("mic_permission_denied", "Microphone permission is required."), null)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            setVoiceState("error", "Speech recognition unavailable.")
            callback(fail("transcription_unavailable", "Speech recognition unavailable."), null)
            setVoiceState("ready")
            return
        }

        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer!!.setRecognitionListener(this)
            }

            latestText = ""
            recording = true
            setVoiceState("recording")

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

            recognizer?.startListening(intent)
            callback(JSONObject().apply { put("ok", true) }, null)
        } catch (_: Throwable) {
            recording = false
            setVoiceState("error", "Failed to start speech recognition.")
            callback(fail("recorder_start_failed", "Failed to start microphone."), null)
            setVoiceState("ready")
        }
    }

    fun stop(callback: BridgeCallback) {
        if (!recording) {
            callback(stopResult("", "not_recording", "Not currently recording."), null)
            return
        }

        recording = false
        pendingCallback = callback
        setVoiceState("processing")

        try {
            recognizer?.stopListening()
        } catch (_: Throwable) {
            finishStop("", "transcription_failed", "Voice transcription failed.")
            return
        }

        val timeout = Runnable {
            val text = latestText.trim()
            if (text.isNotEmpty()) {
                finishStop(text)
            } else {
                finishStop("", "transcription_failed", "Voice transcription failed.")
            }
        }
        stopTimeout = timeout
        main.postDelayed(timeout, 5000)
    }

    fun destroy() {
        recording = false
        pendingCallback = null
        val timeout = stopTimeout
        if (timeout != null) {
            main.removeCallbacks(timeout)
            stopTimeout = null
        }
        try {
            recognizer?.destroy()
        } catch (_: Throwable) {}
        recognizer = null
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}

    override fun onError(error: Int) {
        val reason = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timeout."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy."
            SpeechRecognizer.ERROR_SERVER -> "Speech recognition service error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech recognition timed out."
            else -> "Voice transcription failed."
        }

        if (pendingCallback != null) {
            val text = latestText.trim()
            if (text.isNotEmpty()) {
                finishStop(text)
                return
            }
            finishStop("", "transcription_failed", reason)
            return
        }

        recording = false
        setVoiceState("error", reason)
        setVoiceState("ready")
    }

    override fun onResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

        if (pendingCallback != null) {
            val finalText = if (text.isNotEmpty()) text else latestText.trim()
            if (finalText.isEmpty()) {
                finishStop("", "transcription_failed", "Voice transcription failed.")
                return
            }
            finishStop(finalText)
            return
        }

        if (text.isNotEmpty()) {
            latestText = text
            controller.sendEvent("transcription", JSONObject().apply {
                put("text", text)
                put("isFinal", true)
            })
        }

        recording = false
        setVoiceState("ready")
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (text.isEmpty()) return
        latestText = text
        controller.sendEvent("transcription", JSONObject().apply {
            put("text", text)
            put("isFinal", false)
        })
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun finishStop(text: String, code: String? = null, message: String? = null) {
        val cb = pendingCallback ?: return
        pendingCallback = null

        val timeout = stopTimeout
        if (timeout != null) {
            main.removeCallbacks(timeout)
            stopTimeout = null
        }

        cb(stopResult(text, code, message), null)
        if (code == null) {
            setVoiceState("ready")
        } else {
            setVoiceState("error", message)
            setVoiceState("ready")
        }
    }

    private fun stopResult(text: String, code: String? = null, message: String? = null): JSONObject {
        return JSONObject().apply {
            put("text", text)
            if (code != null) put("code", code)
            if (message != null) put("message", message)
        }
    }

    private fun fail(code: String, message: String): JSONObject {
        return JSONObject().apply {
            put("ok", false)
            put("code", code)
            put("message", message)
        }
    }

    private fun voicePayload(): JSONObject {
        return JSONObject().apply {
            put("state", voiceState)
            put("ready", voiceState == "ready")
            if (!voiceMessage.isNullOrEmpty()) put("message", voiceMessage)
        }
    }

    private fun setVoiceState(state: String, message: String? = null) {
        voiceState = state
        voiceMessage = message
        controller.sendEvent("voiceState", voicePayload())
    }
}
