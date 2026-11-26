package co.edu.unal.reto11.data

/** Opción seleccionable en un chip: lo que ve el usuario y lo que se le dice al modelo. */
data class Opcion(val etiqueta: String, val descripcion: String)

/**
 * Construye el prompt final a partir de la interacción del usuario:
 * su idea libre + el estilo + el ambiente (opcional). El formato (relación de aspecto)
 * no va en el texto: se envía en generationConfig.imageConfig.
 */
object ConstructorPrompt {

    val estilos = listOf(
        Opcion("Fotografía", "fotografía realista con cámara profesional, lente de 35 mm, profundidad de campo"),
        Opcion("Ilustración", "ilustración digital detallada, trazos limpios y colores vibrantes"),
        Opcion("Acuarela", "pintura en acuarela sobre papel texturizado, bordes suaves y pigmentos que se mezclan"),
        Opcion("Óleo", "pintura al óleo con pinceladas visibles, estilo de galería clásica"),
        Opcion("Render 3D", "render 3D estilizado, materiales suaves e iluminación de estudio"),
        Opcion("Pixel art", "pixel art de 32 bits con paleta limitada, estética de videojuego retro"),
        Opcion("Boceto", "boceto a lápiz de grafito sobre papel, sombreado a mano"),
        Opcion("Papel recortado", "collage de papel recortado en capas, sombras suaves entre capas"),
    )

    val ambientes = listOf(
        Opcion("Atardecer", "luz dorada de atardecer, tonos cálidos"),
        Opcion("Nocturno", "escena nocturna con luces artificiales y sombras profundas"),
        Opcion("Neón", "paleta neón de magentas y cianes, atmósfera futurista"),
        Opcion("Pastel", "colores pastel suaves y ambiente tranquilo"),
        Opcion("Brumoso", "niebla ligera y atmósfera misteriosa"),
        Opcion("Blanco y negro", "blanco y negro de alto contraste"),
    )

    /** Relaciones de aspecto admitidas por los modelos de imagen de Gemini. */
    val formatos = listOf(
        Opcion("Cuadrado 1:1", "1:1"),
        Opcion("Vertical 3:4", "3:4"),
        Opcion("Horizontal 4:3", "4:3"),
        Opcion("Historia 9:16", "9:16"),
        Opcion("Panorámico 16:9", "16:9"),
    )

    fun construir(idea: String, estilo: Opcion, ambiente: Opcion?): String = buildString {
        append("Genera una imagen de: ").append(idea.trim().trimEnd('.')).append(". ")
        append("Estilo: ").append(estilo.descripcion).append(". ")
        if (ambiente != null) append("Ambiente: ").append(ambiente.descripcion).append(". ")
        append("Composición cuidada y alta calidad. No incluyas texto, letras ni marcas de agua.")
    }

    fun construirCambio(instruccion: String): String =
        "Edita esta imagen: ${instruccion.trim().trimEnd('.')}. " +
            "Conserva el estilo, la composición y todo lo demás que no se pidió cambiar."

    /** Para generadores que no editan imágenes: prompt original + cambio (se reutiliza la semilla). */
    fun combinarCambio(promptOriginal: String, instruccion: String): String =
        "${promptOriginal.trim()} Cambio solicitado: ${instruccion.trim().trimEnd('.')}."
}
