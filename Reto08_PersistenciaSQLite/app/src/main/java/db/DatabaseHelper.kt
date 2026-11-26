package db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
// Asegúrate de importar tu modelo (puede variar un poco según tu package exacto)
import modelo.Empresa

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    // Definimos las constantes para no escribir los nombres "a mano" y evitar errores
    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "DirectorioEmpresas.db"
        const val TABLE_EMPRESAS = "empresas"

        const val COLUMN_ID = "id"
        const val COLUMN_NOMBRE = "nombre"
        const val COLUMN_URL = "url"
        const val COLUMN_TELEFONO = "telefono"
        const val COLUMN_EMAIL = "email"
        const val COLUMN_PRODUCTOS = "productos"
        const val COLUMN_CLASIFICACION = "clasificacion"
    }

    // Este método se ejecuta la primera vez que se crea la base de datos
    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE " + TABLE_EMPRESAS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_NOMBRE + " TEXT,"
                + COLUMN_URL + " TEXT,"
                + COLUMN_TELEFONO + " TEXT,"
                + COLUMN_EMAIL + " TEXT,"
                + COLUMN_PRODUCTOS + " TEXT,"
                + COLUMN_CLASIFICACION + " TEXT" + ")")

        db.execSQL(createTable)
    }

    // Este método se ejecuta si cambias la versión de la base de datos (DATABASE_VERSION)
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EMPRESAS")
        onCreate(db)
    }

    // ==========================================
    // OPERACIONES CRUD (Create, Read, Update, Delete)
    // ==========================================

    // 1. CREATE (Insertar una nueva empresa)
    fun insertarEmpresa(empresa: Empresa): Long {
        val db = this.writableDatabase
        val values = ContentValues()

        values.put(COLUMN_NOMBRE, empresa.nombre)
        values.put(COLUMN_URL, empresa.url)
        values.put(COLUMN_TELEFONO, empresa.telefono)
        values.put(COLUMN_EMAIL, empresa.email)
        values.put(COLUMN_PRODUCTOS, empresa.productos)
        values.put(COLUMN_CLASIFICACION, empresa.clasificacion)

        // insert() retorna el ID de la fila recién creada, o -1 si hubo un error
        val success = db.insert(TABLE_EMPRESAS, null, values)
        db.close()
        return success
    }

    // 2. READ (Obtener TODAS las empresas)
    fun obtenerTodasLasEmpresas(): ArrayList<Empresa> {
        val listaEmpresas = ArrayList<Empresa>()
        val selectQuery = "SELECT * FROM $TABLE_EMPRESAS"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                val empresa = Empresa(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    nombre = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOMBRE)),
                    url = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URL)),
                    telefono = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TELEFONO)),
                    email = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL)),
                    productos = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PRODUCTOS)),
                    clasificacion = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CLASIFICACION))
                )
                listaEmpresas.add(empresa)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return listaEmpresas
    }

    // 3. READ CON FILTRO (Buscar por nombre o clasificación)
    fun filtrarEmpresas(criterio: String): ArrayList<Empresa> {
        val listaEmpresas = ArrayList<Empresa>()
        val db = this.readableDatabase

        // Usamos LIKE para buscar palabras que contengan el criterio
        val query = "SELECT * FROM $TABLE_EMPRESAS WHERE $COLUMN_NOMBRE LIKE ? OR $COLUMN_CLASIFICACION LIKE ?"
        val argumentos = arrayOf("%$criterio%", "%$criterio%")
        val cursor = db.rawQuery(query, argumentos)

        if (cursor.moveToFirst()) {
            do {
                val empresa = Empresa(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    nombre = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOMBRE)),
                    url = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URL)),
                    telefono = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TELEFONO)),
                    email = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL)),
                    productos = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PRODUCTOS)),
                    clasificacion = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CLASIFICACION))
                )
                listaEmpresas.add(empresa)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return listaEmpresas
    }

    // 4. UPDATE (Actualizar una empresa existente)
    fun actualizarEmpresa(empresa: Empresa): Int {
        val db = this.writableDatabase
        val values = ContentValues()

        values.put(COLUMN_NOMBRE, empresa.nombre)
        values.put(COLUMN_URL, empresa.url)
        values.put(COLUMN_TELEFONO, empresa.telefono)
        values.put(COLUMN_EMAIL, empresa.email)
        values.put(COLUMN_PRODUCTOS, empresa.productos)
        values.put(COLUMN_CLASIFICACION, empresa.clasificacion)

        // update() retorna el número de filas afectadas
        val success = db.update(TABLE_EMPRESAS, values, "$COLUMN_ID = ?", arrayOf(empresa.id.toString()))
        db.close()
        return success
    }

    // 5. DELETE (Eliminar una empresa)
    fun eliminarEmpresa(id: Int): Int {
        val db = this.writableDatabase
        // delete() retorna el número de filas eliminadas
        val success = db.delete(TABLE_EMPRESAS, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
        return success
    }
}