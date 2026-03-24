package com.example.nefelibata.models

import com.google.firebase.Timestamp

/**
 * Representa un capítulo individual de una historia.
 */
data class Capitulo(
    var idCapitulo: String = "",
    var numCapitulo: Long = 0,
    var tituloCap: String = "",
    var historiaCap: String = "", // Contenido de texto del capítulo
    var fechaCreacionC: Timestamp? = null,
    var fechaModificacionC: Timestamp? = null
)
