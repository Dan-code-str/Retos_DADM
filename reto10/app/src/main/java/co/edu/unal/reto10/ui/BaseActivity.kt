package co.edu.unal.reto10.ui

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import co.edu.unal.reto10.R
import co.edu.unal.reto10.util.Tareas
import com.google.android.material.button.MaterialButton
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

open class BaseActivity : AppCompatActivity() {

    /** setContentView + espacio para barras del sistema (la app dibuja de borde a borde). */
    protected fun prepararPantalla(@LayoutRes layout: Int) {
        enableEdgeToEdge()
        setContentView(layout)
        val raiz = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insets ->
            val barras = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }
    }

    /** Corre [trabajo] en segundo plano y entrega el resultado solo si la pantalla sigue viva. */
    protected fun <T> cargar(trabajo: () -> T, exito: (T) -> Unit, error: (Exception) -> Unit) {
        Tareas.ejecutar(
            trabajo,
            { resultado -> if (!isFinishing && !isDestroyed) exito(resultado) },
            { e -> if (!isFinishing && !isDestroyed) error(e) }
        )
    }

    protected fun mensajeError(e: Exception): String = when (e) {
        is UnknownHostException -> getString(R.string.error_sin_conexion)
        is SocketTimeoutException -> getString(R.string.error_tiempo)
        is IOException -> getString(R.string.error_servicio, e.message ?: "")
        else -> getString(R.string.error_respuesta, e.message ?: e.javaClass.simpleName)
    }

    // --- Bloque de estado (layout_estado.xml) ---

    protected fun mostrarCargando(texto: String) {
        findViewById<View>(R.id.estado).visibility = View.VISIBLE
        findViewById<ProgressBar>(R.id.progresoCarga).visibility = View.VISIBLE
        findViewById<TextView>(R.id.textoEstado).text = texto
        findViewById<MaterialButton>(R.id.botonReintentar).visibility = View.GONE
    }

    protected fun mostrarError(texto: String, reintentar: () -> Unit) {
        findViewById<View>(R.id.estado).visibility = View.VISIBLE
        findViewById<ProgressBar>(R.id.progresoCarga).visibility = View.GONE
        findViewById<TextView>(R.id.textoEstado).text = texto
        findViewById<MaterialButton>(R.id.botonReintentar).apply {
            visibility = View.VISIBLE
            setOnClickListener { reintentar() }
        }
    }

    protected fun ocultarEstado() {
        findViewById<View>(R.id.estado).visibility = View.GONE
    }
}
