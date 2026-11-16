package co.edu.unal.reto10.util

import java.text.NumberFormat
import java.util.Locale

object Formato {

    private val LOCALE_CO: Locale = Locale.forLanguageTag("es-CO")
    private val PALABRAS_MENORES = setOf("de", "del", "la", "las", "los", "el", "y", "e", "en")

    fun entero(valor: Long): String = NumberFormat.getIntegerInstance(LOCALE_CO).format(valor)

    fun decimal(valor: Double, decimales: Int = 1): String {
        val formato = NumberFormat.getNumberInstance(LOCALE_CO)
        formato.minimumFractionDigits = decimales
        formato.maximumFractionDigits = decimales
        return formato.format(valor)
    }

    /** 9.412.345 → "9,4 M" · 59.359 → "59,4 mil" · 945 → "945" */
    fun compacto(valor: Long): String = when {
        valor >= 1_000_000 -> decimal(valor / 1_000_000.0) + " M"
        valor >= 10_000 -> decimal(valor / 1_000.0) + " mil"
        else -> entero(valor)
    }

    /** Con signo explícito: +4,7 · −1,2 */
    fun conSigno(valor: Double): String = (if (valor >= 0) "+" else "−") + decimal(kotlin.math.abs(valor))

    /** "BOGOTÁ, D.C." → "Bogotá, D.C." · "VILLA DE LEYVA" → "Villa de Leyva" */
    fun titulo(texto: String): String {
        if (texto.isBlank()) return texto
        return texto.trim().lowercase(LOCALE_CO).split(' ').filter { it.isNotEmpty() }
            .mapIndexed { i, palabra ->
                when {
                    palabra.replace(",", "") == "d.c." -> palabra.uppercase(LOCALE_CO)
                    i > 0 && palabra in PALABRAS_MENORES -> palabra
                    else -> palabra.replaceFirstChar { it.titlecase(LOCALE_CO) }
                }
            }
            .joinToString(" ")
    }
}
