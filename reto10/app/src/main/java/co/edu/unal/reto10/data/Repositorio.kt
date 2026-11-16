package co.edu.unal.reto10.data

import co.edu.unal.reto10.util.Tareas

/**
 * Todas las consultas SoQL de la app. Los métodos son bloqueantes:
 * se llaman desde un hilo de fondo (ver Tareas / BaseActivity.cargar).
 * Los números de consulta coinciden con la tabla del documento del proyecto.
 */
object Repositorio {

    private const val ACCESOS = SocrataClient.ACCESOS
    private const val DETALLE = SocrataClient.DETALLE

    // --- Pantalla Consulta (consultas 1 a 3) ---

    fun datosIniciales(): DatosIniciales {
        val departamentos = Tareas.enParalelo { departamentos() }
        val periodos = Tareas.enParalelo { periodos() }
        return DatosIniciales(departamentos.get(), periodos.get())
    }

    /** #1 Lista de departamentos. */
    fun departamentos(): List<Opcion> = Parser.opciones(
        SocrataClient.consultar(
            ACCESOS, mapOf(
                "\$select" to "distinct codigo_dane_departamento,departamento",
                "\$order" to "departamento"
            )
        ),
        "codigo_dane_departamento", "departamento"
    )

    /** #2 Municipios de un departamento. */
    fun municipios(codigoDepartamento: String): List<Opcion> = Parser.opciones(
        SocrataClient.consultar(
            ACCESOS, mapOf(
                "\$select" to "distinct codigo_dane_municipio,municipio",
                "codigo_dane_departamento" to codigoDepartamento,
                "\$order" to "municipio",
                "\$limit" to "500"
            )
        ),
        "codigo_dane_municipio", "municipio"
    )

    /** #3 Años y trimestres disponibles. */
    fun periodos(): List<Periodo> = Parser.periodos(
        SocrataClient.consultar(
            ACCESOS, mapOf(
                "\$select" to "anno,trimestre",
                "\$group" to "anno,trimestre",
                "\$order" to "anno,trimestre"
            )
        )
    )

    // --- Pantalla Resumen (consultas 4 y 5) ---

    fun resumen(codigoDepartamento: String, codigoMunicipio: String, periodo: Periodo): ResumenMunicipio {
        // #4 Evolución del municipio (incluye el periodo escogido)
        val evolucion = Tareas.enParalelo {
            Parser.evolucion(
                SocrataClient.consultar(
                    ACCESOS, mapOf(
                        "\$select" to "anno,trimestre,accesos_fijos_internet,poblacion_dane,penetracion",
                        "codigo_dane_municipio" to codigoMunicipio,
                        "\$order" to "anno,trimestre"
                    )
                )
            )
        }
        // #5 Ranking del departamento en el periodo
        val ranking = Tareas.enParalelo {
            Parser.ranking(
                SocrataClient.consultar(
                    ACCESOS, mapOf(
                        "\$select" to "codigo_dane_municipio,municipio,penetracion",
                        "anno" to periodo.anno.toString(),
                        "trimestre" to periodo.trimestre.toString(),
                        "codigo_dane_departamento" to codigoDepartamento,
                        "\$order" to "penetracion DESC",
                        "\$limit" to "500"
                    )
                )
            )
        }
        return ResumenMunicipio(evolucion.get(), ranking.get())
    }

    // --- Pantalla Detalle (consultas 6 a 9) ---

