package com.example.nefelibata.models

import java.util.Locale

data class Historia(
    var idHistoria: String = "",
    var titulo: String = "",
    var autor: Autor = Autor(),
    var imagenUrl: String = "",
    var numFavoritos: Int = 0,
    var sinopsis: String = "",
    var estado: Map<String, String> = emptyMap(),
    var genero: Map<String, List<String>> = emptyMap()
) {
    fun obtenerEstadoValidado(): String {
        val valorEs = estado["es"]?.lowercase()?.replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
        } ?: "Pendiente"
        
        val estadosPermitidos = listOf("Pendiente", "En pausa", "Terminada", "Abandonada")
        return if (estadosPermitidos.contains(valorEs)) valorEs else "Pendiente"
    }

    fun obtenerGenerosValidados(): String {
        // Extraemos la lista en español
        val listaEs = genero["es"]
        return if (listaEs.isNullOrEmpty()) {
            "Ninguno"
        } else {
            listaEs.joinToString(", ") {
                it.lowercase().replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
                }
            }
        }
    }
}