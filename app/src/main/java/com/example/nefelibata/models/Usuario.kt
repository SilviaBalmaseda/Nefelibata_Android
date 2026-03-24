package com.example.nefelibata.models

/**
 * Modelo que representa a un usuario de la plataforma.
 */
data class Usuario(
    var idUsuario: String = "",
    var nombre: String = "",
    var email: String = "",
    var idFavoritas: List<String> = emptyList(), // IDs de historias marcadas como favoritas
    var idSiguiendo: List<String> = emptyList(), // IDs de autores a los que sigue
    var preferencias: Map<String, String> = emptyMap(), // Configuración de tema, lenguaje, etc.
    var fotoUser: String = "",
    var numSeguidor: Int = 0
)
