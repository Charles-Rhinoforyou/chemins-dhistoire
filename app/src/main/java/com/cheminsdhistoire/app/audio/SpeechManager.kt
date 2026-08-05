package com.cheminsdhistoire.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.cheminsdhistoire.app.model.NarratedStory
import java.util.Locale

/**
 * Enveloppe la synthèse vocale (TextToSpeech) du téléphone.
 * Lit un récit segment par segment et signale la progression, ce qui permet
 * de mettre en pause puis de reprendre exactement là où on s'est arrêté.
 */
class SpeechManager(
    context: Context,
    private val onReady: (Boolean) -> Unit,
    private val onSegment: (Int) -> Unit,
    private val onFinished: () -> Unit
) : VoiceEngine {
    private var tts: TextToSpeech? = null
    private var ready = false

    private var segments: List<String> = emptyList()
    private var baseIndex = 0

    override val isReady: Boolean get() = ready

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val res = tts?.setLanguage(Locale.FRANCE)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.FRENCH)
                }
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.05f) // intonation légèrement vive
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        utteranceId?.toIntOrNull()?.let { onSegment(it) }
                    }

                    override fun onDone(utteranceId: String?) {
                        val idx = utteranceId?.toIntOrNull() ?: return
                        // L'identifiant est l'index ABSOLU du segment : fini au dernier segment,
                        // quel que soit le point de reprise après une pause.
                        if (idx >= segments.size - 1) onFinished()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { /* ignoré, on continue */ }
                })
            }
            onReady(ready)
        }
    }

    /** Lit un récit à partir d'un segment donné (0 par défaut). */
    override fun speakStory(story: NarratedStory, fromSegment: Int) {
        val engine = tts ?: return
        segments = story.segments
        baseIndex = fromSegment.coerceIn(0, (segments.size - 1).coerceAtLeast(0))
        engine.stop()
        for (i in baseIndex until segments.size) {
            val mode = if (i == baseIndex) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(segments[i], mode, null, i.toString())
        }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
