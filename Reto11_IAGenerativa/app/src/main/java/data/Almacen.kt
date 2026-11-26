package co.edu.unal.reto11.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Una imagen generada guardada en el almacenamiento interno de la app. */
data class Creacion(val archivo: File, val prompt: String, val mimeType: String, val semilla: Long? = null) {
    fun leer() = ImagenGenerada(archivo.readBytes(), mimeType, semilla = semilla)
}

/**
 * Guarda cada imagen generada en files/generadas/ junto con sus datos (archivo .json
 * con el mismo nombre: prompt y semilla), para mostrar el historial y poder compartirla o refinarla.
 */
class Almacen(private val contexto: Context) {

    private val carpeta = File(contexto.filesDir, "generadas").apply { mkdirs() }

    suspend fun guardar(imagen: ImagenGenerada, prompt: String): Creacion = withContext(Dispatchers.IO) {
        val extension = if (imagen.mimeType.contains("jpeg") || imagen.mimeType.contains("jpg")) "jpg" else "png"
        val nombre = "lienzo_${System.currentTimeMillis()}"
        val archivo = File(carpeta, "$nombre.$extension")
        archivo.writeBytes(imagen.bytes)
        val datos = JSONObject().put("prompt", prompt)
        if (imagen.semilla != null) datos.put("semilla", imagen.semilla)
        File(carpeta, "$nombre.json").writeText(datos.toString())
        recortarHistorial()
        Creacion(archivo, prompt, imagen.mimeType, imagen.semilla)
    }

    suspend fun historial(): List<Creacion> = withContext(Dispatchers.IO) { listar() }

    fun buscar(ruta: String): Creacion? = File(ruta).takeIf { it.exists() }?.let { aCreacion(it) }

    /** URI content:// para compartir con otras apps mediante FileProvider. */
    fun uriParaCompartir(creacion: Creacion): Uri =
        FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", creacion.archivo)

    /** Copia la imagen a la galería del teléfono (Pictures/LienzoIA). Requiere Android 10+. */
    suspend fun guardarEnGaleria(creacion: Creacion): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) copiarAMediaStore(creacion) else false
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun copiarAMediaStore(creacion: Creacion): Boolean {
        val resolver = contexto.contentResolver
        val valores = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, creacion.archivo.name)
            put(MediaStore.Images.Media.MIME_TYPE, creacion.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/LienzoIA")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { it.write(creacion.archivo.readBytes()) }
                ?: error("Sin flujo de salida")
            valores.clear()
            valores.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, valores, null, null)
        }.onFailure { resolver.delete(uri, null, null) }.isSuccess
    }

    private fun listar(): List<Creacion> =
        carpeta.listFiles { f -> f.extension == "png" || f.extension == "jpg" }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { aCreacion(it) }

    private fun aCreacion(archivo: File): Creacion {
        val mime = if (archivo.extension == "jpg") "image/jpeg" else "image/png"
        val json = File(archivo.parentFile, archivo.nameWithoutExtension + ".json")
        if (json.exists()) {
            val datos = runCatching { JSONObject(json.readText()) }.getOrNull()
            if (datos != null) {
                val semilla = if (datos.has("semilla")) datos.optLong("semilla") else null
                return Creacion(archivo, datos.optString("prompt"), mime, semilla)
            }
        }
        val txt = File(archivo.parentFile, archivo.nameWithoutExtension + ".txt") // formato anterior
        return Creacion(archivo, txt.takeIf { it.exists() }?.readText().orEmpty(), mime)
    }

    private fun recortarHistorial() {
        listar().drop(MAXIMO_HISTORIAL).forEach { c ->
            c.archivo.delete()
            File(carpeta, c.archivo.nameWithoutExtension + ".json").delete()
            File(carpeta, c.archivo.nameWithoutExtension + ".txt").delete()
        }
    }

    companion object {
        const val MAXIMO_HISTORIAL = 20
    }
}
