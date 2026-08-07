package com.cheminsdhistoire.app.data

import com.cheminsdhistoire.app.location.LocationProvider
import com.cheminsdhistoire.app.model.HistoryPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.Normalizer

/**
 * Agrège plusieurs sources d'Histoire géolocalisées et les croise :
 *  - Wikipédia (texte riche + images) ;
 *  - base Mérimée / Monuments Historiques (patrimoine officiel français géolocalisé).
 *
 * Wikipédia reste prioritaire (image + récit) ; Mérimée ajoute les monuments officiels
 * absents de la recherche Wikipédia et enrichit la couverture. Dédoublonnage par
 * similarité de nom et proximité géographique.
 */
class PlacesRepository(
    private val wikipedia: WikipediaService = WikipediaService(),
    private val merimee: MerimeeService = MerimeeService()
) {
    suspend fun nearby(
        lat: Double,
        lon: Double,
        radiusMeters: Int = 10_000,
        limit: Int = 20
    ): List<HistoryPlace> = withContext(Dispatchers.IO) {
        val (wiki, mer) = coroutineScope {
            val w = async { runCatching { wikipedia.nearbyPlaces(lat, lon, radiusMeters, limit) }.getOrDefault(emptyList()) }
            val m = async { runCatching { merimee.nearby(lat, lon, radiusMeters, limit) }.getOrDefault(emptyList()) }
            w.await() to m.await()
        }

        val merged = wiki.toMutableList()
        mer.forEach { m ->
            val duplicate = wiki.any { w -> isSameMonument(m, w) }
            if (!duplicate) merged.add(m)
        }
        merged.sortedBy { it.distanceMeters }
    }

    private fun isSameMonument(a: HistoryPlace, b: HistoryPlace): Boolean {
        val dist = LocationProvider.distanceMeters(a.lat, a.lon, b.lat, b.lon)
        if (dist < 80) return true
        val na = normalize(a.title)
        val nb = normalize(b.title)
        if (na.isBlank() || nb.isBlank()) return false
        if (na.contains(nb) || nb.contains(na)) return dist < 1500
        // Chevauchement de mots significatifs + proximité.
        val overlap = na.split(' ').filter { it.length >= 5 }
            .intersect(nb.split(' ').filter { it.length >= 5 }.toSet())
        return overlap.isNotEmpty() && dist < 400
    }

    private fun normalize(s: String): String {
        val noAccents = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return noAccents.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\b(le|la|les|de|du|des|d|l|et|a)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
