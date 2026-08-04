package com.cheminsdhistoire.app.data

import com.cheminsdhistoire.app.model.HistoryPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Interroge l'API Wikipedia en français pour trouver les lieux historiques
 * autour d'une position GPS, puis récupère un extrait et une image.
 *
 * API 100% gratuite, sans clé. Source pratique et fiable pour l'Histoire de France.
 */
class WikipediaService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val userAgent =
        "CheminsDHistoire/0.1 (application Android educative; https://github.com/)"

    private val apiBase = "https://fr.wikipedia.org/w/api.php"

    suspend fun nearbyPlaces(
        lat: Double,
        lon: Double,
        radiusMeters: Int = 10_000,
        limit: Int = 20
    ): List<HistoryPlace> = withContext(Dispatchers.IO) {
        val ids = geoSearch(lat, lon, radiusMeters, limit)
        if (ids.isEmpty()) return@withContext emptyList()
        val details = fetchDetails(ids.keys)
        ids.mapNotNull { (pageId, geo) ->
            val d = details[pageId] ?: return@mapNotNull null
            HistoryPlace(
                pageId = pageId,
                title = geo.title,
                lat = geo.lat,
                lon = geo.lon,
                extract = d.extract,
                thumbnailUrl = d.thumbnail,
                pageUrl = d.url,
                distanceMeters = geo.dist
            )
        }.sortedBy { it.distanceMeters }
    }

    private data class GeoHit(val title: String, val lat: Double, val lon: Double, val dist: Double)
    private data class Details(val extract: String, val thumbnail: String?, val url: String)

    private fun geoSearch(lat: Double, lon: Double, radius: Int, limit: Int): Map<Long, GeoHit> {
        val url = apiBase.toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("list", "geosearch")
            .addQueryParameter("gscoord", "$lat|$lon")
            .addQueryParameter("gsradius", radius.coerceAtMost(10_000).toString())
            .addQueryParameter("gslimit", limit.coerceAtMost(50).toString())
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .build()
        val body = get(url.toString()) ?: return emptyMap()
        val out = LinkedHashMap<Long, GeoHit>()
        val arr = JSONObject(body).optJSONObject("query")?.optJSONArray("geosearch") ?: return out
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optLong("pageid", -1)
            if (id < 0) continue
            out[id] = GeoHit(
                title = o.optString("title"),
                lat = o.optDouble("lat"),
                lon = o.optDouble("lon"),
                dist = o.optDouble("dist")
            )
        }
        return out
    }

    private fun fetchDetails(pageIds: Set<Long>): Map<Long, Details> {
        if (pageIds.isEmpty()) return emptyMap()
        val url = apiBase.toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("pageids", pageIds.joinToString("|"))
            .addQueryParameter("prop", "extracts|pageimages|info")
            .addQueryParameter("exsentences", "8")
            .addQueryParameter("explaintext", "1")
            .addQueryParameter("exlimit", "max")
            .addQueryParameter("piprop", "thumbnail")
            .addQueryParameter("pithumbsize", "800")
            .addQueryParameter("inprop", "url")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .build()
        val body = get(url.toString()) ?: return emptyMap()
        val out = HashMap<Long, Details>()
        val pages = JSONObject(body).optJSONObject("query")?.optJSONArray("pages") ?: return out
        for (i in 0 until pages.length()) {
            val p = pages.getJSONObject(i)
            val id = p.optLong("pageid", -1)
            if (id < 0) continue
            val extract = p.optString("extract", "").trim()
            if (extract.isBlank()) continue
            val thumb = p.optJSONObject("thumbnail")?.optString("source")
            val pageUrl = p.optString("fullurl", "https://fr.wikipedia.org/?curid=$id")
            out[id] = Details(extract, thumb, pageUrl)
        }
        return out
    }

    private fun get(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
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
