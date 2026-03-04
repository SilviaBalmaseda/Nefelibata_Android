package com.example.nefelibata.models

import com.google.firebase.Timestamp

data class Capitulo(
    var idCapitulo: String = "",
    var numCapitulo: Long = 0L, // Long para compatibilidad con Firestore
    var tituloCap: String = "",
    var historiaCap: String = "",
    var fechaCreacionC: Timestamp? = null
)
