package com.cheminsdhistoire.app.narration

import com.cheminsdhistoire.app.model.HistoryPlace
import com.cheminsdhistoire.app.model.NarratedStory

/** Un moteur capable de transformer un lieu en récit raconté. */
interface NarrationEngine {
    val label: String
    suspend fun narrate(place: HistoryPlace): NarratedStory
}

object NarrationUtils {

    /** Découpe un texte en segments courts (≈ une phrase) pour la lecture et le suivi. */
    fun toSegments(text: String): List<String> {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return emptyList()
        val parts = Regex("(?<=[.!?…])\\s+").split(clean)
        val out = ArrayList<String>()
        val buffer = StringBuilder()
        for (p in parts) {
            val s = p.trim()
            if (s.isEmpty()) continue
            if (buffer.isEmpty()) {
                buffer.append(s)
            } else if (buffer.length + s.length < 160) {
                buffer.append(' ').append(s)
            } else {
                out.add(buffer.toString())
                buffer.setLength(0)
                buffer.append(s)
            }
        }
        if (buffer.isNotEmpty()) out.add(buffer.toString())
        return out
    }

    fun formatDistance(meters: Double): String {
        return if (meters < 950) "${Math.round(meters / 10.0) * 10} mètres"
        else String.format("%.1f km", meters / 1000.0).replace('.', ',')
    }
}
