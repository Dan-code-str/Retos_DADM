package co.edu.unal.reto10.ui

import android.content.Context
import co.edu.unal.reto10.util.Formato
import org.json.JSONArray
import org.json.JSONObject

/**
 * Una zona del mapa (departamento o municipio): código DANE numérico, nombre, código del
 * departamento al que pertenece y polígonos. Cada anillo es [lon0, lat0, lon1, lat1, …].
 */
class Zona(val codigo: Int, val nombre: String, val departamento: Int, val poligonos: List<List<DoubleArray>>)

/**
 * Lee y guarda en memoria los contornos (GeoJSON en assets, paquete @john-guerra/geo-colombia,
 * licencia MIT, a partir del Marco Geoestadístico Nacional del DANE).
 * La lectura es pesada: llamar desde un hilo de fondo.
 */
object GeoCache {

    private const val ARCHIVO_DEPARTAMENTOS = "departamentos.geojson"
    private const val ARCHIVO_MUNICIPIOS = "municipios.geojson"

    @Volatile private var departamentos: List<Zona>? = null
    @Volatile private var municipios: List<Zona>? = null

    fun departamentos(context: Context): List<Zona> =
        departamentos ?: synchronized(this) {
            departamentos ?: leer(context, ARCHIVO_DEPARTAMENTOS, "DPTO_CCDGO", "DPTO_CNMBR")
                .also { departamentos = it }
        }

    fun municipiosDe(context: Context, codigoDepartamento: Int): List<Zona> {
        val todos = municipios ?: synchronized(this) {
            municipios ?: leer(context, ARCHIVO_MUNICIPIOS, "MPIO_CDPMP", "MPIO_CNMBR")
                .also { municipios = it }
        }
        return todos.filter { it.departamento == codigoDepartamento }
    }

    /** Lectura con JSONObject/JSONArray del SDK, igual que las respuestas del servicio. */
    private fun leer(context: Context, archivo: String, campoCodigo: String, campoNombre: String): List<Zona> {
        val texto = context.assets.open(archivo).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val features = JSONObject(texto).getJSONArray("features")
        val lista = ArrayList<Zona>(features.length())
        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val propiedades = feature.getJSONObject("properties")
            val geometria = feature.optJSONObject("geometry") ?: continue
            val coordenadas = geometria.getJSONArray("coordinates")
            val poligonos = when (geometria.getString("type")) {
                "Polygon" -> listOf(leerPoligono(coordenadas))
                "MultiPolygon" -> (0 until coordenadas.length()).map { leerPoligono(coordenadas.getJSONArray(it)) }
                else -> emptyList()
            }
            lista.add(
                Zona(
                    codigo = propiedades.getString(campoCodigo).toInt(),
                    nombre = Formato.titulo(propiedades.optString(campoNombre)),
                    departamento = propiedades.optString("DPTO_CCDGO").toIntOrNull() ?: 0,
                    poligonos = poligonos
                )
            )
        }
        return lista
    }

    private fun leerPoligono(anillos: JSONArray): List<DoubleArray> =
        (0 until anillos.length()).map { r ->
            val puntos = anillos.getJSONArray(r)
            val plano = DoubleArray(puntos.length() * 2)
            for (k in 0 until puntos.length()) {
                val punto = puntos.getJSONArray(k)
                plano[2 * k] = punto.getDouble(0)
                plano[2 * k + 1] = punto.getDouble(1)
            }
            plano
        }
}
