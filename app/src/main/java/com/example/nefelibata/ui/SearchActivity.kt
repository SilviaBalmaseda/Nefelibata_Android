package com.example.nefelibata.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
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
 * Pantalla de búsqueda que permite filtrar historias por título, autor, estado y género.
 * Recupera el estilo visual de la página principal (cartas grandes) y permite
 * ocultar/mostrar los filtros secundarios para ganar espacio.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    
    private lateinit var adapterHistorias: HistoriaAdapter
    private lateinit var adapterAutores: AutorAdapter
    
    private var listaHistoriasBase = mutableListOf<Historia>()
    private var listaAutoresBase = mutableListOf<Usuario>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.ivBackSearch.setOnClickListener { finish() }

        // Lógica para ocultar/mostrar opciones avanzadas (estilo Géneros)
        binding.llFiltrosToggleHeader.setOnClickListener {
            binding.llAdvancedFiltersContainer.isVisible = !binding.llAdvancedFiltersContainer.isVisible
            binding.ivToggleFiltrosArrow.setImageResource(
                if (binding.llAdvancedFiltersContainer.isVisible) android.R.drawable.arrow_up_float 
                else android.R.drawable.arrow_down_float
            )
        }

        setupAdapters()
        setupTabs()
        setupFiltrosHistorias()
        cargarDatosBase()

        binding.etSearchText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { ejecutarBusquedaSegunTab() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupAdapters() {
        binding.rvSearchResults.layoutManager = LinearLayoutManager(this)
        // Usamos el diseño de cartas grandes igual que en el Inicio
        adapterHistorias = HistoriaAdapter(emptyList(), emptyList()) { /* Favoritos no requeridos */ }
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

    private fun setupFiltrosHistorias() {
        val estadosTraducidos = mutableListOf(getString(R.string.status_todos))
        estadosTraducidos.addAll(Constants.getEstadosTraducidos(this))
        
        binding.actvSearchStatus.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, estadosTraducidos))
        binding.actvSearchStatus.setText(getString(R.string.status_todos), false)
        
        binding.actvSearchStatus.onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ ->
            ejecutarBusquedaSegunTab()
        }

        val generosTraducidos = Constants.getGenerosTraducidos(this)
        for (g in generosTraducidos) {
            val chip = Chip(this)
            chip.text = g
            chip.isCheckable = true
            chip.setOnCheckedChangeListener { _, _ -> ejecutarBusquedaSegunTab() }
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

    private fun cargarDatosBase() {
        db.collection("historias").get().addOnSuccessListener { docs ->
            listaHistoriasBase = docs.mapNotNull { it.toObject(Historia::class.java).apply { idHistoria = it.id } }.toMutableList()
            if (binding.tabLayoutSearch.selectedTabPosition == 0) buscarHistorias()
        }
        db.collection("usuarios").get().addOnSuccessListener { docs ->
            listaAutoresBase = docs.mapNotNull { it.toObject(Usuario::class.java) }.toMutableList()
            if (binding.tabLayoutSearch.selectedTabPosition == 1) buscarAutores()
        }
    }

    private fun ejecutarBusquedaSegunTab() {
        if (binding.tabLayoutSearch.selectedTabPosition == 0) buscarHistorias() else buscarAutores()
    }

    private fun buscarHistorias() {
        val query = binding.etSearchText.text.toString().trim().lowercase()
        val statusSelTraducido = binding.actvSearchStatus.text.toString()
        val genres = (0 until binding.cgSearchGenres.childCount)
            .map { binding.cgSearchGenres.getChildAt(it) as Chip }
            .filter { it.isChecked }
            .map { it.text.toString() }

        val filtrada = listaHistoriasBase.filter { h ->
            val matchQuery = query.isEmpty() || h.titulo.lowercase().contains(query) || h.autor.nombre.lowercase().contains(query)
            val estadosTraducidos = Constants.getEstadosTraducidos(this)
            val indiceEstado = estadosTraducidos.indexOf(statusSelTraducido) - 1
            val matchStatus = statusSelTraducido == getString(R.string.status_todos) || 
                            (indiceEstado >= 0 && h.obtenerEstadoValidado() == Constants.ESTADOS_DB[indiceEstado])
            val generosTraducidos = Constants.getGenerosTraducidos(this)
            val generosDBSeleccionados = genres.map { gTrad -> 
                val idx = generosTraducidos.indexOf(gTrad)
                if (idx != -1) Constants.GENEROS_DB[idx] else ""
            }.filter { it.isNotEmpty() }
            val hGenerosDB = h.genero["es"] ?: emptyList()
            val matchGenres = genres.isEmpty() || hGenerosDB.containsAll(generosDBSeleccionados)
            
            matchQuery && matchStatus && matchGenres
        }
        
        mostrarResultados(filtrada.isEmpty()) { 
            adapterHistorias.actualizarDatos(filtrada, emptyList()) 
        }
    }

    private fun buscarAutores() {
        val query = binding.etSearchText.text.toString().trim().lowercase()
        val currentUserId = auth.currentUser?.uid
        val filtrada = listaAutoresBase.filter { 
            (query.isEmpty() || it.nombre.lowercase().contains(query)) && it.idUsuario != currentUserId 
        }
        mostrarResultados(filtrada.isEmpty()) { adapterAutores.actualizarDatos(filtrada) }
    }

    private fun mostrarResultados(vacia: Boolean, updateAction: () -> Unit) {
        if (vacia) {
            binding.rvSearchResults.isVisible = false
            binding.tvSearchMessage.isVisible = true
        } else {
            binding.tvSearchMessage.isVisible = false
            binding.rvSearchResults.isVisible = true
            updateAction()
        }
    }
}
