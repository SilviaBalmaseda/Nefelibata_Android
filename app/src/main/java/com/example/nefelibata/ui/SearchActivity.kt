package com.example.nefelibata.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nefelibata.R
import com.example.nefelibata.adapters.HistoriaAdapter
import com.example.nefelibata.models.Historia
import com.example.nefelibata.utils.Constants
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class SearchActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: HistoriaAdapter
    private lateinit var rvResultados: RecyclerView
    private lateinit var etSearch: TextInputEditText
    private lateinit var actvStatus: AutoCompleteTextView
    private lateinit var cgGenres: ChipGroup
    private lateinit var ivBack: ImageView
    private lateinit var llGenerosHeader: LinearLayout
    private lateinit var ivToggleGeneros: ImageView
    private lateinit var btnBuscar: MaterialButton
    private lateinit var tvSearchMessage: TextView

    private var listaHistoriasCompleta = mutableListOf<Historia>()
    private var listaFavoritosUsuario = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_search)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Inicializar vistas
        rvResultados = findViewById(R.id.rv_search_results)
        etSearch = findViewById(R.id.et_search_text)
        actvStatus = findViewById(R.id.actv_search_status)
        cgGenres = findViewById(R.id.cg_search_genres)
        ivBack = findViewById(R.id.iv_back_search)
        llGenerosHeader = findViewById(R.id.ll_generos_header_search)
        ivToggleGeneros = findViewById(R.id.iv_toggle_generos_search)
        btnBuscar = findViewById(R.id.btn_ejecutar_busqueda)
        tvSearchMessage = findViewById(R.id.tv_search_message)

        ivBack.setOnClickListener { finish() }

        llGenerosHeader.setOnClickListener {
            if (cgGenres.visibility == View.VISIBLE) {
                cgGenres.visibility = View.GONE
                ivToggleGeneros.setImageResource(android.R.drawable.arrow_down_float)
            } else {
                cgGenres.visibility = View.VISIBLE
                ivToggleGeneros.setImageResource(android.R.drawable.arrow_up_float)
            }
        }

        setupRecyclerView()
        setupFiltros()
        obtenerFavoritos()

        btnBuscar.setOnClickListener { realizarBusqueda() }
    }

    private fun setupRecyclerView() {
        rvResultados.layoutManager = LinearLayoutManager(this)
        adapter = HistoriaAdapter(emptyList(), emptyList()) { historia ->
            gestionarFavorito(historia)
        }
        rvResultados.adapter = adapter
    }

    private fun setupFiltros() {
        val estados = mutableListOf("Todos")
        estados.addAll(Constants.ESTADOS)
        
        actvStatus.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, estados))
        actvStatus.setText("Todos", false)

        val generos = Constants.GENEROS
        for (g in generos) {
            val chip = Chip(this)
            chip.text = g
            chip.isCheckable = true
            cgGenres.addView(chip)
        }
    }

    private fun obtenerFavoritos() {
        val user = auth.currentUser
        if (user != null) {
            db.collection("usuarios").document(user.uid).get().addOnSuccessListener { doc ->
                listaFavoritosUsuario = (doc.get("idFavoritas") as? List<*>)?.map { it.toString() }?.toMutableList() ?: mutableListOf()
                cargarHistoriasBase()
            }
        } else {
            cargarHistoriasBase()
        }
    }

    private fun cargarHistoriasBase() {
        db.collection("historias").get().addOnSuccessListener { documentos ->
            listaHistoriasCompleta = documentos.map { doc ->
                val h = doc.toObject(Historia::class.java)
                h.idHistoria = doc.id
                h
            }.toMutableList()
            adapter.actualizarDatos(emptyList(), listaFavoritosUsuario)
        }
    }

    private fun gestionarFavorito(historia: Historia) {
        val user = auth.currentUser ?: return
        val userRef = db.collection("usuarios").document(user.uid)
        val historiaRef = db.collection("historias").document(historia.idHistoria)

        if (listaFavoritosUsuario.contains(historia.idHistoria)) {
            userRef.update("idFavoritas", FieldValue.arrayRemove(historia.idHistoria))
            historiaRef.update("numFavoritos", FieldValue.increment(-1))
            listaFavoritosUsuario.remove(historia.idHistoria)
            historia.numFavoritos -= 1
        } else {
            userRef.update("idFavoritas", FieldValue.arrayUnion(historia.idHistoria))
            historiaRef.update("numFavoritos", FieldValue.increment(1))
            listaFavoritosUsuario.add(historia.idHistoria)
            historia.numFavoritos += 1
        }
        adapter.notifyDataSetChanged()
    }

    private fun realizarBusqueda() {
        val texto = etSearch.text.toString().trim().lowercase()
        val estadoSel = actvStatus.text.toString()
        val generosMarcados = (0 until cgGenres.childCount)
            .map { cgGenres.getChildAt(it) as Chip }
            .filter { it.isChecked }
            .map { it.text.toString().lowercase() }

        if (texto.isNotEmpty() && texto.length < 3) {
            adapter.actualizarDatos(emptyList(), listaFavoritosUsuario)
            rvResultados.visibility = View.GONE
            tvSearchMessage.visibility = View.VISIBLE
            tvSearchMessage.text = "Escribe al menos 3 letras para buscar por texto"
            return
        }

        if (texto.isEmpty() && estadoSel == "Todos" && generosMarcados.isEmpty()) {
            adapter.actualizarDatos(emptyList(), listaFavoritosUsuario)
            rvResultados.visibility = View.GONE
            tvSearchMessage.visibility = View.VISIBLE
            tvSearchMessage.text = "Introduce algún criterio para empezar a buscar"
            return
        }

        val filtrada = listaHistoriasCompleta.filter { h ->
            val cumpleTexto = texto.isEmpty() || h.titulo.lowercase().contains(texto) || h.autor.nombre.lowercase().contains(texto)
            val cumpleEstado = estadoSel == "Todos" || h.obtenerEstadoValidado() == estadoSel
            val hGeneros = h.genero["es"]?.map { it.lowercase() } ?: emptyList()
            val cumpleGeneros = generosMarcados.isEmpty() || hGeneros.containsAll(generosMarcados)
            cumpleTexto && cumpleEstado && cumpleGeneros
        }

        if (filtrada.isEmpty()) {
            adapter.actualizarDatos(emptyList(), listaFavoritosUsuario)
            rvResultados.visibility = View.GONE
            tvSearchMessage.visibility = View.VISIBLE
            tvSearchMessage.text = "No se han encontrado resultados para tu búsqueda"
        } else {
            tvSearchMessage.visibility = View.GONE
            rvResultados.visibility = View.VISIBLE
            adapter.actualizarDatos(filtrada, listaFavoritosUsuario)
        }
    }
}