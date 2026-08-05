package com.cheminsdhistoire.app.map

import com.cheminsdhistoire.app.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Un tracé routier réel : la géométrie qui suit les routes + distance + durée. */
data class RoadRoute(
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double
)

/**
 * Calcule un itinéraire routier via OSRM (serveur public d'OpenStreetMap, gratuit, sans clé).
 * Passe par tous les points d'étape dans l'ordre fourni.
 */
class RoutingService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun route(waypoints: List<GeoPoint>): RoadRoute? = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext null
        val coords = waypoints.joinToString(";") { "${it.lon},${it.lat}" }
        val url = "https://router.project-osrm.org/route/v1/driving/$coords" +
            "?overview=full&geometries=geojson"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CheminsDHistoire/0.1 (application Android educative)")
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                if (json.optString("code") != "Ok") return@withContext null
                val routes = json.optJSONArray("routes") ?: return@withContext null
                if (routes.length() == 0) return@withContext null
                val route = routes.getJSONObject(0)
                val coordsArr = route.getJSONObject("geometry").getJSONArray("coordinates")
                val pts = ArrayList<GeoPoint>(coordsArr.length())
                for (i in 0 until coordsArr.length()) {
                    val c = coordsArr.getJSONArray(i)
                    // GeoJSON = [lon, lat]
                    pts.add(GeoPoint(c.getDouble(1), c.getDouble(0)))
                }
                RoadRoute(
                    points = pts,
                    distanceMeters = route.optDouble("distance", 0.0),
                    durationSeconds = route.optDouble("duration", 0.0)
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
