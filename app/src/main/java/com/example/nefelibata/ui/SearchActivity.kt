package com.example.nefelibata.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nefelibata.R
import com.example.nefelibata.adapters.AutorAdapter
import com.example.nefelibata.adapters.HistoriaAdapter
import com.example.nefelibata.models.Historia
import com.example.nefelibata.models.Usuario
import com.example.nefelibata.utils.Constants
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SearchActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var rvResultados: RecyclerView
    private lateinit var etSearch: TextInputEditText
    private lateinit var tabLayout: TabLayout
    private lateinit var llFiltrosHistorias: LinearLayout
    private lateinit var tvMessage: TextView
    
    private lateinit var adapterHistorias: HistoriaAdapter
    private lateinit var adapterAutores: AutorAdapter
    
    private var listaHistoriasBase = mutableListOf<Historia>()
    private var listaAutoresBase = mutableListOf<Usuario>()
    
    private lateinit var actvStatus: AutoCompleteTextView
    private lateinit var cgGenres: ChipGroup
    private lateinit var ivToggle: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Inicializar vistas
        rvResultados = findViewById(R.id.rv_search_results)
        etSearch = findViewById(R.id.et_search_text)
        tabLayout = findViewById(R.id.tab_layout_search)
        llFiltrosHistorias = findViewById(R.id.ll_filtros_historias)
        tvMessage = findViewById(R.id.tv_search_message)
        
        actvStatus = findViewById(R.id.actv_search_status)
        cgGenres = findViewById(R.id.cg_search_genres)
        ivToggle = findViewById(R.id.iv_toggle_generos_search)

        findViewById<ImageView>(R.id.iv_back_search).setOnClickListener { finish() }

        setupAdapters()
        setupTabs()
        setupFiltrosHistorias()
        cargarDatosBase()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { ejecutarBusquedaSegunTab() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        findViewById<Button>(R.id.btn_ejecutar_busqueda).setOnClickListener { ejecutarBusquedaSegunTab() }
    }

    private fun setupAdapters() {
        rvResultados.layoutManager = LinearLayoutManager(this)
        adapterHistorias = HistoriaAdapter(emptyList(), emptyList()) { }
        adapterAutores = AutorAdapter(emptyList()) { autor ->
            val intent = Intent(this, PerfilAutorActivity::class.java)
            intent.putExtra("idAutor", autor.idUsuario)
            startActivity(intent)
        }
        rvResultados.adapter = adapterHistorias
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    llFiltrosHistorias.visibility = View.VISIBLE
                    rvResultados.adapter = adapterHistorias
                } else {
                    llFiltrosHistorias.visibility = View.GONE
                    rvResultados.adapter = adapterAutores
                }
                ejecutarBusquedaSegunTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupFiltrosHistorias() {
        val estados = mutableListOf("Todos")
        estados.addAll(Constants.ESTADOS)
        actvStatus.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, estados))
        actvStatus.setText("Todos", false)

        for (g in Constants.GENEROS) {
            val chip = Chip(this)
            chip.text = g
            chip.isCheckable = true
            cgGenres.addView(chip)
        }

        // CORRECCIÓN: Usar el ID correcto para el header de búsqueda
        findViewById<LinearLayout>(R.id.ll_generos_header_search).setOnClickListener {
            cgGenres.isVisible = !cgGenres.isVisible
            ivToggle.setImageResource(if (cgGenres.isVisible) android.R.drawable.arrow_up_float else android.R.drawable.arrow_down_float)
        }
    }

    private fun cargarDatosBase() {
        db.collection("historias").get().addOnSuccessListener { docs ->
            listaHistoriasBase = docs.mapNotNull { it.toObject(Historia::class.java).apply { idHistoria = it.id } }.toMutableList()
        }
        db.collection("usuarios").get().addOnSuccessListener { docs ->
            listaAutoresBase = docs.mapNotNull { it.toObject(Usuario::class.java) }.toMutableList()
        }
    }

    private fun ejecutarBusquedaSegunTab() {
        if (tabLayout.selectedTabPosition == 0) buscarHistorias() else buscarAutores()
    }

    private fun buscarHistorias() {
        val query = etSearch.text.toString().trim().lowercase()
        val status = actvStatus.text.toString()
        val genres = (0 until cgGenres.childCount).map { cgGenres.getChildAt(it) as Chip }.filter { it.isChecked }.map { it.text.toString().lowercase() }

        if (query.isNotEmpty() && query.length < Constants.MIN_SEARCH_LENGTH) {
            limpiarResultados("Escribe al menos ${Constants.MIN_SEARCH_LENGTH} letras para buscar por texto")
            return
        }

        if (query.isEmpty() && status == "Todos" && genres.isEmpty()) {
            limpiarResultados("Introduce algún criterio para buscar historias")
            return
        }

        val filtrada = listaHistoriasBase.filter { h ->
            val matchQuery = query.isEmpty() || h.titulo.lowercase().contains(query) || h.autor.nombre.lowercase().contains(query)
            val matchStatus = status == "Todos" || h.obtenerEstadoValidado() == status
            val hGenres = h.genero["es"]?.map { it.lowercase() } ?: emptyList()
            val matchGenres = genres.isEmpty() || hGenres.containsAll(genres)
            matchQuery && matchStatus && matchGenres
        }
        mostrarResultados(filtrada.isEmpty(), { adapterHistorias.actualizarDatos(filtrada, emptyList()) })
    }

    private fun buscarAutores() {
        val query = etSearch.text.toString().trim().lowercase()
        val currentUserId = auth.currentUser?.uid

        if (query.isNotEmpty() && query.length < Constants.MIN_SEARCH_LENGTH) {
            limpiarResultados("Escribe al menos ${Constants.MIN_SEARCH_LENGTH} letras para buscar autores")
            return
        }

        if (query.isEmpty()) {
            limpiarResultados("Introduce el nombre de un autor")
            return
        }

        val filtrada = listaAutoresBase.filter { 
            it.nombre.lowercase().contains(query) && it.idUsuario != currentUserId 
        }
        
        mostrarResultados(filtrada.isEmpty(), { adapterAutores.actualizarDatos(filtrada) })
    }

    private fun limpiarResultados(msg: String) {
        rvResultados.isVisible = false
        tvMessage.isVisible = true
        tvMessage.text = msg
    }

    private fun mostrarResultados(vacia: Boolean, updateAction: () -> Unit) {
        if (vacia) {
            limpiarResultados("No se han encontrado resultados")
        } else {
            tvMessage.isVisible = false
            rvResultados.isVisible = true
            updateAction()
        }
    }
}