package com.cheminsdhistoire.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import com.cheminsdhistoire.app.data.SettingsStore
import com.cheminsdhistoire.app.model.NarratedStory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Voix neurale via l'API Gemini TTS (clé gratuite de l'utilisateur). Récupère de
 * l'audio PCM 16 bits / 24 kHz et le joue avec [AudioTrack], segment par segment.
 * Reporte la progression du téléchargement (%). Peut pré-synthétiser à l'avance
 * ([prepare]) pour enchaîner sans silence. Repli auto sur la voix du téléphone.
 */
class GeminiVoice(
    private val settings: SettingsStore,
    private val fallback: SpeechManager,
    private val onSegment: (Int) -> Unit,
    private val onFinished: () -> Unit,
    private val onProgress: (String, Float) -> Unit = { _, _ -> }
) : VoiceEngine {

    override val isReady: Boolean get() = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var prepareJob: Job? = null
    private var track: AudioTrack? = null

    @Volatile private var cachedStory: NarratedStory? = null
    @Volatile private var cachedPcm: ByteArray? = null
    private var segmentOffsets: IntArray = IntArray(0)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun speakStory(story: NarratedStory, fromSegment: Int) {
        stop()
        job = scope.launch {
            val pcm = if (story === cachedStory && cachedPcm != null) {
                cachedPcm!!
            } else {
                val fresh = try {
                    synthesize(story) { f -> onProgress("Chargement de la voix", f) }
                } catch (e: Exception) {
                    Log.w(TAG, "Synthèse Gemini échouée: ${e.message}"); null
                }
                if (fresh == null) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        fallback.speakStory(story, fromSegment)
                    }
                    return@launch
                }
                cachedStory = story
                cachedPcm = fresh
                fresh
            }
            computeOffsets(story, pcm.size)
            play(pcm, fromSegment)
        }
    }

    /** Pré-synthétise un récit en fond (sans progression visible) pour éviter les silences. */
    override fun prepare(story: NarratedStory) {
        if (settings.geminiKey.isBlank()) return
        if (story === cachedStory && cachedPcm != null) return
        prepareJob?.cancel()
        prepareJob = scope.launch {
            val pcm = runCatching { synthesize(story) { } }.getOrNull() ?: return@launch
            cachedStory = story
            cachedPcm = pcm
        }
    }

    private fun CoroutineScope.play(pcm: ByteArray, fromSegment: Int) {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        val t = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            max(minBuf, SAMPLE_RATE),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track = t
        try {
            t.play()
            var pos = segmentOffsets.getOrElse(fromSegment) { 0 }.coerceIn(0, pcm.size)
            if (pos % 2 != 0) pos++
            var seg = fromSegment
            onSegment(seg)
            val chunk = 8192
            while (pos < pcm.size && isActive) {
                val end = min(pos + chunk, pcm.size)
                val written = try {
                    t.write(pcm, pos, end - pos)
                } catch (e: Exception) {
                    -1
                }
                if (written <= 0) break
                pos += written
                while (seg + 1 < segmentOffsets.size && pos >= segmentOffsets[seg + 1]) {
                    seg++
                    onSegment(seg)
                }
            }
            if (isActive) onFinished()
        } finally {
            runCatching { t.stop() }
            runCatching { t.release() }
            if (track === t) track = null
        }
    }

    private fun computeOffsets(story: NarratedStory, totalBytes: Int) {
        val lens = story.segments.map { it.length.coerceAtLeast(1) }
        val total = lens.sum().toDouble().coerceAtLeast(1.0)
        val offs = IntArray(lens.size)
        var cum = 0
        for (i in lens.indices) {
            var b = ((cum / total) * totalBytes).toInt()
            if (b % 2 != 0) b++
            offs[i] = b.coerceIn(0, totalBytes)
            cum += lens[i]
        }
        segmentOffsets = offs
    }

    private fun synthesize(story: NarratedStory, onFrac: (Float) -> Unit): ByteArray? {
        val key = settings.geminiKey
        if (key.isBlank()) return null
        val text = "Raconte ce récit d'Histoire avec enthousiasme, chaleur et vivacité :\n\n" +
            story.script

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))
            ))
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("AUDIO"))
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().put("voiceName", VOICE))
                    })
                })
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "$TTS_MODEL:generateContent?key=$key"
        val req = Request.Builder()
            .url(url)
            .header("Accept-Encoding", "identity") // pour obtenir un Content-Length et un vrai %
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body ?: return null
            val total = body.contentLength()
            val stream = body.byteStream()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16384)
            var read = 0L
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                read += n
                if (total > 0) onFrac((read.toFloat() / total).coerceIn(0f, 1f))
            }
            val jsonText = out.toString("UTF-8")
            val parts = JSONObject(jsonText).optJSONArray("candidates")
                ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                ?: return null
            val b64 = StringBuilder()
            for (i in 0 until parts.length()) {
                parts.getJSONObject(i).optJSONObject("inlineData")?.optString("data")?.let {
                    b64.append(it)
                }
            }
            if (b64.isEmpty()) return null
            return Base64.decode(b64.toString(), Base64.DEFAULT)
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        track?.let { t ->
            runCatching { t.pause() }
            runCatching { t.flush() }
            runCatching { t.stop() }
            runCatching { t.release() }
        }
        track = null
    }

    override fun shutdown() {
        prepareJob?.cancel()
        stop()
    }

    companion object {
        private const val TAG = "GeminiVoice"
        private const val SAMPLE_RATE = 24000
        private const val TTS_MODEL = "gemini-2.5-flash-preview-tts"
        private const val VOICE = "Kore"
    }
}
