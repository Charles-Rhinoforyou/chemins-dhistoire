package com.cheminsdhistoire.app.map

import com.cheminsdhistoire.app.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** Transforme un nom de lieu en coordonnées (Nominatim / OpenStreetMap, gratuit). */
class GeocodingService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class Place(val name: String, val point: GeoPoint)

    suspend fun geocode(query: String): Place? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null
        val url = "https://nominatim.openstreetmap.org/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "1")
            .addQueryParameter("countrycodes", "fr")
            .addQueryParameter("accept-language", "fr")
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CheminsDHistoire/0.1 (application Android educative)")
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@withContext null
                val arr = JSONArray(body)
                if (arr.length() == 0) return@withContext null
                val o = arr.getJSONObject(0)
                Place(
                    name = o.optString("display_name").substringBefore(","),
                    point = GeoPoint(o.getDouble("lat"), o.getDouble("lon"))
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
