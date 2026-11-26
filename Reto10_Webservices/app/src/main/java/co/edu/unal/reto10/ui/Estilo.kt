package co.edu.unal.reto10.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import co.edu.unal.reto10.R

/** Colores y utilidades visuales compartidas por las vistas dibujadas a mano. */
object Estilo {
    val AGUAMARINA = Color.rgb(46, 230, 197)
    val LIMA = Color.rgb(184, 243, 74)
    val PISTA = Color.rgb(26, 43, 46)
    val TEXTO = Color.rgb(226, 241, 238)
    val TEXTO_SUAVE = Color.rgb(159, 184, 179)
    val FONDO = Color.rgb(7, 16, 18)

    @Volatile private var fuente: Typeface? = null

    /** Space Grotesk (res/font); si no se puede cargar, la fuente del sistema. */
    fun fuente(context: Context): Typeface =
        fuente ?: (runCatching { ResourcesCompat.getFont(context, R.font.space_grotesk_familia) }.getOrNull()
            ?: Typeface.DEFAULT).also { fuente = it }

    fun dp(context: Context, valor: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor, context.resources.displayMetrics)
}

object Animaciones {
    /** Cuenta desde 0 hasta [destino] en el TextView, con el formato dado. */
    fun contar(vista: TextView, destino: Double, duracion: Long = 900, formato: (Double) -> String) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duracion
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { vista.text = formato(destino * (it.animatedValue as Float)) }
            start()
        }
    }
}
