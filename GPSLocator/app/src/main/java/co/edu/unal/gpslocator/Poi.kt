package co.edu.unal.gpslocator

import androidx.annotation.StringRes
import org.osmdroid.util.GeoPoint
import kotlin.math.roundToInt

/**
 * Tipo de punto de interés.
 * - [tags]: etiquetas de OpenStreetMap que lo identifican (clave -> valores).
 * - [searchTerms]: tipos que se consultan en Nominatim (una petición por término).
 * El [id] debe coincidir con los valores de @array/poi_category_values.
 */
data class PoiCategory(
    val id: String,
    @param:StringRes val label: Int,
    val color: Int,
    val tags: Map<String, Set<String>>,
    val searchTerms: List<String>,
)

/** Punto de interés encontrado alrededor de la ubicación consultada. */
data class Poi(
    val name: String,
    val point: GeoPoint,
    val category: PoiCategory,
    val distanceMeters: Double,
) {
    /** Evita duplicados (p. ej. el mismo lugar como nodo y como edificio). */
    val dedupeKey: String
        get() = "${name.lowercase()}|${category.id}|" +
            "${(point.latitude * 2000).roundToInt()}|${(point.longitude * 2000).roundToInt()}"
}

object PoiCategories {
    val all = listOf(
        PoiCategory(
            "hospital", R.string.cat_hospital, 0xFFD32F2F.toInt(),
            mapOf("amenity" to setOf("hospital", "clinic")),
            listOf("hospital", "clinic"),
        ),
        PoiCategory(
            "pharmacy", R.string.cat_pharmacy, 0xFF00897B.toInt(),
            mapOf("amenity" to setOf("pharmacy")),
            listOf("pharmacy"),
        ),
        PoiCategory(
            "tourism", R.string.cat_tourism, 0xFF7B1FA2.toInt(),
            mapOf(
                "tourism" to setOf("attraction", "museum", "viewpoint", "gallery", "zoo", "theme_park"),
                "historic" to setOf("monument", "memorial", "castle"),
            ),
            listOf("attraction", "museum", "monument"),
        ),
        PoiCategory(
            "park", R.string.cat_park, 0xFF558B2F.toInt(),
            mapOf("leisure" to setOf("park")),
            listOf("park"),
        ),
        PoiCategory(
            "restaurant", R.string.cat_restaurant, 0xFFEF6C00.toInt(),
            mapOf("amenity" to setOf("restaurant", "cafe", "fast_food")),
            listOf("restaurant", "cafe"),
        ),
        PoiCategory(
            "education", R.string.cat_education, 0xFF1976D2.toInt(),
            mapOf("amenity" to setOf("school", "university", "college")),
            listOf("school", "university"),
        ),
        PoiCategory(
            "police", R.string.cat_police, 0xFF283593.toInt(),
            mapOf("amenity" to setOf("police")),
            listOf("police"),
        ),
        PoiCategory(
            "hotel", R.string.cat_hotel, 0xFFC2185B.toInt(),
            mapOf("tourism" to setOf("hotel", "hostel", "guest_house")),
            listOf("hotel", "hostel"),
        ),
        PoiCategory(
            "bank", R.string.cat_bank, 0xFF5D4037.toInt(),
            mapOf("amenity" to setOf("bank")),
            listOf("bank"),
        ),
    )

    fun byIds(ids: Set<String>): List<PoiCategory> = all.filter { it.id in ids }

    /** Categoría que corresponde a una etiqueta OSM (clave=valor), si hay alguna. */
    fun match(categories: List<PoiCategory>, key: String, value: String): PoiCategory? =
        categories.firstOrNull { it.tags[key]?.contains(value) == true }
}