    fun detalle(codigoMunicipio: String, periodo: Periodo): DetalleMunicipio {
        val filtro = mapOf(
            "anno" to periodo.anno.toString(),
            "trimestre" to periodo.trimestre.toString(),
            "codigo_dane_municipio" to codigoMunicipio
        )

        // #6, #7, #8: accesos agrupados por segmento, tecnología y proveedor
        fun agrupado(campo: String) = Tareas.enParalelo {
            Parser.conteos(
                SocrataClient.consultar(
                    DETALLE, filtro + mapOf(
                        "\$select" to "$campo,sum(accesos_fijos_internet) as accesos",
                        "\$group" to campo,
                        "\$order" to "accesos DESC",
                        "\$limit" to "500"
                    )
                ),
                campo
            )
        }

        val segmentos = agrupado("segmento")
        val tecnologias = agrupado("tecnologia")
        val proveedores = agrupado("proveedor")

        // #9 Velocidad de bajada promedio ponderada por accesos
        val velocidad = Tareas.enParalelo {
            Parser.velocidad(
                SocrataClient.consultar(
                    DETALLE, filtro + mapOf(
                        "\$select" to "sum(accesos_fijos_internet*velocidad_bajada)/sum(accesos_fijos_internet) as vel_prom"
                    )
                )
            )
        }

        return DetalleMunicipio(
            estratos = Categorias.estratos(segmentos.get()),
            tecnologias = Categorias.tecnologias(tecnologias.get()),
            operadores = Categorias.operadores(proveedores.get()),
            velocidadPromedio = velocidad.get()
        )
    }

    // --- Pantalla Mapa (consultas 10 a 12) ---
    // Una sola consulta trae todos los periodos, así el slider y la animación no esperan a la red.
    // Nacional: agrupado por departamento. Por departamento: filtrado y agrupado por municipio.

    fun mapaSerie(modo: ModoMapa, codigoDepartamento: String? = null): SerieMapa {
        val campo = if (codigoDepartamento == null) "codigo_dane_departamento" else "codigo_dane_municipio"
        val categoria = when (modo) {
            ModoMapa.PENETRACION -> null
            ModoMapa.FIBRA -> "tecnologia"
            ModoMapa.OPERADOR -> "proveedor"
        }
        val dataset = if (modo == ModoMapa.PENETRACION) ACCESOS else DETALLE
        val sumas = if (modo == ModoMapa.PENETRACION) {
            "sum(accesos_fijos_internet) as acc,sum(poblacion_dane) as pob"
        } else {
            "sum(accesos_fijos_internet) as acc"
        }
        val grupo = listOfNotNull("anno", "trimestre", campo, categoria).joinToString(",")

        val parametros = buildMap {
            put("\$select", "$grupo,$sumas")
            put("\$group", grupo)
            put("\$limit", "50000")
            if (codigoDepartamento != null) put("codigo_dane_departamento", codigoDepartamento)
        }
        val filas = Parser.filasZona(SocrataClient.consultar(dataset, parametros), campo, categoria)

        val porPeriodo = filas.groupBy { Periodo(it.anno, it.trimestre) }
        val periodos = porPeriodo.keys.sortedWith(compareBy({ it.anno }, { it.trimestre }))
        val valores = porPeriodo.mapValues { (_, delPeriodo) ->
            delPeriodo.groupBy { it.codigo }
                .mapNotNull { (codigo, grupoZona) -> calcular(modo, codigo, grupoZona)?.let { codigo to it } }
                .toMap()
        }
        val totales = porPeriodo.mapNotNull { (periodo, delPeriodo) ->
            calcular(modo, 0, delPeriodo)?.let { periodo to it }
        }.toMap()
        return SerieMapa(periodos, valores, totales)
    }

    /** Convierte las filas de una zona (o de todo el conjunto, código 0) en su valor para el mapa. */
    private fun calcular(modo: ModoMapa, codigo: Int, filas: List<FilaZona>): ValorZona? {
        val total = filas.sumOf { it.accesos }
        return when (modo) {
            ModoMapa.PENETRACION -> {
                val poblacion = filas.sumOf { it.poblacion }
                if (poblacion <= 0) null else ValorZona(codigo, total * 100.0 / poblacion, null, total, poblacion)
            }

            ModoMapa.FIBRA -> {
                if (total <= 0) return null
                val fibra = filas.filter { Categorias.esFibra(it.categoria) }.sumOf { it.accesos }
                ValorZona(codigo, fibra * 100.0 / total, null, total)
            }

            ModoMapa.OPERADOR -> {
                if (total <= 0) return null
                val principal = filas.groupBy { Categorias.nombreOperador(it.categoria) }
                    .mapValues { (_, g) -> g.sumOf { it.accesos } }
                    .maxByOrNull { it.value } ?: return null
                ValorZona(codigo, principal.value * 100.0 / total, principal.key, total)
            }
        }
    }
}
