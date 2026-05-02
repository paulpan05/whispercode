package ai.opencode.app

import android.app.Application

class WhisperCodeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile
        private lateinit var instance: WhisperCodeApp

        fun get(): WhisperCodeApp = instance
    }
}
