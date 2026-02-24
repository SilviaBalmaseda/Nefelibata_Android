package com.example.nefelibata

import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nefelibata.models.Historia
import com.example.nefelibata.adapters.HistoriaAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var rvHistorias: RecyclerView
    private lateinit var adapter: HistoriaAdapter
    private lateinit var llNumerosPaginas: LinearLayout
    private lateinit var btnAnterior: MaterialButton
    private lateinit var btnSiguiente: MaterialButton

    private var paginaActual = 1
    private var totalPaginas = 1
    private val HISTORIAS_POR_PAGINA = 5L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = Firebase.auth
        db = FirebaseFirestore.getInstance()

        rvHistorias = findViewById(R.id.rv_historias)
        llNumerosPaginas = findViewById(R.id.ll_numeros_paginas)
        btnAnterior = findViewById(R.id.btn_anterior)
        btnSiguiente = findViewById(R.id.btn_siguiente)
        val chipGroup = findViewById<ChipGroup>(R.id.cg_generos)

        rvHistorias.layoutManager = LinearLayoutManager(this)
        adapter = HistoriaAdapter(emptyList())
        rvHistorias.adapter = adapter

        // Configurar géneros
        val listaGeneros = listOf(
            "Acción", "Aventura", "Comedia", "Drama", "Deportes", 
            "Fantasía", "Magia", "Musical", "Psicológico", 
            "Romance", "Superhéroes", "Terror", "Tragedia"
        )
        for (genero in listaGeneros) {
            val chip = Chip(this)
            chip.text = genero
            chip.isCheckable = true
            chipGroup.addView(chip)
        }

        calcularTotalPaginasYCargar()

        btnAnterior.setOnClickListener {
            if (paginaActual > 1) {
                paginaActual--
                cargarHistoriasDeFirebase()
            }
        }

        btnSiguiente.setOnClickListener {
            if (paginaActual < totalPaginas) {
                paginaActual++
                cargarHistoriasDeFirebase()
            }
        }
    }

    private fun calcularTotalPaginasYCargar() {
        db.collection("historias").count().get(com.google.firebase.firestore.AggregateSource.SERVER)
            .addOnSuccessListener { snapshot ->
                val totalHistorias = snapshot.count
                totalPaginas = Math.ceil(totalHistorias.toDouble() / HISTORIAS_POR_PAGINA).toInt()
                if (totalPaginas == 0) totalPaginas = 1
                cargarHistoriasDeFirebase()
            }
    }

    private fun cargarHistoriasDeFirebase() {
        db.collection("historias")
            .orderBy("titulo")
            .limit(HISTORIAS_POR_PAGINA)
            .get()
            .addOnSuccessListener { documentos ->
                val listaTemporal = documentos.toObjects(Historia::class.java)
                adapter.actualizarDatos(listaTemporal)
                actualizarVistaPaginacion()
            }
    }

    private fun actualizarVistaPaginacion() {
        btnAnterior.isEnabled = paginaActual > 1
        btnSiguiente.isEnabled = paginaActual < totalPaginas

        llNumerosPaginas.removeAllViews()

        // Creamos botones para cada número de página
        for (i in 1..totalPaginas) {
            val btnNum = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            btnNum.text = i.toString()
            btnNum.minWidth = 0
            btnNum.minimumWidth = 0
            btnNum.setPadding(20, 0, 20, 0)
            
            // Ajuste de margen para que parezcan pegados
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(-2, 0, -2, 0) // Margen negativo para solapar bordes
            btnNum.layoutParams = params

            if (i == paginaActual) {
                // Estilo para la página seleccionada (azul con texto blanco)
                btnNum.setBackgroundColor(Color.parseColor("#2196F3"))
                btnNum.setTextColor(Color.WHITE)
                btnNum.strokeWidth = 0
            } else {
                // Estilo para las otras páginas (borde gris/azul y texto azul)
                btnNum.setTextColor(Color.parseColor("#2196F3"))
                btnNum.setStrokeColorResource(android.R.color.darker_gray)
                btnNum.setOnClickListener {
                    paginaActual = i
                    cargarHistoriasDeFirebase()
                }
            }

            llNumerosPaginas.addView(btnNum)
        }
    }
}