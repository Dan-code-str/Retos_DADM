package co.edu.unal.reto10.data

import co.edu.unal.reto10.util.Formato
import org.json.JSONArray

/**
 * Convierte las respuestas JSON del servicio en objetos, usando solo
 * org.json.JSONArray / JSONObject del SDK de Android.
 * Socrata entrega los números como texto ("22414"); getLong/getDouble los convierten.
 */
object Parser {

    fun opciones(json: String, campoCodigo: String, campoNombre: String): List<Opcion> {
        val arreglo = JSONArray(json)
        val lista = ArrayList<Opcion>(arreglo.length())
        for (i in 0 until arreglo.length()) {
            val o = arreglo.getJSONObject(i)
            val nombre = o.optString(campoNombre)
            lista.add(Opcion(o.getString(campoCodigo), nombre, Formato.titulo(nombre)))
        }
        return lista
    }

    fun periodos(json: String): List<Periodo> {
        val arreglo = JSONArray(json)
        val lista = ArrayList<Periodo>(arreglo.length())
        for (i in 0 until arreglo.length()) {
            val o = arreglo.getJSONObject(i)
            lista.add(Periodo(o.getInt("anno"), o.getInt("trimestre")))
        }
        return lista
    }

    fun evolucion(json: String): List<PeriodoAccesos> {
        val arreglo = JSONArray(json)
        val lista = ArrayList<PeriodoAccesos>(arreglo.length())
        for (i in 0 until arreglo.length()) {
            val o = arreglo.getJSONObject(i)
            lista.add(
                PeriodoAccesos(
                    periodo = Periodo(o.getInt("anno"), o.getInt("trimestre")),
                    accesos = o.optLong("accesos_fijos_internet"),
                    poblacion = o.optLong("poblacion_dane"),
                    penetracion = o.optDouble("penetracion", 0.0)
                )
            )
        }
        return lista
    }

    fun ranking(json: String): List<PosicionMunicipio> {
        val arreglo = JSONArray(json)
        val lista = ArrayList<PosicionMunicipio>(arreglo.length())
        for (i in 0 until arreglo.length()) {
            val o = arreglo.getJSONObject(i)
            lista.add(
                PosicionMunicipio(
                    codigo = o.getString("codigo_dane_municipio"),
                    nombre = Formato.titulo(o.optString("municipio")),
                    penetracion = o.optDouble("penetracion", 0.0)
                )
            )
        }
        return lista
    }

    /** Respuesta de una consulta agrupada: [{"<campo>": "...", "accesos": "123"}, ...] */
    fun conteos(json: String, campo: String): List<ItemConteo> {
        val arreglo = JSONArray(json)
        val lista = ArrayList<ItemConteo>(arreglo.length())
        for (i in 0 until arreglo.length()) {
            val o = arreglo.getJSONObject(i)
            lista.add(ItemConteo(o.optString(campo, "Sin dato"), o.optLong("accesos")))
        }
        return lista
    }

    fun velocidad(json: String): Double? {
        val arreglo = JSONArray(json)
        if (arreglo.length() == 0) return null
        val valor = arreglo.getJSONObject(0).optDouble("vel_prom", Double.NaN)
        return if (valor.isNaN()) null else valor
    }

    /** Filas agrupadas por departamento o municipio (consultas 10 a 12 y sus versiones municipales). */
    fun filasZona(json: String, campoCodigo: String, campoCategoria: String?): List<FilaZona> {
        val arreglo = JSONArray(json)
        val lista = ArrayList<FilaZona>(arreglo.length())
        for (i in 0 until arreglo.length()) {
            val o = arreglo.getJSONObject(i)
            lista.add(
                FilaZona(
                    anno = o.optInt("anno"),
                    trimestre = o.optInt("trimestre"),
                    codigo = o.getString(campoCodigo).toInt(),
                    categoria = if (campoCategoria == null) "" else o.optString(campoCategoria),
                    accesos = o.optLong("acc"),
                    poblacion = o.optLong("pob")
                )
            )
        }
        return lista
    }
}
