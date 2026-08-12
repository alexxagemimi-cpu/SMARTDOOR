package com.exhibition.smartdoorlock.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Speaks prompts using the on-device Android TextToSpeech engine. The Arduino never
 * speaks directly — DETECTED is just a signal; converting it into the spoken prompt
 * happens entirely here, one level away from both Bluetooth and UI code, so the
 * speech backend is easy to replace later without touching either.
 *
 * If TTS is still initializing when speak() is called (e.g. DETECTED arrives right
 * after connecting), the text is queued and spoken as soon as init finishes rather
 * than silently dropped.
 */
class TextToSpeechManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                tts?.language = Locale.getDefault().takeIf {
                    tts?.isLanguageAvailable(it) == TextToSpeech.LANG_AVAILABLE ||
                        tts?.isLanguageAvailable(it) == TextToSpeech.LANG_COUNTRY_AVAILABLE
                } ?: Locale.US
                pendingText?.let { text ->
                    pendingText = null
                    speakInternal(text)
                }
            }
        }
    }

    fun speak(text: String) {
        if (isReady) {
            speakInternal(text)
        } else {
            pendingText = text
        }
    }

    private fun speakInternal(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "door_prompt")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
