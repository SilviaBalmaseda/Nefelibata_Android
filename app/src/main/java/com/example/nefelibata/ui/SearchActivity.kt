package com.example.nefelibata.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nefelibata.R
import com.example.nefelibata.adapters.AutorAdapter
import com.example.nefelibata.adapters.HistoriaAdapter
import com.example.nefelibata.databinding.ActivitySearchBinding
import com.example.nefelibata.models.Historia
import com.example.nefelibata.models.Usuario
import com.example.nefelibata.utils.Constants
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Pantalla de búsqueda que permite filtrar historias por título, autor, estado y género,
 * así como buscar otros autores.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    
    private lateinit var adapterHistorias: HistoriaAdapter
    private lateinit var adapterAutores: AutorAdapter
    
    // Listas completas descargadas de Firestore para realizar el filtrado en local
    private var listaHistoriasBase = mutableListOf<Historia>()
    private var listaAutoresBase = mutableListOf<Usuario>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.ivBackSearch.setOnClickListener { finish() }

        setupAdapters()
        setupTabs()
        setupFiltrosHistorias()
        cargarDatosBase()

        // Búsqueda en tiempo real mientras el usuario escribe
        binding.etSearchText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { ejecutarBusquedaSegunTab() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        binding.btnEjecutarBusqueda.setOnClickListener { ejecutarBusquedaSegunTab() }
    }

    private fun setupAdapters() {
        binding.rvSearchResults.layoutManager = LinearLayoutManager(this)
        adapterHistorias = HistoriaAdapter(emptyList(), emptyList()) { /* Gestión de favoritos no requerida aquí inicialmente */ }
        adapterAutores = AutorAdapter(emptyList()) { autor ->
            val intent = Intent(this, PerfilAutorActivity::class.java).apply {
                putExtra("idAutor", autor.idUsuario)
            }
            startActivity(intent)
        }
        binding.rvSearchResults.adapter = adapterHistorias
    }

    private fun setupTabs() {
        binding.tabLayoutSearch.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // Cambia la visibilidad de los filtros y el adaptador según la pestaña seleccionada
                if (tab?.position == 0) {
                    binding.llFiltrosHistorias.visibility = View.VISIBLE
                    binding.rvSearchResults.adapter = adapterHistorias
                } else {
                    binding.llFiltrosHistorias.visibility = View.GONE
                    binding.rvSearchResults.adapter = adapterAutores
                }
                ejecutarBusquedaSegunTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    /**
     * Configura el desplegable de estados y los chips de géneros con textos traducidos.
     */
    private fun setupFiltrosHistorias() {
        val estadosTraducidos = mutableListOf(getString(R.string.status_todos))
        estadosTraducidos.addAll(Constants.getEstadosTraducidos(this))
        
        binding.actvSearchStatus.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, estadosTraducidos))
        binding.actvSearchStatus.setText(getString(R.string.status_todos), false)

        val generosTraducidos = Constants.getGenerosTraducidos(this)
        for (g in generosTraducidos) {
            val chip = Chip(this)
            chip.text = g
            chip.isCheckable = true
            binding.cgSearchGenres.addView(chip)
        }

        binding.llGenerosHeaderSearch.setOnClickListener {
            binding.cgSearchGenres.isVisible = !binding.cgSearchGenres.isVisible
            binding.ivToggleGenerosSearch.setImageResource(
                if (binding.cgSearchGenres.isVisible) android.R.drawable.arrow_up_float 
                else android.R.drawable.arrow_down_float
            )
        }
    }

    /**
     * Descarga todos los datos necesarios de Firestore para permitir una búsqueda local fluida.
     */
    private fun cargarDatosBase() {
        db.collection("historias").get().addOnSuccessListener { docs ->
            listaHistoriasBase = docs.mapNotNull { it.toObject(Historia::class.java).apply { idHistoria = it.id } }.toMutableList()
        }
        db.collection("usuarios").get().addOnSuccessListener { docs ->
            listaAutoresBase = docs.mapNotNull { it.toObject(Usuario::class.java) }.toMutableList()
        }
    }

    private fun ejecutarBusquedaSegunTab() {
        if (binding.tabLayoutSearch.selectedTabPosition == 0) buscarHistorias() else buscarAutores()
    }

    /**
     * Filtra la lista local de historias según los criterios seleccionados.
     */
    private fun buscarHistorias() {
        val query = binding.etSearchText.text.toString().trim().lowercase()
        val statusSelTraducido = binding.actvSearchStatus.text.toString()
        val genres = (0 until binding.cgSearchGenres.childCount)
            .map { binding.cgSearchGenres.getChildAt(it) as Chip }
            .filter { it.isChecked }
            .map { it.text.toString() }

        // Validación de longitud mínima de búsqueda
        if (query.isNotEmpty() && query.length < Constants.MIN_SEARCH_LENGTH) {
            limpiarResultados(getString(R.string.search_msg_min_chars, Constants.MIN_SEARCH_LENGTH))
            return
        }

        // Estado inicial de la búsqueda
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

    /**
     * Filtra la lista local de autores.
     */
    private fun buscarAutores() {
        val query = binding.etSearchText.text.toString().trim().lowercase()
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
        binding.rvSearchResults.isVisible = false
        binding.tvSearchMessage.isVisible = true
        binding.tvSearchMessage.text = msg
    }

    private fun mostrarResultados(vacia: Boolean, updateAction: () -> Unit) {
        if (vacia) {
            limpiarResultados(getString(R.string.search_msg_no_results))
        } else {
            binding.tvSearchMessage.isVisible = false
            binding.rvSearchResults.isVisible = true
            updateAction()
        }
    }
}
