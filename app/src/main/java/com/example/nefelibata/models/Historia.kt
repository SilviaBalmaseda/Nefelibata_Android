package com.example.nefelibata.models

data class Historia(
    var idHistoria: String = "",
    var titulo: String = "",
    var autor: Autor = Autor(), // Aquí usamos la clase Autor
    var imagenUrl: String = "",
    var numFavoritos: Int = 0,
    var sinopsis: String = ""
)
