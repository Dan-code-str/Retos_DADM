package co.edu.unal.reto10.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import co.edu.unal.reto10.R

data class Barra(
    val etiqueta: String,
    val valor: Double,
    val texto: String,
    val destacada: Boolean = false
)

/** Pinta una lista de barras horizontales con resplandor, proporcionales al valor más grande. */
object Barras {

    fun llenar(contenedor: LinearLayout, barras: List<Barra>, animar: Boolean = true) {
        contenedor.removeAllViews()
        val maximo = barras.maxOfOrNull { it.valor } ?: 0.0
        val inflador = LayoutInflater.from(contenedor.context)
        for ((i, barra) in barras.withIndex()) {
            val fila = inflador.inflate(R.layout.item_barra, contenedor, false)
            val etiqueta = fila.findViewById<TextView>(R.id.etiqueta)
            val valor = fila.findViewById<TextView>(R.id.valor)
            val vistaBarra = fila.findViewById<BarraGlowView>(R.id.barra)

            etiqueta.text = barra.etiqueta
            valor.text = barra.texto
            if (barra.destacada) {
                etiqueta.setTypeface(etiqueta.typeface, Typeface.BOLD)
                valor.setTypeface(valor.typeface, Typeface.BOLD)
            }
            val fraccion = if (maximo > 0.0) (barra.valor / maximo).toFloat() else 0f
            vistaBarra.mostrar(if (barra.valor > 0.0) fraccion.coerceAtLeast(0.01f) else 0f, if (animar) i * 70L else -1)
            contenedor.addView(fila)
        }
    }
}
