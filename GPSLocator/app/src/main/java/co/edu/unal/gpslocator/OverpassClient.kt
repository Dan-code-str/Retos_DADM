package co.edu.unal.gpslocator

import android.util.Log
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Consulta puntos de interés de OpenStreetMap con la Overpass API.
 * Las llamadas son bloqueantes: ejecútelas fuera del hilo principal.
 */
object OverpassClient {

    private val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )
    private const val USER_AGENT = "GPSLocator/1.0 (Android; curso UNAL)"
    private const val MAX_RESULTS = 150
    private const val TAG = "OverpassClient"

    fun search(center: GeoPoint, radiusMeters: Double, categories: List<PoiCategory>): List<Poi> {
        if (categories.isEmpty()) return emptyList()
        val query = buildQuery(center, radiusMeters, categories)
        var lastError: Exception? = null
        for (endpoint in endpoints) {
            try {
                return parse(post(endpoint, query), center, radiusMeters, categories)
            } catch (e: Exception) {
                Log.w(TAG, "Falló $endpoint", e)
                lastError = e
            }
        }
        throw lastError ?: IOException("Sin respuesta de Overpass")
    }

    private fun buildQuery(center: GeoPoint, radiusMeters: Double, categories: List<PoiCategory>): String {
        val around = String.format(
            Locale.US, "(around:%d,%.6f,%.6f)",
            radiusMeters.roundToInt(), center.latitude, center.longitude,
        )
        val sb = StringBuilder("[out:json][timeout:25];(")
        for (category in categories) {
            for ((key, values) in category.tags) {
                sb.append("nwr[\"name\"][\"").append(key).append("\"~\"^(")
                    .append(values.joinToString("|"))
                    .append(")\$\"]").append(around).append(';')
            }
        }
        sb.append(");out tags center;")
        return sb.toString()
    }

    private fun post(endpoint: String, query: String): String {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.outputStream.use {
                it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("Overpass respondió HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(
        json: String,
        center: GeoPoint,
        radiusMeters: Double,
        categories: List<PoiCategory>,
    ): List<Poi> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val seen = HashSet<String>()
        val result = ArrayList<Poi>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: continue
            val name = tags.optString("name").trim()
            if (name.isEmpty()) continue

            // Los nodos traen lat/lon; las vías y relaciones traen "center".
            val coords = if (el.has("lat")) el else el.optJSONObject("center") ?: continue
            val lat = coords.optDouble("lat")
            val lon = coords.optDouble("lon")
            if (lat.isNaN() || lon.isNaN()) continue

            val category = categories.firstOrNull { cat ->
                cat.tags.any { (key, values) -> tags.optString(key) in values }
            } ?: continue

            val point = GeoPoint(lat, lon)
            val distance = center.distanceToAsDouble(point)
            if (distance > radiusMeters) continue

            // Evita duplicados (p. ej. el mismo hospital como nodo y como edificio).
            val key = "${name.lowercase()}|${category.id}|${(lat * 2000).roundToInt()}|${(lon * 2000).roundToInt()}"
            if (!seen.add(key)) continue

            result.add(Poi(name, point, category, distance))
        }
        return result.sortedBy { it.distanceMeters }.take(MAX_RESULTS)
    }
}
