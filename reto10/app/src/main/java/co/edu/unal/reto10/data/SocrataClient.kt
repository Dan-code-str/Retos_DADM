package co.edu.unal.reto10.data

import android.net.Uri
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente mínimo para la API SODA (Socrata) de datos.gov.co.
 * Arma la URL con los parámetros SoQL y devuelve el cuerpo JSON como texto.
 */
object SocrataClient {

    private const val BASE = "https://www.datos.gov.co/resource/"

    /** Accesos fijos a Internet por departamento, municipio y población (MinTIC). */
    const val ACCESOS = "tvzi-ba2g"

    /** Accesos fijos a Internet por proveedor, tecnología, segmento y velocidad (MinTIC). */
    const val DETALLE = "hwrp-3su3"

    /** Token de aplicación opcional de datos.gov.co; sin él el servicio limita las peticiones. */
    var appToken: String? = null

    @Throws(IOException::class)
    fun consultar(dataset: String, parametros: Map<String, String>): String {
        val builder = Uri.parse("$BASE$dataset.json").buildUpon()
        for ((clave, valor) in parametros) {
            builder.appendQueryParameter(clave, valor)
        }
        val conexion = URL(builder.build().toString()).openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 15_000
            conexion.readTimeout = 25_000
            conexion.setRequestProperty("Accept", "application/json")
            appToken?.let { conexion.setRequestProperty("X-App-Token", it) }

            val codigo = conexion.responseCode
            val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
            val cuerpo = flujo?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (codigo !in 200..299) {
                throw IOException("El servicio respondió con código $codigo")
            }
            return cuerpo
        } finally {
            conexion.disconnect()
        }
    }
}
