package com.example.nefelibata

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nefelibata.R
import com.example.nefelibata.adapters.HistoriaAdapter
import com.example.nefelibata.models.Historia
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
    private lateinit var tvLogout: TextView
    
    private lateinit var llGenerosHeader: LinearLayout
    private lateinit var ivToggleGeneros: ImageView
    private lateinit var cgGeneros: ChipGroup

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
        llUserLoggedIn = findViewById(R.id.ll_user_logged_in)
        tvUsername = findViewById(R.id.tv_username)
        tvLogout = findViewById(R.id.tv_logout)
        
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
            chip.textAlignment = View.TEXT_ALIGNMENT_CENTER
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

        tvLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
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