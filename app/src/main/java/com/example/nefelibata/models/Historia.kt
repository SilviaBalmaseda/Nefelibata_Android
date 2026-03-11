package com.example.nefelibata.models

import com.example.nefelibata.utils.Constants
import com.google.firebase.Timestamp
import java.util.Locale

data class Historia(
    var idHistoria: String = "",
    var titulo: String = "",
    var autor: Autor = Autor(),
    var imagenUrl: String = "",
    var numFavoritos: Int = 0,
    var sinopsis: String = "",
    var estado: Map<String, String> = emptyMap(),
    var genero: Map<String, List<String>> = emptyMap(),
    var contCapitulos: Long = 0,
    var ultimoNumCap: Long = 0,
    var fechaCreacionH: Timestamp? = null,
    var fechaModificacionH: Timestamp? = null
) {
    fun obtenerEstadoValidado(): String {
        val valorEs = estado["es"]?.lowercase()?.replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
        } ?: Constants.ESTADOS.first()
        return if (Constants.ESTADOS.contains(valorEs)) valorEs else Constants.ESTADOS.first()
    }

    fun obtenerGenerosValidados(): String {
        val listaEs = genero["es"]
        return if (listaEs.isNullOrEmpty()) "Ninguno"
        else listaEs.joinToString(", ") {
            it.lowercase().replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
        }
    }
}