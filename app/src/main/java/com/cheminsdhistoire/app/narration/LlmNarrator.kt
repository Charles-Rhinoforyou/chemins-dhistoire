package com.cheminsdhistoire.app.narration

import android.content.Context
import android.util.Log
import com.cheminsdhistoire.app.model.HistoryPlace
import com.cheminsdhistoire.app.model.NarratedStory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Narrateur par IA générative locale tournant SUR le téléphone (MediaPipe LLM Inference,
 * modèle Gemma). Le modèle (.task/.bin) doit être déposé dans /files/models/.
 *
 * Accès par réflexion : l'application compile et fonctionne toujours, même sans la
 * librairie chargée ni le modèle présent. En cas d'absence ou d'échec → repli
 * automatique sur le [TemplateNarrator].
 */
class LlmNarrator(
    private val appContext: Context,
    private val fallback: TemplateNarrator = TemplateNarrator()
) : NarrationEngine {

    override val label = "IA locale (Gemma)"

    @Volatile
    private var engine: Any? = null

    override suspend fun narrate(place: HistoryPlace): NarratedStory = withContext(Dispatchers.IO) {
        val model = findModel()
        if (model == null) {
            return@withContext fallback.narrate(place)
        }
        try {
            val llm = engine ?: createEngine(model.absolutePath).also { engine = it }
            val prompt = buildPrompt(place)
            val raw = generate(llm, prompt).trim()
            if (raw.length < 60) return@withContext fallback.narrate(place)
            val script = raw.replace(Regex("\\s+"), " ").trim()
            NarratedStory(
                place = place,
                title = place.title,
                script = script,
                segments = NarrationUtils.toSegments(script),
                imageUrl = place.thumbnailUrl,
                sourceUrl = place.pageUrl
            )
        } catch (e: Throwable) {
            Log.w(TAG, "LLM indisponible, repli sur le narrateur local : ${e.message}")
            fallback.narrate(place)
        }
    }

    fun isModelPresent(): Boolean = findModel() != null

    private fun findModel(): File? {
        val dir = File(appContext.filesDir, "models")
        if (!dir.isDirectory) return null
        return dir.listFiles { f ->
            f.isFile && (f.name.endsWith(".task") || f.name.endsWith(".bin"))
        }?.firstOrNull()
    }

    private fun buildPrompt(place: HistoryPlace): String {
        val dist = NarrationUtils.formatDistance(place.distanceMeters)
        return """
            Tu es un narrateur de podcast d'Histoire de France, passionné et vivant.
            Public : jeunes. Ton : enthousiaste, rythmé, avec UNE pointe d'humour légère
            (pas plus), jamais moqueur. Longueur : 150 à 220 mots. Reste STRICTEMENT
            dans le contexte du lieu ci-dessous, n'invente aucun fait.

            Lieu : ${place.title} (à $dist de l'auditeur).
            Informations vérifiées :
            "${place.extract}"

            Écris le texte du récit à lire à voix haute, sans titre ni liste.
        """.trimIndent()
    }

    // --- Accès MediaPipe par réflexion (aucune dépendance de compilation dure) ---

    private fun createEngine(modelPath: String): Any {
        val llmCls = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference")
        val optCls =
            Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions")
        var builder = optCls.getMethod("builder").invoke(null)
        val bCls = builder.javaClass
        builder = bCls.getMethod("setModelPath", String::class.java).invoke(builder, modelPath)
        builder = bCls.getMethod("setMaxTokens", Int::class.javaPrimitiveType)
            .invoke(builder, 512)
        builder = bCls.getMethod("setTemperature", Float::class.javaPrimitiveType)
            .invoke(builder, 0.8f)
        builder = bCls.getMethod("setTopK", Int::class.javaPrimitiveType).invoke(builder, 40)
        val options = bCls.getMethod("build").invoke(builder)
        return llmCls.getMethod("createFromOptions", Context::class.java, optCls)
            .invoke(null, appContext, options)
    }

    private fun generate(llm: Any, prompt: String): String {
        val m = llm.javaClass.getMethod("generateResponse", String::class.java)
        return m.invoke(llm, prompt) as String
    }

    companion object {
        private const val TAG = "LlmNarrator"
    }
}
