package com.example.nefelibata.ui

import android.content.Context
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
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class LeerHistoriaActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var tvTituloHistoria: TextView
    private lateinit var tvTituloCapitulo: TextView
    private lateinit var tvContenidoCapitulo: TextView
    private lateinit var btnAnterior: MaterialButton
    private lateinit var btnSiguiente: MaterialButton
    private lateinit var selectorCapitulo: AutoCompleteTextView
    private lateinit var ivBack: ImageView

    private var listaCapitulos = mutableListOf<Capitulo>()
    private var indiceActual = 0
    private lateinit var idHistoria: String

    override fun onCreate(savedInstanceState: Bundle?) {
        // APLICAR TEMA ANTES DE CUALQUIER OTRA COSA
        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val modeSaved = sharedPrefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(modeSaved)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leer_historia)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_leer)) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        db = FirebaseFirestore.getInstance()

        try {
            tvTituloHistoria = findViewById(R.id.tv_leer_titulo_historia)
            tvTituloCapitulo = findViewById(R.id.tv_leer_titulo_capitulo)
            tvContenidoCapitulo = findViewById(R.id.tv_leer_contenido_capitulo)
            btnAnterior = findViewById(R.id.btn_leer_anterior)
            btnSiguiente = findViewById(R.id.btn_leer_siguiente)
            selectorCapitulo = findViewById(R.id.actv_leer_selector_capitulo)
            ivBack = findViewById(R.id.iv_back_leer)

            idHistoria = intent.getStringExtra("idHistoria") ?: ""
            val tituloHistoria = intent.getStringExtra("tituloHistoria") ?: "Historia"
            tvTituloHistoria.text = tituloHistoria

            ivBack.setOnClickListener { finish() }

            if (idHistoria.isNotEmpty()) {
                cargarCapitulos()
            } else {
                Toast.makeText(this, "Error: Datos de historia no encontrados", Toast.LENGTH_SHORT).show()
                finish()
            }

            configurarBotones()
        } catch (e: Exception) {
            Log.e("LeerHistoria", "Error en onCreate: ${e.message}")
            finish()
        }
    }

    private fun cargarCapitulos() {
        db.collection("historias").document(idHistoria)
            .collection("capitulos")
            .orderBy("numCapitulo", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documentos ->
                listaCapitulos = documentos.mapNotNull { doc ->
                    try {
                        val capitulo = doc.toObject(Capitulo::class.java)
                        capitulo.idCapitulo = doc.id
                        capitulo
                    } catch (e: Exception) {
                        null
                    }
                }.toMutableList()
                
                if (listaCapitulos.isNotEmpty()) {
                    configurarSelector()
                    actualizarVistaCapitulo()
                } else {
                    tvTituloCapitulo.text = "Sin capítulos"
                    tvContenidoCapitulo.text = "Esta historia aún no tiene contenido."
                    btnAnterior.isEnabled = false
                    btnSiguiente.isEnabled = false
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al conectar con la base de datos", Toast.LENGTH_SHORT).show()
            }
    }

    private fun configurarSelector() {
        val titulos = listaCapitulos.map { it.tituloCap }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, titulos)
        selectorCapitulo.setAdapter(adapter)

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

        val capitulo = listaCapitulos[indiceActual]
        tvTituloCapitulo.text = capitulo.tituloCap
        tvContenidoCapitulo.text = capitulo.historiaCap
        
        selectorCapitulo.setText(capitulo.tituloCap, false)

        btnAnterior.isEnabled = indiceActual > 0
        btnSiguiente.isEnabled = indiceActual < listaCapitulos.size - 1
    }
}