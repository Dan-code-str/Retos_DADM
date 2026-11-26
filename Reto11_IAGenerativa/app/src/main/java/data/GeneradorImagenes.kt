package co.edu.unal.reto11.data

/** Cualquier servicio de IA capaz de convertir un prompt en imagen. */
interface GeneradorImagenes {
    /** Nombre visible del servicio y modelo. */
    val nombre: String

    /** true si respeta la relación de aspecto pedida. */
    val admiteFormato: Boolean

    /** true si puede editar una imagen existente; si no, se refina regenerando con la misma semilla. */
    val editaImagenes: Boolean

    suspend fun generar(
        prompt: String,
        relacionAspecto: String,
        imagenBase: ImagenGenerada? = null,
        semilla: Long? = null,
    ): ImagenGenerada
}
