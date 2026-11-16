package co.edu.unal.reto10.data

/** Una opción de selector: código DANE + nombre tal como viene del servicio + nombre para mostrar. */
data class Opcion(val codigo: String, val nombreOriginal: String, val nombre: String) {
    override fun toString() = nombre
}

data class Periodo(val anno: Int, val trimestre: Int) {
    override fun toString() = "$anno-T$trimestre"
}

data class PeriodoAccesos(
    val periodo: Periodo,
    val accesos: Long,
    val poblacion: Long,
    /** Accesos por cada 100 habitantes. */
    val penetracion: Double
)

data class PosicionMunicipio(val codigo: String, val nombre: String, val penetracion: Double)

data class ItemConteo(val etiqueta: String, val valor: Long)

data class DatosIniciales(val departamentos: List<Opcion>, val periodos: List<Periodo>)

data class ResumenMunicipio(
    val evolucion: List<PeriodoAccesos>,
    val ranking: List<PosicionMunicipio>
)

data class DetalleMunicipio(
    val estratos: List<ItemConteo>,
    val tecnologias: List<ItemConteo>,
    val operadores: List<ItemConteo>,
    /** Velocidad de bajada promedio ponderada por accesos, en Mbps; null si no hay datos. */
    val velocidadPromedio: Double?
) {
    val total: Long get() = tecnologias.sumOf { it.valor }
}

// --- Mapa ---

enum class ModoMapa { PENETRACION, FIBRA, OPERADOR }

/** Una fila agrupada por periodo y zona (departamento o municipio): categoría (tecnología/proveedor) y sumas. */
data class FilaZona(
    val anno: Int,
    val trimestre: Int,
    val codigo: Int,
    val categoria: String,
    val accesos: Long,
    val poblacion: Long
)

/**
 * Valor de una zona (departamento o municipio) para colorear el mapa.
 * PENETRACION: accesos por cada 100 hab. · FIBRA: % de accesos de fibra · OPERADOR: % del operador principal.
 */
data class ValorZona(
    val codigo: Int,
    val valor: Double,
    val categoria: String?,
    val accesos: Long,
    val poblacion: Long = 0
)

/** Todos los periodos de un modo de mapa: valor por zona y total del conjunto (país o departamento). */
data class SerieMapa(
    val periodos: List<Periodo>,
    val valores: Map<Periodo, Map<Int, ValorZona>>,
    val totales: Map<Periodo, ValorZona>
)
