package com.example.service

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import com.example.util.BackgroundSettingsHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Locale

interface VoiceSpeaker {
    fun speak(message: String, onError: ((String) -> Unit)? = null)
    fun shutdown()
}

class AndroidTextToSpeechSpeaker(private val appContext: Context) : VoiceSpeaker {
    @Volatile
    private var tts: TextToSpeech? = null
    @Volatile
    private var isReady: Boolean = false
    @Volatile
    private var pendingPhrase: String? = null
    @Volatile
    private var pendingErrorCallback: ((String) -> Unit)? = null

    override fun speak(message: String, onError: ((String) -> Unit)?) {
        val current = tts
        if (current != null && isReady) {
            speakNow(current, message, onError)
            return
        }

        pendingPhrase = message
        pendingErrorCallback = onError

        if (current == null) {
            try {
                lateinit var created: TextToSpeech
                created = TextToSpeech(appContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val langResult = created.setLanguage(Locale.US)
                        if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                            langResult == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            created.setLanguage(Locale.getDefault())
                        }
                        created.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        isReady = true
                        val phrase = pendingPhrase
                        val errCb = pendingErrorCallback
                        pendingPhrase = null
                        pendingErrorCallback = null
                        if (!phrase.isNullOrBlank()) {
                            speakNow(created, phrase, errCb)
                        }
                    } else {
                        isReady = false
                        tts = null
                        runCatching { created.shutdown() }
                        val errCb = pendingErrorCallback
                        pendingPhrase = null
                        pendingErrorCallback = null
                        errCb?.invoke("Android Text-to-Speech failed to initialize (code $status)")
                    }
                }
                tts = created
            } catch (e: Exception) {
                isReady = false
                tts = null
                pendingPhrase = null
                pendingErrorCallback = null
                onError?.invoke(e.message ?: "Unable to start Android Text-to-Speech")
            }
        }
    }

    private fun speakNow(engine: TextToSpeech, text: String, onError: ((String) -> Unit)?) {
        val result = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pocketcode_done_${System.currentTimeMillis()}")
        }.getOrElse {
            onError?.invoke(it.message ?: "Text-to-Speech error")
            return
        }
        if (result == TextToSpeech.ERROR) {
            onError?.invoke("Text-to-Speech could not speak message")
        }
    }

    override fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        isReady = false
        pendingPhrase = null
        pendingErrorCallback = null
    }
}

class AgentBackgroundCoordinator(
    private val appContext: Context? = null,
    private val voiceSpeaker: VoiceSpeaker? = appContext?.let { AndroidTextToSpeechSpeaker(it.applicationContext) },
    private val serviceStarter: (Context) -> Unit = { ctx ->
        val intent = Intent(ctx, AgentForegroundService::class.java).apply {
            action = AgentForegroundService.ACTION_START_WORKING
        }
        runCatching { ContextCompat.startForegroundService(ctx, intent) }
    },
    private val serviceStopper: (Context) -> Unit = { ctx ->
        val stopIntent = Intent(ctx, AgentForegroundService::class.java).apply {
            action = AgentForegroundService.ACTION_STOP_WORKING
        }
        runCatching { ctx.stopService(stopIntent) }
    },
    private val completionNotifier: (Context, String?) -> Unit = { ctx, sessionName ->
        AgentForegroundService.showTaskCompletedNotification(ctx, sessionName)
    },
    private val deviceLockedProvider: (Context) -> Boolean = { ctx ->
        BackgroundSettingsHelper.isDeviceLockedOrScreenOff(ctx)
    }
) {
    @Volatile
    private var isAppForeground: Boolean = true

    @Volatile
    private var isRunActive: Boolean = false

    @Volatile
    private var isForegroundServiceActive: Boolean = false

    private val _taskCompletedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val taskCompletedEvents: SharedFlow<Unit> = _taskCompletedEvents.asSharedFlow()

    fun onAppForegrounded() {
        isAppForeground = true
        val ctx = appContext ?: return
        if (!deviceLockedProvider(ctx) && isForegroundServiceActive) {
            stopWorkingNotification(ctx)
        }
    }

    fun onAppLeavingOrBackgrounded() {
        isAppForeground = false
        val ctx = appContext ?: return
        if (isRunActive) {
            startWorkingNotification(ctx)
        }
    }

    fun onActivityPaused() {
        val ctx = appContext ?: return
        if (isRunActive && deviceLockedProvider(ctx)) {
            startWorkingNotification(ctx)
        }
    }

    fun onRunStarted() {
        isRunActive = true
        val ctx = appContext ?: return
        if (!isAppForeground || deviceLockedProvider(ctx)) {
            startWorkingNotification(ctx)
        }
    }

    fun onRunFinished(
        voiceEnabled: Boolean,
        wasCancelled: Boolean,
        sessionName: String? = null,
        wasError: Boolean = false
    ) {
        isRunActive = false
        val ctx = appContext
        if (ctx != null && isForegroundServiceActive) {
            stopWorkingNotification(ctx)
        }

        val isBackgroundOrLocked = if (ctx != null) {
            !isAppForeground || deviceLockedProvider(ctx)
        } else {
            !isAppForeground
        }

        if (!wasCancelled && !wasError) {
            _taskCompletedEvents.tryEmit(Unit)
            if (isBackgroundOrLocked) {
                if (ctx != null) {
                    completionNotifier(ctx, sessionName)
                }
                if (voiceEnabled) {
                    voiceSpeaker?.speak(VOICE_COMPLETION_MESSAGE, null)
                }
            }
        }
    }

    fun testVoice(onError: ((String) -> Unit)? = null) {
        val speaker = voiceSpeaker
        if (speaker == null) {
            onError?.invoke("Text-to-Speech unavailable")
            return
        }
        speaker.speak(VOICE_COMPLETION_MESSAGE, onError)
    }

    fun shutdown() {
        voiceSpeaker?.shutdown()
    }

    private fun startWorkingNotification(ctx: Context) {
        if (!isForegroundServiceActive) {
            isForegroundServiceActive = true
            serviceStarter(ctx)
        }
    }

    private fun stopWorkingNotification(ctx: Context) {
        isForegroundServiceActive = false
        serviceStopper(ctx)
    }

    companion object {
        const val VOICE_COMPLETION_MESSAGE = "Your task is completed, sir."
    }
}
