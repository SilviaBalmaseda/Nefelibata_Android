package com.example.nefelibata.models

import android.content.Context
import com.example.nefelibata.R
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
    // Función mejorada para obtener el recurso traducido del estado
    fun obtenerEstadoTraducido(context: Context): String {
        val valorEs = estado["es"] ?: "Pendiente"
        val resId = Constants.ESTADOS_MAP[valorEs] ?: R.string.status_pendiente
        return context.getString(resId)
    }

    fun obtenerEstadoValidado(): String {
        return estado["es"] ?: "Pendiente"
    }

    // Función mejorada para obtener géneros traducidos
    fun obtenerGenerosTraducidos(context: Context): String {
        val listaEs = genero["es"] ?: return context.getString(R.string.genre_none)
        return listaEs.joinToString(", ") { g ->
            val resId = Constants.GENEROS_MAP[g]
            if (resId != null) context.getString(resId) else g
        }
    }

    fun obtenerGenerosValidados(): String {
        return genero["es"]?.joinToString(", ") ?: "Ninguno"
    }
}