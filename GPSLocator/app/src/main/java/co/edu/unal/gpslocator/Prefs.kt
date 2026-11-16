package co.edu.unal.gpslocator

import android.content.SharedPreferences

/** Claves y validación de las preferencias de búsqueda. */
object Prefs {
    const val RADIUS_KM = "radius_km"
    const val CATEGORIES = "categories"
    const val DEFAULT_RADIUS_KM = 1.0
    const val MIN_RADIUS_KM = 0.1
    const val MAX_RADIUS_KM = 20.0

    fun parseRadius(value: String?): Double? =
        value?.trim()?.replace(',', '.')?.toDoubleOrNull()
            ?.takeIf { it in MIN_RADIUS_KM..MAX_RADIUS_KM }

    fun radiusKm(prefs: SharedPreferences): Double =
        parseRadius(prefs.getString(RADIUS_KM, null)) ?: DEFAULT_RADIUS_KM

    fun categoryIds(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(CATEGORIES, null) ?: emptySet()

    /** Huella de la configuración actual, para saber si hay que volver a buscar. */
    fun fingerprint(prefs: SharedPreferences): String =
        "${radiusKm(prefs)}|${categoryIds(prefs).sorted().joinToString(",")}"
}
