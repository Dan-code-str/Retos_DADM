package co.edu.unal.gpslocator

import android.util.Log
import org.osmdroid.util.GeoPoint

/**
 * Punto único de búsqueda de lugares: usa Nominatim y, si falla por completo,
 * intenta con la Overpass API (más completa, pero con servidores a menudo saturados).
 */
object PoiRepository {

    private const val TAG = "PoiRepository"

    fun search(
        center: GeoPoint,
        radiusMeters: Double,
        categories: List<PoiCategory>,
        isCancelled: () -> Boolean,
        onPartial: (List<Poi>) -> Unit,
    ): List<Poi> = try {
        NominatimClient.search(center, radiusMeters, categories, isCancelled, onPartial)
    } catch (e: Exception) {
        if (isCancelled()) throw e
        Log.w(TAG, "Nominatim no respondió; se intenta con Overpass", e)
        OverpassClient.search(center, radiusMeters, categories)
    }
}
