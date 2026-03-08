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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nefelibata.R
import com.example.nefelibata.adapters.HistoriaAdapter
import com.example.nefelibata.models.Historia
import com.example.nefelibata.ui.SearchActivity
import com.example.nefelibata.ui.SettingsActivity
import com.example.nefelibata.ui.auth.LoginActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

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

    private var paginaActual = 1
    private var totalPaginas = 1
    private val HISTORIAS_POR_PAGINA = 5L
    
    private var listaFavoritosUsuario = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
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

        rvHistorias = findViewById(R.id.rv_historias)
        llNumerosPaginas = findViewById(R.id.ll_numeros_paginas)
        btnAnterior = findViewById(R.id.btn_anterior)
        btnSiguiente = findViewById(R.id.btn_siguiente)
        llUserLoggedIn = findViewById(R.id.ll_user_logged_in)
        tvUsername = findViewById(R.id.tv_username)
        ivSettings = findViewById(R.id.iv_settings)

        rvHistorias.layoutManager = LinearLayoutManager(this)
        
        adapter = HistoriaAdapter(emptyList(), emptyList()) { historia ->
            gestionarFavorito(historia)
        }
        rvHistorias.adapter = adapter

        checkUserSession()
        obtenerFavoritosYCargarHistorias()
        sincronizarTemaDesdeNube()

        btnAnterior.setOnClickListener { if (paginaActual > 1) { paginaActual--; cargarHistoriasDeFirebase() } }
        btnSiguiente.setOnClickListener { if (paginaActual < totalPaginas) { paginaActual++; cargarHistoriasDeFirebase() } }
        ivSettings.setOnClickListener { view -> showUserMenu(view) }
    }

    private fun obtenerFavoritosYCargarHistorias() {
        val user = auth.currentUser
        if (user != null) {
            db.collection("usuarios").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    listaFavoritosUsuario = (doc.get("idFavoritas") as? List<*>)?.map { it.toString() }?.toMutableList() ?: mutableListOf()
                    calcularTotalPaginasYCargar()
                }
                .addOnFailureListener { calcularTotalPaginasYCargar() }
        } else {
            calcularTotalPaginasYCargar()
        }
    }

    private fun gestionarFavorito(historia: Historia) {
        val user = auth.currentUser ?: return
        val userRef = db.collection("usuarios").document(user.uid)
        val historiaRef = db.collection("historias").document(historia.idHistoria)

        if (listaFavoritosUsuario.contains(historia.idHistoria)) {
            val userUpdate = hashMapOf("idFavoritas" to FieldValue.arrayRemove(historia.idHistoria))
            userRef.set(userUpdate, SetOptions.merge()).addOnSuccessListener {
                val historiaUpdate = hashMapOf("numFavoritos" to FieldValue.increment(-1))
                historiaRef.set(historiaUpdate, SetOptions.merge()).addOnSuccessListener {
                    listaFavoritosUsuario.remove(historia.idHistoria)
                    cargarHistoriasDeFirebase()
                }
            }
        } else {
            val userUpdate = hashMapOf("idFavoritas" to FieldValue.arrayUnion(historia.idHistoria))
            userRef.set(userUpdate, SetOptions.merge()).addOnSuccessListener {
                val historiaUpdate = hashMapOf("numFavoritos" to FieldValue.increment(1))
                historiaRef.set(historiaUpdate, SetOptions.merge()).addOnSuccessListener {
                    listaFavoritosUsuario.add(historia.idHistoria)
                    cargarHistoriasDeFirebase()
                }
            }
        }
    }

    private fun cargarHistoriasDeFirebase() {
        db.collection("historias").orderBy("titulo").limit(HISTORIAS_POR_PAGINA).get()
            .addOnSuccessListener { documentos ->
                val lista = documentos.map { doc ->
                    val h = doc.toObject(Historia::class.java)
                    h.idHistoria = doc.id
                    h
                }
                adapter.actualizarDatos(lista, listaFavoritosUsuario)
                actualizarVistaPaginacion()
            }
    }

    private fun checkUserSession() {
        val user = auth.currentUser
        if (user != null) {
            llUserLoggedIn.visibility = View.VISIBLE
            db.collection("usuarios").document(user.uid).get().addOnSuccessListener { doc ->
                if (doc.exists()) tvUsername.text = doc.getString("nombre")
            }
        } else {
            llUserLoggedIn.visibility = View.GONE
        }
    }

    private fun calcularTotalPaginasYCargar() {
        db.collection("historias").count().get(com.google.firebase.firestore.AggregateSource.SERVER)
            .addOnSuccessListener { snapshot ->
                val total = snapshot.count
                totalPaginas = Math.ceil(total.toDouble() / HISTORIAS_POR_PAGINA).toInt().coerceAtLeast(1)
                cargarHistoriasDeFirebase()
            }
    }

    private fun showUserMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.user_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_ajustes -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
                R.id.menu_buscar -> { startActivity(Intent(this, SearchActivity::class.java)); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun actualizarVistaPaginacion() {
        btnAnterior.isEnabled = paginaActual > 1
        btnSiguiente.isEnabled = paginaActual < totalPaginas
        llNumerosPaginas.removeAllViews()
        for (i in 1..totalPaginas) {
            val btnNum = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            btnNum.text = i.toString()
            btnNum.minWidth = 0; btnNum.minimumWidth = 0; btnNum.setPadding(35, 0, 35, 0)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(4, 0, 4, 0); btnNum.layoutParams = params
            if (i == paginaActual) {
                btnNum.setBackgroundColor(Color.parseColor("#0D47A1")); btnNum.setTextColor(Color.WHITE); btnNum.strokeWidth = 0
            } else {
                btnNum.setTextColor(Color.parseColor("#0D47A1")); btnNum.setStrokeColorResource(android.R.color.darker_gray)
                btnNum.setOnClickListener { paginaActual = i; cargarHistoriasDeFirebase() }
            }
            llNumerosPaginas.addView(btnNum)
        }
    }

    private fun sincronizarTemaDesdeNube() {
        val user = auth.currentUser ?: return
        db.collection("usuarios").document(user.uid).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val preferencias = document.get("preferencias") as? Map<*, *>
                val temaRemoto = preferencias?.get("tema") as? String
                temaRemoto?.let {
                    val modeRemoto = when (it) {
                        "claro" -> AppCompatDelegate.MODE_NIGHT_NO
                        "oscuro" -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                    getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().putInt("theme_mode", modeRemoto).apply()
                    AppCompatDelegate.setDefaultNightMode(modeRemoto)
                }
            }
        }
    }
}