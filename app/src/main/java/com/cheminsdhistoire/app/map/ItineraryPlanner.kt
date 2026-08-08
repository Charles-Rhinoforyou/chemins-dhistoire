package com.cheminsdhistoire.app.map

import com.cheminsdhistoire.app.data.WikipediaService
import com.cheminsdhistoire.app.model.GeoPoint
import com.cheminsdhistoire.app.model.HistoryPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt

data class Itinerary(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val destinationName: String,
    val stops: List<HistoryPlace>
)

/**
 * Construit un itinéraire « scénique » : parmi les lieux historiques situés dans le
 * couloir entre le départ et la destination, retient les plus intéressants puis les
 * ordonne dans le sens de la route (pas de retour en arrière).
 */
class ItineraryPlanner(private val wiki: WikipediaService) {

    suspend fun plan(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String,
        era: Era = Era.TOUTES,
        themes: Set<Theme> = emptySet(),
        maxStops: Int = 8,
        corridorHalfWidthMeters: Double = 12_000.0
    ): Itinerary = withContext(Dispatchers.IO) {
        val lat0 = Math.toRadians(origin.lat)
        val r = 6_371_000.0
        fun toXY(p: GeoPoint): Pair<Double, Double> {
            val x = Math.toRadians(p.lon - origin.lon) * Math.cos(lat0) * r
            val y = Math.toRadians(p.lat - origin.lat) * r
            return x to y
        }

        val (dx, dy) = toXY(destination)
        val lenSq = dx * dx + dy * dy
        val totalMeters = kotlin.math.sqrt(lenSq)

        // Échantillonne des points le long de la ligne départ -> destination.
        val samples = (totalMeters / 15_000.0).roundToInt().coerceIn(3, 10)
        val candidates = LinkedHashMap<Long, HistoryPlace>()
        for (i in 0..samples) {
            val f = i.toDouble() / samples
            val lat = origin.lat + f * (destination.lat - origin.lat)
            val lon = origin.lon + f * (destination.lon - origin.lon)
            wiki.nearbyPlaces(lat, lon, radiusMeters = 8_000, limit = 8).forEach { p ->
                candidates.putIfAbsent(p.pageId, p)
            }
        }

        data class Scored(val place: HistoryPlace, val t: Double, val score: Double, val distToDest: Double)

        val scored = candidates.values.mapNotNull { p ->
            if (!EraClassifier.matches(p, era)) return@mapNotNull null
            if (!ThemeClassifier.matches(p, themes)) return@mapNotNull null
            val (px, py) = toXY(GeoPoint(p.lat, p.lon))
            val t = if (lenSq == 0.0) 0.0 else (px * dx + py * dy) / lenSq
            val perp = hypot(px - t * dx, py - t * dy)
            if (t < -0.1 || t > 1.1 || perp > corridorHalfWidthMeters) return@mapNotNull null
            val interest = (if (p.thumbnailUrl != null) 2.0 else 0.0) +
                (p.extract.length / 150.0).coerceAtMost(5.0)
            val distToDest = hypot(px - dx, py - dy)
            Scored(p, t.coerceIn(0.0, 1.0), interest, distToDest)
        }

        val best = if (scored.isEmpty()) {
            emptyList()
        } else {
            // La DERNIÈRE étape est le lieu le plus proche de la destination.
            val finalStop = scored.minByOrNull { it.distToDest }!!
            // Les étapes intermédiaires (avant lui) sont choisies par intérêt, ordonnées le long de la route.
            val middle = scored
                .filter { it !== finalStop && it.t < finalStop.t }
                .sortedByDescending { it.score }
                .take((maxStops - 1).coerceAtLeast(0))
                .sortedBy { it.t }
            middle.map { it.place } + finalStop.place
        }

        Itinerary(origin, destination, destinationName, best)
    }
}
