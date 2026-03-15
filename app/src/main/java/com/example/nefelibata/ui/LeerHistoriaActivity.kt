package com.example.nefelibata.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.nefelibata.R
import com.example.nefelibata.models.Capitulo
import com.example.nefelibata.models.Historia
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class LeerHistoriaActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var tvTituloHistoria: TextView
    private lateinit var btnNombreAutor: MaterialButton 
    private lateinit var tvTituloCapitulo: TextView
    private lateinit var tvContenidoCapitulo: TextView
    private lateinit var btnAnterior: MaterialButton
    private lateinit var btnSiguiente: MaterialButton
    private lateinit var selectorCapitulo: AutoCompleteTextView
    private lateinit var ivBack: ImageView

    private var listaCapitulos = mutableListOf<Capitulo>()
    private var indiceActual = 0
    private lateinit var idHistoria: String
    private var idAutorActual: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leer_historia)

        db = FirebaseFirestore.getInstance()

        try {
            // IDs vinculados al nuevo diseño dentro del folio blanco
            tvTituloHistoria = findViewById(R.id.tv_leer_titulo_historia)
            btnNombreAutor = findViewById(R.id.btn_leer_ver_autor) 
            tvTituloCapitulo = findViewById(R.id.tv_leer_titulo_capitulo)
            tvContenidoCapitulo = findViewById(R.id.tv_leer_contenido_capitulo)
            btnAnterior = findViewById(R.id.btn_leer_anterior)
            btnSiguiente = findViewById(R.id.btn_leer_siguiente)
            selectorCapitulo = findViewById(R.id.actv_leer_selector_capitulo)
            ivBack = findViewById(R.id.iv_back_leer)

            idHistoria = intent.getStringExtra("idHistoria") ?: ""
            val tituloRecibido = intent.getStringExtra("tituloHistoria") ?: "Historia"
            tvTituloHistoria.text = tituloRecibido

            ivBack.setOnClickListener { finish() }

            if (idHistoria.isNotEmpty()) {
                cargarDatosHistoria()
                cargarCapitulos()
            }

            btnNombreAutor.setOnClickListener {
                idAutorActual?.let { id ->
                    val intent = Intent(this, PerfilAutorActivity::class.java)
                    intent.putExtra("idAutor", id)
                    startActivity(intent)
                }
            }

            configurarBotones()
        } catch (e: Exception) {
            Log.e("LeerHistoria", "Error inicializando vistas: ${e.message}")
            finish()
        }
    }

    private fun cargarDatosHistoria() {
        db.collection("historias").document(idHistoria).get().addOnSuccessListener { doc ->
            try {
                val historia = doc.toObject(Historia::class.java)
                historia?.let {
                    tvTituloHistoria.text = it.titulo
                    btnNombreAutor.text = it.autor.nombre
                    idAutorActual = it.autor.id
                }
            } catch (e: Exception) {
                Log.e("LeerHistoria", "Error mapeando historia")
            }
        }
    }

    private fun cargarCapitulos() {
        db.collection("historias").document(idHistoria)
            .collection("capitulos")
            .orderBy("numCapitulo", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documentos ->
                listaCapitulos = documentos.mapNotNull { doc ->
                    val cap = doc.toObject(Capitulo::class.java)
                    cap.idCapitulo = doc.id
                    cap
                }.toMutableList()
                
                if (listaCapitulos.isNotEmpty()) {
                    configurarSelector()
                    actualizarVistaCapitulo()
                }
            }
    }

    private fun configurarSelector() {
        val titulos = listaCapitulos.map { 
            if (it.tituloCap.isNullOrEmpty()) "Cap. ${it.numCapitulo}" else it.tituloCap 
        }
        selectorCapitulo.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, titulos))
        selectorCapitulo.setOnItemClickListener { _, _, position, _ ->
            indiceActual = position
            actualizarVistaCapitulo()
        }
    }

    private fun configurarBotones() {
        btnAnterior.setOnClickListener {
            if (indiceActual > 0) {
                indiceActual--
                actualizarVistaCapitulo()
            }
        }
        btnSiguiente.setOnClickListener {
            if (indiceActual < listaCapitulos.size - 1) {
                indiceActual++
                actualizarVistaCapitulo()
            }
        }
    }

    private fun actualizarVistaCapitulo() {
        if (listaCapitulos.isEmpty()) return
        val cap = listaCapitulos[indiceActual]
        tvTituloCapitulo.text = if (cap.tituloCap.isNullOrEmpty()) "Cap. ${cap.numCapitulo}" else cap.tituloCap
        tvContenidoCapitulo.text = cap.historiaCap
        selectorCapitulo.setText(tvTituloCapitulo.text, false)
        btnAnterior.isEnabled = indiceActual > 0
        btnSiguiente.isEnabled = indiceActual < listaCapitulos.size - 1
    }
}