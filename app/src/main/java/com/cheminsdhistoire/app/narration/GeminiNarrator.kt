package com.cheminsdhistoire.app.narration

import com.cheminsdhistoire.app.data.SettingsStore
import com.cheminsdhistoire.app.model.HistoryPlace
import com.cheminsdhistoire.app.model.NarratedStory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Narrateur via l'API Google Gemini, avec la clé GRATUITE de l'utilisateur
 * (Google AI Studio). Récits nettement plus riches. En cas d'absence de clé
 * ou d'erreur → repli automatique sur le [TemplateNarrator].
 */
class GeminiNarrator(
    private val settings: SettingsStore,
    private val fallback: TemplateNarrator = TemplateNarrator(),
    private val onProgress: (String, Float) -> Unit = { _, _ -> }
) : NarrationEngine {

    override val label = "IA Gemini (clé perso)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val model = "gemini-1.5-flash"

    override suspend fun narrate(place: HistoryPlace): NarratedStory = withContext(Dispatchers.IO) {
        val key = settings.geminiKey
        if (key.isBlank()) return@withContext fallback.narrate(place)
        try {
            val script = requestNarration(place, key)?.trim()
            if (script.isNullOrBlank() || script.length < 60) {
                return@withContext fallback.narrate(place)
            }
            val clean = script.replace(Regex("\\s+"), " ").trim()
            NarratedStory(
                place = place,
                title = place.title,
                script = clean,
                segments = NarrationUtils.toSegments(clean),
                imageUrl = place.thumbnailUrl,
                sourceUrl = place.pageUrl
            )
        } catch (e: Exception) {
            fallback.narrate(place)
        }
    }

    private fun requestNarration(place: HistoryPlace, key: String): String? {
        val dist = NarrationUtils.formatDistance(place.distanceMeters)
        val prompt = """
            Tu es un narrateur de podcast d'Histoire de France, passionné et vivant, qui s'adresse
            à un public jeune. Ton enthousiaste et rythmé, avec UNE seule pointe d'humour légère
            (jamais moqueur). 160 à 230 mots. Reste STRICTEMENT dans le contexte du lieu fourni,
            n'invente aucun fait, aucune date non fournie. Écris uniquement le texte à lire à voix
            haute, sans titre, sans liste, sans balise.

            Lieu : ${place.title} (à $dist de l'auditeur).
            Informations vérifiées (source Wikipédia) :
            "${place.extract}"
        """.trimIndent()

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.9)
                put("maxOutputTokens", 700)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
        val req = Request.Builder()
            .url(url)
            .header("Accept-Encoding", "identity")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val respBody = resp.body ?: return null
            val total = respBody.contentLength()
            val stream = respBody.byteStream()
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var readSum = 0L
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                readSum += n
                if (total > 0) onProgress("Écriture du récit", (readSum.toFloat() / total).coerceIn(0f, 1f))
            }
            val body = out.toString("UTF-8")
            val candidates = JSONObject(body).optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")?.optJSONArray("parts") ?: return null
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.getJSONObject(i).optString("text"))
            }
            return sb.toString()
        }
    }
}
