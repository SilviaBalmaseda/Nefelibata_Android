package com.example.nefelibata

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nefelibata.R
import com.example.nefelibata.adapters.HistoriaAdapter
import com.example.nefelibata.models.Historia
import com.example.nefelibata.ui.SettingsActivity
import com.example.nefelibata.ui.auth.LoginActivity
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
    private lateinit var llUserLoggedIn: LinearLayout
    private lateinit var tvUsername: TextView
    private lateinit var ivSettings: ImageView

    private lateinit var llGenerosHeader: LinearLayout
    private lateinit var ivToggleGeneros: ImageView
    private lateinit var cgGeneros: ChipGroup

    private var paginaActual = 1
    private var totalPaginas = 1
    private val HISTORIAS_POR_PAGINA = 5L

    override fun onCreate(savedInstanceState: Bundle?) {
        // APLICAR TEMA ANTES DE SUPER.ONCREATE
        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val modeSaved = sharedPrefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(modeSaved)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = Firebase.auth
        db = FirebaseFirestore.getInstance()

        // Sincronizar tema con Firestore al iniciar
        sincronizarTemaDesdeNube()

        rvHistorias = findViewById(R.id.rv_historias)
        llNumerosPaginas = findViewById(R.id.ll_numeros_paginas)
        btnAnterior = findViewById(R.id.btn_anterior)
        btnSiguiente = findViewById(R.id.btn_siguiente)
        
        llUserLoggedIn = findViewById(R.id.ll_user_logged_in)
        tvUsername = findViewById(R.id.tv_username)
        ivSettings = findViewById(R.id.iv_settings)
        
        llGenerosHeader = findViewById(R.id.ll_generos_header)
        ivToggleGeneros = findViewById(R.id.iv_toggle_generos)
        cgGeneros = findViewById(R.id.cg_generos)

        rvHistorias.layoutManager = LinearLayoutManager(this)
        adapter = HistoriaAdapter(emptyList())
        rvHistorias.adapter = adapter

        val listaGeneros = listOf(
            "Acción", "Aventura", "Comedia", "Drama", "Deportes",
            "Fantasía", "Magia", "Musical", "Psicológico",
            "Romance", "Superhéroes", "Terror", "Tragedia"
        )
        for (genero in listaGeneros) {
            val chip = Chip(this)
            chip.text = genero
            chip.isCheckable = true
            cgGeneros.addView(chip)
        }
        
        llGenerosHeader.setOnClickListener {
            if (cgGeneros.visibility == View.VISIBLE) {
                cgGeneros.visibility = View.GONE
                ivToggleGeneros.setImageResource(android.R.drawable.arrow_down_float)
            } else {
                cgGeneros.visibility = View.VISIBLE
                ivToggleGeneros.setImageResource(android.R.drawable.arrow_up_float)
            }
        }

        checkUserSession()
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

        ivSettings.setOnClickListener { view ->
            showUserMenu(view)
        }
    }

    private fun sincronizarTemaDesdeNube() {
        val user = auth.currentUser
        if (user != null) {
            db.collection("usuarios").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val preferencias = document.get("preferencias") as? Map<*, *>
                        val temaRemoto = preferencias?.get("tema") as? String
                        
                        temaRemoto?.let {
                            val modeRemoto = when (it) {
                                "claro" -> AppCompatDelegate.MODE_NIGHT_NO
                                "oscuro" -> AppCompatDelegate.MODE_NIGHT_YES
                                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                            }
                            
                            val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                            if (sharedPrefs.getInt("theme_mode", -1) != modeRemoto) {
                                sharedPrefs.edit().putInt("theme_mode", modeRemoto).apply()
                                AppCompatDelegate.setDefaultNightMode(modeRemoto)
                            }
                        }
                    }
                }
        }
    }

    private fun showUserMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.user_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_ajustes -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.menu_buscar -> {
                    Toast.makeText(this, "Función de búsqueda próximamente", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_mis_historias -> {
                    Toast.makeText(this, "Navegando a Mis Historias", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun checkUserSession() {
        val user = auth.currentUser
        if (user != null) {
            llUserLoggedIn.visibility = View.VISIBLE

            db.collection("usuarios").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        tvUsername.text = document.getString("nombre")
                    } else {
                        tvUsername.text = user.displayName ?: "Usuario"
                    }
                }
        } else {
            llUserLoggedIn.visibility = View.GONE
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

        for (i in 1..totalPaginas) {
            val btnNum = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            btnNum.text = i.toString()
            btnNum.minWidth = 0
            btnNum.minimumWidth = 0
            btnNum.setPadding(35, 0, 35, 0)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(4, 0, 4, 0)
            btnNum.layoutParams = params

            if (i == paginaActual) {
                btnNum.setBackgroundColor(Color.parseColor("#0D47A1"))
                btnNum.setTextColor(Color.WHITE)
                btnNum.strokeWidth = 0
            } else {
                btnNum.setTextColor(Color.parseColor("#0D47A1"))
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