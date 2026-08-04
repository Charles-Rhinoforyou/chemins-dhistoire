package com.cheminsdhistoire.app.data

import android.content.Context
import com.cheminsdhistoire.app.model.JourneyEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persiste les récits sauvegardés dans un simple fichier JSON local.
 * Permet de revenir plus tard sur le contenu de son parcours, hors-ligne.
 */
class JourneyStore(context: Context) {

    private val file = File(context.filesDir, "journeys.json")

    suspend fun load(): List<JourneyEntry> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                JourneyEntry(
                    title = o.optString("title"),
                    script = o.optString("script"),
                    imageUrl = o.optString("imageUrl").ifBlank { null },
                    sourceUrl = o.optString("sourceUrl"),
                    lat = o.optDouble("lat"),
                    lon = o.optDouble("lon"),
                    savedAt = o.optLong("savedAt")
                )
            }.sortedByDescending { it.savedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun add(entry: JourneyEntry): List<JourneyEntry> = withContext(Dispatchers.IO) {
        val current = load().toMutableList()
        // Évite les doublons (même titre déjà sauvegardé).
        if (current.none { it.title == entry.title }) {
            current.add(0, entry)
            persist(current)
        }
        current
    }

    suspend fun remove(entry: JourneyEntry): List<JourneyEntry> = withContext(Dispatchers.IO) {
        val current = load().filterNot { it.title == entry.title && it.savedAt == entry.savedAt }
        persist(current)
        current
    }

    private fun persist(entries: List<JourneyEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("title", e.title)
                    .put("script", e.script)
                    .put("imageUrl", e.imageUrl ?: "")
                    .put("sourceUrl", e.sourceUrl)
                    .put("lat", e.lat)
                    .put("lon", e.lon)
                    .put("savedAt", e.savedAt)
            )
        }
        file.writeText(arr.toString())
    }
}
