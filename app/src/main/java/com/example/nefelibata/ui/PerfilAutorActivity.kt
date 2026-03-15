package com.example.nefelibata.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.nefelibata.R
import com.example.nefelibata.adapters.HistoriaAdapter
import com.example.nefelibata.models.Historia
import com.example.nefelibata.models.Usuario
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class PerfilAutorActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var rvHistorias: RecyclerView
    private lateinit var adapter: HistoriaAdapter
    private lateinit var btnSeguir: MaterialButton
    private lateinit var tvSeguidores: TextView
    private lateinit var ivToggle: ImageView
    
    private var idAutor: String = ""
    private var listaSiguiendoUsuario = mutableListOf<String>()
    private var numSeguidoresActual = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil_autor)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        idAutor = intent.getStringExtra("idAutor") ?: ""

        if (idAutor.isEmpty()) { finish(); return }

        // Inicializar vistas
        val ivBack = findViewById<ImageView>(R.id.iv_back_perfil)
        val tvNombre = findViewById<TextView>(R.id.tv_perfil_nombre)
        tvSeguidores = findViewById(R.id.tv_perfil_seguidores)
        val ivFoto = findViewById<ImageView>(R.id.iv_perfil_foto)
        btnSeguir = findViewById(R.id.btn_perfil_seguir)
        rvHistorias = findViewById(R.id.rv_perfil_historias)
        ivToggle = findViewById(R.id.iv_perfil_toggle_obras)
        val llHeaderObras = findViewById<LinearLayout>(R.id.ll_perfil_obras_header)

        ivBack.setOnClickListener { finish() }

        // Lógica colapsable
        llHeaderObras.setOnClickListener {
            if (rvHistorias.visibility == View.VISIBLE) {
                rvHistorias.visibility = View.GONE
                ivToggle.setImageResource(android.R.drawable.arrow_down_float)
            } else {
                rvHistorias.visibility = View.VISIBLE
                ivToggle.setImageResource(android.R.drawable.arrow_up_float)
            }
        }

        setupRecyclerView()
        obtenerDatosUsuarioLogueado()
        cargarDatosAutor(tvNombre, ivFoto)
        cargarObrasAutor()

        btnSeguir.setOnClickListener { gestionarSeguimiento() }
    }

    private fun obtenerDatosUsuarioLogueado() {
        val currentUser = auth.currentUser ?: return
        db.collection("usuarios").document(currentUser.uid).get().addOnSuccessListener { doc ->
            val user = doc.toObject(Usuario::class.java)
            listaSiguiendoUsuario = user?.idSiguiendo?.toMutableList() ?: mutableListOf()
            actualizarBotonSeguir()
        }
    }

    private fun gestionarSeguimiento() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Inicia sesión para seguir autores", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentUser.uid == idAutor) {
            Toast.makeText(this, "No puedes seguirte a ti mismo", Toast.LENGTH_SHORT).show()
            return
        }

        val userRef = db.collection("usuarios").document(currentUser.uid)
        val autorRef = db.collection("usuarios").document(idAutor)

        if (listaSiguiendoUsuario.contains(idAutor)) {
            // DEJAR DE SEGUIR (Optimista)
            listaSiguiendoUsuario.remove(idAutor)
            numSeguidoresActual--
            userRef.update("idSiguiendo", FieldValue.arrayRemove(idAutor))
            autorRef.update("numSeguidor", FieldValue.increment(-1))
        } else {
            // SEGUIR (Optimista)
            listaSiguiendoUsuario.add(idAutor)
            numSeguidoresActual++
            userRef.update("idSiguiendo", FieldValue.arrayUnion(idAutor))
            autorRef.update("numSeguidor", FieldValue.increment(1))
        }
        
        actualizarBotonSeguir()
        tvSeguidores.text = "$numSeguidoresActual SEGUIDORES"
    }

    private fun actualizarBotonSeguir() {
        if (listaSiguiendoUsuario.contains(idAutor)) {
            // MODO DEJAR DE SEGUIR: Color Rojo
            btnSeguir.text = "Dejar de seguir"
            btnSeguir.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F44336")) // Rojo Material
        } else {
            // MODO SEGUIR: Color Azul Principal
            btnSeguir.text = "Seguir"
            btnSeguir.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.primary_blue))
        }
    }

    private fun setupRecyclerView() {
        rvHistorias.layoutManager = LinearLayoutManager(this)
        adapter = HistoriaAdapter(emptyList(), emptyList()) { }
        rvHistorias.adapter = adapter
    }

    private fun cargarDatosAutor(tvName: TextView, ivPhoto: ImageView) {
        db.collection("usuarios").document(idAutor).get().addOnSuccessListener { doc ->
            val usuario = doc.toObject(Usuario::class.java)
            usuario?.let {
                tvName.text = it.nombre
                numSeguidoresActual = it.numSeguidor
                tvSeguidores.text = "$numSeguidoresActual SEGUIDORES"
                if (!it.fotoUser.isNullOrEmpty()) {
                    ivPhoto.load(it.fotoUser) { 
                        transformations(CircleCropTransformation())
                        placeholder(android.R.drawable.ic_menu_gallery)
                    }
                }
            }
        }
    }

    private fun cargarObrasAutor() {
        db.collection("historias").whereEqualTo("autor.id", idAutor).get().addOnSuccessListener { query ->
            val lista = query.mapNotNull { doc ->
                val h = doc.toObject(Historia::class.java)
                h.idHistoria = doc.id
                h
            }
            adapter.actualizarDatos(lista, emptyList())
        }
    }
}