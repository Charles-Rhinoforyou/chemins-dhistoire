package com.cheminsdhistoire.app.data

import com.cheminsdhistoire.app.location.LocationProvider
import com.cheminsdhistoire.app.model.HistoryPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Base Mérimée (Monuments Historiques français) via la plateforme open data
 * Opendatasoft — source spécialisée, officielle et géolocalisée, avec un vrai
 * texte historique. Gratuite, sans clé.
 */
class MerimeeService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val base = "https://data.opendatasoft.com/api/explore/v2.1/catalog/datasets/" +
        "liste-des-immeubles-proteges-au-titre-des-monuments-historiques@culture/records"

    suspend fun nearby(
        lat: Double,
        lon: Double,
        radiusMeters: Int = 10_000,
        limit: Int = 25
    ): List<HistoryPlace> = withContext(Dispatchers.IO) {
        val km = (radiusMeters / 1000.0).coerceIn(1.0, 20.0)
        val url = base.toHttpUrl().newBuilder()
            .addQueryParameter(
                "where",
                "within_distance(coordonnees_au_format_wgs84, geom'POINT($lon $lat)', ${km}km)"
            )
            .addQueryParameter(
                "select",
                "titre_editorial_de_la_notice,historique,format_abrege_du_siecle_de_construction," +
                    "commune_forme_editoriale,coordonnees_au_format_wgs84,reference_de_la_notice"
            )
            .addQueryParameter("limit", limit.coerceAtMost(50).toString())
            .build()

        val body = get(url.toString()) ?: return@withContext emptyList()
        val out = ArrayList<HistoryPlace>()
        val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
        for (i in 0 until results.length()) {
            val o = results.getJSONObject(i)
            val title = o.optString("titre_editorial_de_la_notice").trim()
            val historique = o.optString("historique").trim()
            if (title.isBlank() || historique.length < 40) continue
            val coord = o.optJSONObject("coordonnees_au_format_wgs84") ?: continue
            val plat = coord.optDouble("lat", Double.NaN)
            val plon = coord.optDouble("lon", Double.NaN)
            if (plat.isNaN() || plon.isNaN()) continue

            val century = o.optString("format_abrege_du_siecle_de_construction").trim()
            val ref = o.optString("reference_de_la_notice").trim()
            val commune = o.optString("commune_forme_editoriale").trim()

            val extract = buildString {
                append("Monument historique")
                if (commune.isNotBlank()) append(" à $commune")
                if (century.isNotBlank()) append(" ($century)")
                append(". ")
                append(cleanHtml(historique))
            }
            val pageUrl = if (ref.isNotBlank())
                "https://www.pop.culture.gouv.fr/notice/merimee/$ref"
            else "https://www.pop.culture.gouv.fr/"

            out.add(
                HistoryPlace(
                    // Identifiant synthétique positif, hors plage des pageid Wikipedia.
                    pageId = 2_000_000_000L + abs((ref.ifBlank { title }).hashCode()),
                    title = title,
                    lat = plat,
                    lon = plon,
                    extract = extract,
                    thumbnailUrl = null,
                    pageUrl = pageUrl,
                    distanceMeters = LocationProvider.distanceMeters(lat, lon, plat, plon)
                )
            )
        }
        out.sortedBy { it.distanceMeters }
    }

    private fun cleanHtml(s: String): String =
        s.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()

    private fun get(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CheminsDHistoire/0.1 (application Android educative)")
            .header("Accept", "application/json")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (e: Exception) {
            null
        }
    }
}
