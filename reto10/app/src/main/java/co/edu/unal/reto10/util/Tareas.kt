package co.edu.unal.reto10.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Ejecuta trabajo de red fuera del hilo principal y devuelve el resultado a la interfaz.
 * Se usa un pool "cached" para que las consultas en paralelo dentro de una tarea no se bloqueen.
 */
object Tareas {

    private val ejecutor: ExecutorService = Executors.newCachedThreadPool()
    private val principal = Handler(Looper.getMainLooper())

    fun <T> ejecutar(trabajo: () -> T, exito: (T) -> Unit, error: (Exception) -> Unit) {
        ejecutor.execute {
            try {
                val resultado = trabajo()
                principal.post { exito(resultado) }
            } catch (e: Exception) {
                val causa = if (e is ExecutionException && e.cause is Exception) e.cause as Exception else e
                principal.post { error(causa) }
            }
        }
    }

    /** Lanza una consulta en paralelo; se recoge con get() dentro de otra tarea de fondo. */
    fun <T> enParalelo(trabajo: () -> T): Future<T> = ejecutor.submit<T> { trabajo() }
}
