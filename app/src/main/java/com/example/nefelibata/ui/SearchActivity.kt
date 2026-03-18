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
        val estadosTraducidos = mutableListOf(getString(R.string.status_todos))
        estadosTraducidos.addAll(Constants.getEstadosTraducidos(this))
        
        actvStatus.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, estadosTraducidos))
        actvStatus.setText(getString(R.string.status_todos), false)

        val generosTraducidos = Constants.getGenerosTraducidos(this)
        for (g in generosTraducidos) {
            val chip = Chip(this)
            chip.text = g
            chip.isCheckable = true
            cgGenres.addView(chip)
        }

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
        val statusSelTraducido = actvStatus.text.toString()
        val genres = (0 until cgGenres.childCount).map { cgGenres.getChildAt(it) as Chip }.filter { it.isChecked }.map { it.text.toString() }

        if (query.isNotEmpty() && query.length < Constants.MIN_SEARCH_LENGTH) {
            limpiarResultados(getString(R.string.search_msg_min_chars, Constants.MIN_SEARCH_LENGTH))
            return
        }

        if (query.isEmpty() && statusSelTraducido == getString(R.string.status_todos) && genres.isEmpty()) {
            limpiarResultados(getString(R.string.search_msg_initial))
            return
        }

        val filtrada = listaHistoriasBase.filter { h ->
            val matchQuery = query.isEmpty() || h.titulo.lowercase().contains(query) || h.autor.nombre.lowercase().contains(query)
            
            val estadosTraducidos = Constants.getEstadosTraducidos(this)
            val indiceEstado = estadosTraducidos.indexOf(statusSelTraducido)
            val estadoDB = if (indiceEstado != -1) Constants.ESTADOS_DB[indiceEstado] else null
            val matchStatus = statusSelTraducido == getString(R.string.status_todos) || h.obtenerEstadoValidado() == estadoDB
            
            val generosTraducidos = Constants.getGenerosTraducidos(this)
            val generosDBSeleccionados = genres.map { gTrad -> Constants.GENEROS_DB[generosTraducidos.indexOf(gTrad)] }
            val hGenerosDB = h.genero["es"] ?: emptyList()
            val matchGenres = genres.isEmpty() || hGenerosDB.containsAll(generosDBSeleccionados)
            
            matchQuery && matchStatus && matchGenres
        }
        mostrarResultados(filtrada.isEmpty()) { adapterHistorias.actualizarDatos(filtrada, emptyList()) }
    }

    private fun buscarAutores() {
        val query = etSearch.text.toString().trim().lowercase()
        val currentUserId = auth.currentUser?.uid

        if (query.isNotEmpty() && query.length < Constants.MIN_SEARCH_LENGTH) {
            limpiarResultados(getString(R.string.search_msg_min_chars_authors, Constants.MIN_SEARCH_LENGTH))
            return
        }

        if (query.isEmpty()) {
            limpiarResultados(getString(R.string.search_msg_initial_author))
            return
        }

        val filtrada = listaAutoresBase.filter { 
            it.nombre.lowercase().contains(query) && it.idUsuario != currentUserId 
        }
        
        mostrarResultados(filtrada.isEmpty()) { adapterAutores.actualizarDatos(filtrada) }
    }

    private fun limpiarResultados(msg: String) {
        rvResultados.isVisible = false
        tvMessage.isVisible = true
        tvMessage.text = msg
    }

    private fun mostrarResultados(vacia: Boolean, updateAction: () -> Unit) {
        if (vacia) {
            limpiarResultados(getString(R.string.search_msg_no_results))
        } else {
            tvMessage.isVisible = false
            rvResultados.isVisible = true
            updateAction()
        }
    }
}