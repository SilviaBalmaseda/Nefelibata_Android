package com.example.nefelibata.models

data class Usuario(
    var idUsuario: String = "",
    var nombre: String = "",
    var email: String = "",
    var idFavoritas: List<String> = emptyList(),
    var idSiguiendo: List<String> = emptyList(),
    var preferencias: Map<String, String> = emptyMap(),
    var fotoUser: String = "",
    var numSeguidor: Int = 0
)