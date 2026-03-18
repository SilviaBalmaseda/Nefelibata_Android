package com.example.nefelibata.utils

import android.content.Context
import com.example.nefelibata.R

object Constants {
    const val MIN_NAME_LENGTH = 3
    const val MIN_STORY_TITLE_LENGTH = 3
    const val MIN_SEARCH_LENGTH = 3
    
    const val LANG_ES = "esp"
    const val LANG_EN = "ing"
    const val DEFAULT_LANG = LANG_ES

    // Estos se quedan para identificar en la BBDD (No cambian)
    val ESTADOS_DB = listOf("Pendiente", "En pausa", "Terminada", "Abandonada")
    val GENEROS_DB = listOf(
        "Acción", "Aventura", "Comedia", "Drama", "Deportes", 
        "Fantasía", "Magia", "Musical", "Psicológico", "Romance", 
        "Superhéroes", "Terror", "Tragedia"
    )

    val ESTADOS_MAP = mapOf(
        "Pendiente" to R.string.status_pendiente,
        "En pausa" to R.string.status_en_pausa,
        "Terminada" to R.string.status_terminada,
        "Abandonada" to R.string.status_abandonada
    )

    val GENEROS_MAP = mapOf(
        "Acción" to R.string.genre_accion,
        "Aventura" to R.string.genre_aventura,
        "Comedia" to R.string.genre_comedia,
        "Drama" to R.string.genre_drama,
        "Deportes" to R.string.genre_deportes,
        "Fantasía" to R.string.genre_fantasia,
        "Magia" to R.string.genre_magia,
        "Musical" to R.string.genre_musical,
        "Psicológico" to R.string.genre_psicologico,
        "Romance" to R.string.genre_romance,
        "Superhéroes" to R.string.genre_superheroes,
        "Terror" to R.string.genre_terror,
        "Tragedia" to R.string.genre_tragedia
    )

    // FUNCIONES PARA OBTENER LISTAS TRADUCIDAS
    fun getEstadosTraducidos(context: Context): List<String> {
        return ESTADOS_DB.map { context.getString(ESTADOS_MAP[it]!!) }
    }

    fun getGenerosTraducidos(context: Context): List<String> {
        return GENEROS_DB.map { context.getString(GENEROS_MAP[it]!!) }
    }
}