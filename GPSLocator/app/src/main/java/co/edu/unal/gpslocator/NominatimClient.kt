package co.edu.unal.gpslocator

import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.osmdroid.util.GeoPoint
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.cos

/**
 * Busca puntos de interés con Nominatim (buscador oficial de OpenStreetMap).
 * Hace una petición por tipo de lugar dentro del recuadro que contiene el círculo y
 * respeta la política de uso: máximo 1 petición por segundo y User-Agent propio.
 * Las llamadas son bloqueantes: ejecútelas fuera del hilo principal.
 */
object NominatimClient {

    private const val ENDPOINT = "https://nominatim.openstreetmap.org/search"
    private const val USER_AGENT = "GPSLocator/1.0 (Android; curso UNAL)"
    private const val PER_TERM_LIMIT = 40 // máximo que permite Nominatim
    private const val MIN_INTERVAL_MS = 1100L
    private const val MAX_RESULTS = 150
    private const val TAG = "NominatimClient"

    /** Clases de OSM que no son puntos de interés (calles, barrios, etc.). */
    private val ignoredClasses = setOf("highway", "place", "boundary", "landuse", "natural", "waterway", "railway")

    private var lastRequestAt = 0L

    fun search(
        center: GeoPoint,
        radiusMeters: Double,
        categories: List<PoiCategory>,
        isCancelled: () -> Boolean,
        onPartial: (List<Poi>) -> Unit,
    ): List<Poi> {
        val viewbox = viewbox(center, radiusMeters)
        val found = LinkedHashMap<String, Poi>()
        var requests = 0
        var failures = 0
        var lastError: Exception? = null

        for (category in categories) {
            for (term in category.searchTerms) {
                if (isCancelled()) return sorted(found)
                throttle()
                requests++
                try {
                    val json = get(buildUrl(term, viewbox))
                    parseInto(json, center, radiusMeters, category, categories, found)
                    onPartial(sorted(found))
                } catch (e: IOException) {
                    Log.w(TAG, "Falló la búsqueda de '$term'", e)
                    failures++
                    lastError = e
                }
            }
        }
        // Si no funcionó ninguna petición, avisa para que se intente con otra fuente.
        if (requests > 0 && failures == requests && lastError != null) throw lastError
        return sorted(found)
    }

    @Synchronized
    private fun throttle() {
        val wait = lastRequestAt + MIN_INTERVAL_MS - SystemClock.elapsedRealtime()
        if (wait > 0) Thread.sleep(wait)
        lastRequestAt = SystemClock.elapsedRealtime()
    }

    /** Recuadro "izquierda,arriba,derecha,abajo" que contiene el círculo de búsqueda. */
    private fun viewbox(center: GeoPoint, radiusMeters: Double): String {
        val dLat = radiusMeters / 111_320.0
        val dLon = radiusMeters / (111_320.0 * cos(Math.toRadians(center.latitude)).coerceAtLeast(0.01))
        return String.format(
            Locale.US, "%.6f,%.6f,%.6f,%.6f",
            center.longitude - dLon, center.latitude + dLat,
            center.longitude + dLon, center.latitude - dLat,
        )
    }

    private fun buildUrl(term: String, viewbox: String) =
        "$ENDPOINT?format=jsonv2&amenity=${URLEncoder.encode(term, "UTF-8")}" +
            "&viewbox=$viewbox&bounded=1&limit=$PER_TERM_LIMIT&accept-language=es"

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("Nominatim respondió HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseInto(
        json: String,
        center: GeoPoint,
        radiusMeters: Double,
        queried: PoiCategory,
        categories: List<PoiCategory>,
        out: MutableMap<String, Poi>,
    ) {
        val items = try {
            JSONArray(json)
        } catch (e: org.json.JSONException) {
            throw IOException("Respuesta inválida de Nominatim", e)
        }
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val name = item.optString("name").trim()
            if (name.isEmpty()) continue

            val osmClass = item.optString("category")
            if (osmClass in ignoredClasses) continue
            val lat = item.optString("lat").toDoubleOrNull() ?: continue
            val lon = item.optString("lon").toDoubleOrNull() ?: continue

            val point = GeoPoint(lat, lon)
            val distance = center.distanceToAsDouble(point)
            if (distance > radiusMeters) continue // el recuadro es más grande que el círculo

            val category = PoiCategories.match(categories, osmClass, item.optString("type")) ?: queried
            val poi = Poi(name, point, category, distance)
            out.putIfAbsent(poi.dedupeKey, poi)
        }
    }

    private fun sorted(found: Map<String, Poi>): List<Poi> =
        found.values.sortedBy { it.distanceMeters }.take(MAX_RESULTS)
}
