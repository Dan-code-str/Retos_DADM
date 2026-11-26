package modelo

data class Empresa(
    var id: Int = 0, // El ID será autoincrementable en la base de datos
    var nombre: String,
    var url: String,
    var telefono: String,
    var email: String,
    var productos: String,
    // Como la clasificación puede ser "consultoría, desarrollo y/o fábrica",
    // lo guardaremos como un texto (String) concatenado.
    var clasificacion: String
)