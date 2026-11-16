package co.edu.unal.reto10.data

import co.edu.unal.reto10.util.Formato

/**
 * Traduce y agrupa los valores crudos del servicio (segmentos, tecnologías,
 * razones sociales) en etiquetas cortas para mostrar.
 */
object Categorias {

    private val ORDEN_SEGMENTOS = listOf(
        "Estrato 1", "Estrato 2", "Estrato 3", "Estrato 4", "Estrato 5", "Estrato 6",
        "Sin estratificar", "Corporativo", "Otros"
    )

    fun estratos(items: List<ItemConteo>): List<ItemConteo> {
        val suma = LinkedHashMap<String, Long>()
        for (item in items) {
            val s = item.etiqueta.uppercase()
            val clave = when {
                s.startsWith("RESIDENCIAL - ESTRATO") -> "Estrato " + s.substringAfterLast(' ').trim()
                s.startsWith("CORPORATIVO") -> "Corporativo"
                s.startsWith("SIN ESTRATIFICAR") -> "Sin estratificar"
                else -> "Otros"
            }
            suma[clave] = (suma[clave] ?: 0L) + item.valor
        }
        val ordenados = ORDEN_SEGMENTOS.mapNotNull { clave ->
            suma[clave]?.takeIf { it > 0 }?.let { ItemConteo(clave, it) }
        }
        val resto = (suma.keys - ORDEN_SEGMENTOS.toSet()).map { ItemConteo(it, suma.getValue(it)) }
        return ordenados + resto
    }

    fun tecnologias(items: List<ItemConteo>, maximo: Int = 5): List<ItemConteo> =
        agruparYRecortar(items.map { ItemConteo(nombreTecnologia(it.etiqueta), it.valor) }, maximo, "Otras")

    /** FTTH, FTTB, FTTP y otras variantes de fibra óptica. */
    fun esFibra(tecnologiaCruda: String): Boolean {
        val nombre = nombreTecnologia(tecnologiaCruda)
        return nombre.startsWith("Fibra") || nombre == "Otra fibra"
    }

    fun operadores(items: List<ItemConteo>, maximo: Int = 5): List<ItemConteo> =
        agruparYRecortar(items.map { ItemConteo(nombreOperador(it.etiqueta), it.valor) }, maximo, null)

    fun nombreTecnologia(cruda: String): String {
        val s = cruda.uppercase()
        return when {
            s.contains("FTTH") -> "Fibra (FTTH)"
            s.contains("FTTB") -> "Fibra (FTTB)"
            s.contains("FIBER TO THE PREMISES") -> "Fibra (FTTP)"
            s.contains("OTRAS TECNOLOGÍAS DE FIBRA") || s.contains("FTTX") -> "Otra fibra"
            s.contains("HFC") -> "Cable (HFC)"
            s.contains("SATELITAL LEO") -> "Satelital LEO"
            s.startsWith("SATELITAL") -> "Satelital"
            s.contains("XDSL") -> "xDSL"
            s == "WIFI" -> "WiFi"
            s.contains("FWA") -> "5G fijo (FWA)"
            else -> Formato.titulo(cruda)
        }
    }

    /** Razón social → nombre comercial para los operadores grandes. */
    fun nombreOperador(razonSocial: String): String {
        val s = razonSocial.uppercase()
        return when {
            s.contains("COMCEL") -> "Claro"
            s.contains("COLOMBIA TELECOMUNICACIONES") -> "Movistar"
            s.contains("UNE EPM") || s.contains("COLOMBIA MOVIL") -> "Tigo"
            s.contains("EMPRESA DE TELECOMUNICACIONES DE BOGOTA") -> "ETB"
            s.contains("PARTNERS TELECOM") -> "WOM"
            s.contains("AVANTEL") -> "Avantel"
            else -> Formato.titulo(quitarSufijoSocietario(razonSocial))
        }
    }

    private fun quitarSufijoSocietario(nombre: String): String {
        // Quita anotaciones entre comillas o paréntesis, p. ej. "EN LIQUIDACION".
        var limpio = nombre.replace(Regex("\"[^\"]*\"|\\([^)]*\\)"), " ").replace(Regex("\\s+"), " ").trim()
        val sufijos = listOf("E.S.P BIC", "E.S.P.", "E.S.P", "ESP", "BIC", "S.A.S.", "S.A.S", "SAS", "S.A.", "S.A", "SA", "LTDA.", "LTDA")
        var cambio = true
        while (cambio) {
            cambio = false
            for (sufijo in sufijos) {
                if (limpio.uppercase().endsWith(" $sufijo")) {
                    limpio = limpio.dropLast(sufijo.length).trim().trimEnd(',', '.')
                    cambio = true
                }
            }
        }
        return limpio
    }

    private fun agruparYRecortar(items: List<ItemConteo>, maximo: Int, etiquetaResto: String?): List<ItemConteo> {
        val suma = LinkedHashMap<String, Long>()
        for (item in items) suma[item.etiqueta] = (suma[item.etiqueta] ?: 0L) + item.valor
        val ordenados = suma.entries.sortedByDescending { it.value }.map { ItemConteo(it.key, it.value) }
        if (ordenados.size <= maximo) return ordenados
        val principales = ordenados.take(maximo)
        if (etiquetaResto == null) return principales
        val resto = ordenados.drop(maximo).sumOf { it.valor }
        return if (resto > 0) principales + ItemConteo(etiquetaResto, resto) else principales
    }
}
