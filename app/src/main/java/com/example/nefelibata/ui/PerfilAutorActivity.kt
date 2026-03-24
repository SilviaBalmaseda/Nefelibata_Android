package com.example.nefelibata.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.CircleCropTransformation
import com.example.nefelibata.R
import com.example.nefelibata.adapters.HistoriaAdapter
import com.example.nefelibata.databinding.ActivityPerfilAutorBinding
import com.example.nefelibata.databinding.DialogImageViewerBinding
import com.example.nefelibata.models.Historia
import com.example.nefelibata.models.Usuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class PerfilAutorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPerfilAutorBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: HistoriaAdapter
    
    private var idAutor: String = ""
    private var fotoUrlAutor: String? = null
    private var listaSiguiendoUsuario = mutableListOf<String>()
    private var listaFavoritosUsuario = mutableListOf<String>()
    private var numSeguidoresActual = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerfilAutorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        idAutor = intent.getStringExtra("idAutor") ?: ""

        if (idAutor.isEmpty()) { finish(); return }

        setupUI()
        setupRecyclerView()
        obtenerDatosUsuarioLogueado()
        cargarDatosAutor()
    }

    private fun setupUI() {
        binding.ivBackPerfil.setOnClickListener { finish() }

        binding.llPerfilObrasHeader.setOnClickListener {
            if (binding.rvPerfilHistorias.visibility == View.VISIBLE) {
                binding.rvPerfilHistorias.visibility = View.GONE
                binding.ivPerfilToggleObras.setImageResource(android.R.drawable.arrow_down_float)
            } else {
                binding.rvPerfilHistorias.visibility = View.VISIBLE
                binding.ivPerfilToggleObras.setImageResource(android.R.drawable.arrow_up_float)
            }
        }

        binding.ivPerfilFoto.setOnClickListener { mostrarImagenAmpliada() }
        binding.btnPerfilSeguir.setOnClickListener { gestionarSeguimiento() }
    }

    private fun mostrarImagenAmpliada() {
        if (fotoUrlAutor.isNullOrEmpty()) return

        val dialogBinding = DialogImageViewerBinding.inflate(layoutInflater)
        dialogBinding.ivFullImage.load(fotoUrlAutor) {
            crossfade(true)
            placeholder(android.R.drawable.ic_menu_gallery)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogBinding.root.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun obtenerDatosUsuarioLogueado() {
        val currentUser = auth.currentUser
        
        if (currentUser?.uid == idAutor) {
            binding.btnPerfilSeguir.visibility = View.GONE
        } else {
            binding.btnPerfilSeguir.visibility = View.VISIBLE
        }

        if (currentUser == null) {
            cargarObrasAutor()
            return
        }
        
        db.collection("usuarios").document(currentUser.uid).get().addOnSuccessListener { doc ->
            val user = doc.toObject(Usuario::class.java)
            listaSiguiendoUsuario = user?.idSiguiendo?.toMutableList() ?: mutableListOf()
            listaFavoritosUsuario = user?.idFavoritas?.toMutableList() ?: mutableListOf()
            actualizarBotonSeguir()
            cargarObrasAutor()
        }.addOnFailureListener {
            cargarObrasAutor()
        }
    }

    private fun gestionarSeguimiento() {
        val currentUser = auth.currentUser ?: run {
            Toast.makeText(this, getString(R.string.login_to_follow), Toast.LENGTH_SHORT).show()
            return
        }
        
        if (currentUser.uid == idAutor) {
            binding.btnPerfilSeguir.visibility = View.GONE
            return
        }

        val userRef = db.collection("usuarios").document(currentUser.uid)
        val autorRef = db.collection("usuarios").document(idAutor)

        if (listaSiguiendoUsuario.contains(idAutor)) {
            listaSiguiendoUsuario.remove(idAutor)
            numSeguidoresActual--
            userRef.update("idSiguiendo", FieldValue.arrayRemove(idAutor))
            autorRef.update("numSeguidor", FieldValue.increment(-1))
        } else {
            listaSiguiendoUsuario.add(idAutor)
            numSeguidoresActual++
            userRef.update("idSiguiendo", FieldValue.arrayUnion(idAutor))
            autorRef.update("numSeguidor", FieldValue.increment(1))
        }
        
        actualizarBotonSeguir()
        binding.tvPerfilSeguidores.text = getString(R.string.followers_count, numSeguidoresActual)
    }

    private fun actualizarBotonSeguir() {
        if (listaSiguiendoUsuario.contains(idAutor)) {
            binding.btnPerfilSeguir.text = getString(R.string.unfollow_button)
            binding.btnPerfilSeguir.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F44336"))
        } else {
            binding.btnPerfilSeguir.text = getString(R.string.follow_button)
            binding.btnPerfilSeguir.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_blue))
        }
    }

    private fun setupRecyclerView() {
        binding.rvPerfilHistorias.layoutManager = LinearLayoutManager(this)
        adapter = HistoriaAdapter(emptyList(), emptyList()) { historia ->
            gestionarFavorito(historia)
        }
        binding.rvPerfilHistorias.adapter = adapter
    }

    private fun gestionarFavorito(historia: Historia) {
        val user = auth.currentUser ?: return
        val userRef = db.collection("usuarios").document(user.uid)
        val historiaRef = db.collection("historias").document(historia.idHistoria)

        if (listaFavoritosUsuario.contains(historia.idHistoria)) {
            listaFavoritosUsuario.remove(historia.idHistoria)
            val userUpdate = hashMapOf("idFavoritas" to FieldValue.arrayRemove(historia.idHistoria))
            userRef.set(userUpdate, SetOptions.merge()).addOnSuccessListener {
                val historiaUpdate = hashMapOf("numFavoritos" to FieldValue.increment(-1))
                historiaRef.set(historiaUpdate, SetOptions.merge()).addOnSuccessListener {
                    cargarObrasAutor()
                }
            }
        } else {
            listaFavoritosUsuario.add(historia.idHistoria)
            val userUpdate = hashMapOf("idFavoritas" to FieldValue.arrayUnion(historia.idHistoria))
            userRef.set(userUpdate, SetOptions.merge()).addOnSuccessListener {
                val historiaUpdate = hashMapOf("numFavoritos" to FieldValue.increment(1))
                historiaRef.set(historiaUpdate, SetOptions.merge()).addOnSuccessListener {
                    cargarObrasAutor()
                }
            }
        }
    }

    private fun cargarDatosAutor() {
        db.collection("usuarios").document(idAutor).get().addOnSuccessListener { doc ->
            val usuario = doc.toObject(Usuario::class.java)
            usuario?.let {
                binding.tvPerfilNombre.text = it.nombre
                numSeguidoresActual = it.numSeguidor
                binding.tvPerfilSeguidores.text = getString(R.string.followers_count, numSeguidoresActual)
                fotoUrlAutor = it.fotoUser
                if (!fotoUrlAutor.isNullOrEmpty()) {
                    binding.ivPerfilFoto.load(fotoUrlAutor) { 
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
            adapter.actualizarDatos(lista, listaFavoritosUsuario)
        }
    }
}
