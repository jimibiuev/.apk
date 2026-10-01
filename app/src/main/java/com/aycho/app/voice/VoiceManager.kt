package com.aycho.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * 语音播报管理器（TTS）
 *
 * 让 Agent 在操作过程中像真人一样开口说话：
 * - 逐条播报旁白（QUEUE_FLUSH，保证新旁白立即打断旧旁白）
 * - 支持随时静音 / 停止
 * - 提供 onSpeakingChanged 回调，用于 UI 显示"正在说话"状态
 */
class VoiceManager(context: Context) {

    companion object {
        private const val TAG = "VoiceManager"
    }

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var enabled = true
    private var pendingText: String? = null

    /** 是否正在播报，供 UI 显示声波状态 */
    var onSpeakingChanged: ((Boolean) -> Unit)? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.CHINA)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "Chinese TTS not available, fallback to default locale")
                        tts?.setLanguage(Locale.getDefault())
                    }
                    tts?.setSpeechRate(1.05f)
                    tts?.setPitch(1.0f)
                    isReady = true
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            onSpeakingChanged?.invoke(true)
                        }

                        override fun onDone(utteranceId: String?) {
                            onSpeakingChanged?.invoke(false)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            onSpeakingChanged?.invoke(false)
                        }
                    })
                    pendingText?.let {
                        pendingText = null
                        speak(it)
                    }
                } else {
                    Log.e(TAG, "TTS init failed: $status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS init exception", e)
        }
    }

    /** 开关语音播报 */
    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) stop()
    }

    /**
     * 播报一句话。新内容会打断正在播报的旧内容，保证对话的自然节奏。
     */
    fun speak(text: String) {
        if (!enabled) return
        val content = text.trim()
        if (content.isEmpty()) return

        if (!isReady) {
            pendingText = content
            return
        }
        try {
            tts?.speak(content, TextToSpeech.QUEUE_FLUSH, null, "aycho_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(TAG, "speak failed", e)
        }
    }

    /** 立即停止播报（用户打断时调用） */
    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        isReady = false
    }
}
