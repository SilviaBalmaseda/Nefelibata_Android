package com.example.nefelibata

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nefelibata.adapters.HistoriaAdapter
import com.example.nefelibata.databinding.ActivityMainBinding
import com.example.nefelibata.models.Historia
import com.example.nefelibata.models.Usuario
import com.example.nefelibata.ui.MisHistoriasActivity
import com.example.nefelibata.ui.SearchActivity
import com.example.nefelibata.ui.SettingsActivity
import com.example.nefelibata.utils.Constants
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlin.math.ceil

/**
 * Actividad principal que muestra el listado de historias con paginación.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: HistoriaAdapter

    // Variables para control de paginación
    private var paginaActual = 1
    private var totalPaginas = 1
    private val historiasPorPagina = 5L
    
    // Almacena el último documento visible de cada página para usar con startAfter() de Firebase
    private val paginasSnapshots = mutableMapOf<Int, DocumentSnapshot?>()
    
    // Lista local de IDs de historias favoritas del usuario logueado
    private var listaFavoritosUsuario = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Carga y aplica el tema guardado antes de inflar la vista
        val sharedPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val modeSaved = sharedPrefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(modeSaved)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Manejo de Insets para diseño edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        checkUserSession()
        sincronizarTemaDesdeNube()

        // Listeners de navegación de páginas
        binding.btnAnterior.setOnClickListener { 
            if (paginaActual > 1) { 
                paginaActual--
                cargarHistoriasDeFirebase() 
            } 
        }
        binding.btnSiguiente.setOnClickListener { 
            if (paginaActual < totalPaginas) { 
                paginaActual++
                cargarHistoriasDeFirebase() 
            } 
        }
        
        // Icono de perfil/ajustes
        binding.ivSettings.setOnClickListener { view -> showUserMenu(view) }
        
        obtenerFavoritosYCargarHistorias()
    }

    override fun onResume() {
        super.onResume()
        // Refresca datos al volver a la actividad para actualizar favoritos o cambios en historias
        obtenerFavoritosYCargarHistorias(mantenerPagina = true)
    }

    private fun setupRecyclerView() {
        binding.rvHistorias.layoutManager = LinearLayoutManager(this)
        adapter = HistoriaAdapter(emptyList(), emptyList()) { historia ->
            gestionarFavorito(historia)
        }
        binding.rvHistorias.adapter = adapter
    }

    /**
     * Recupera la lista de favoritos del usuario y luego inicia la carga de historias.
     */
    private fun obtenerFavoritosYCargarHistorias(mantenerPagina: Boolean = false) {
        val user = auth.currentUser
        if (user != null) {
            db.collection("usuarios").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    val usuario = doc.toObject(Usuario::class.java)
                    listaFavoritosUsuario = usuario?.idFavoritas?.toMutableList() ?: mutableListOf()
                    calcularTotalPaginasYCargar(mantenerPagina)
                }
                .addOnFailureListener { 
                    Toast.makeText(this, "Error cargando favoritos", Toast.LENGTH_SHORT).show()
                    calcularTotalPaginasYCargar(mantenerPagina) 
                }
        } else {
            calcularTotalPaginasYCargar(mantenerPagina)
        }
    }

    /**
     * Agrega o quita una historia de los favoritos del usuario en Firestore.
     */
    private fun gestionarFavorito(historia: Historia) {
        val user = auth.currentUser ?: return
        val userRef = db.collection("usuarios").document(user.uid)
        val historiaRef = db.collection("historias").document(historia.idHistoria)

        if (listaFavoritosUsuario.contains(historia.idHistoria)) {
            // Eliminar de favoritos
            listaFavoritosUsuario.remove(historia.idHistoria)
            userRef.set(hashMapOf("idFavoritas" to FieldValue.arrayRemove(historia.idHistoria)), SetOptions.merge())
                .addOnSuccessListener {
                    historiaRef.set(hashMapOf("numFavoritos" to FieldValue.increment(-1)), SetOptions.merge())
                        .addOnSuccessListener { cargarHistoriasDeFirebase() }
                }
        } else {
            // Añadir a favoritos
            listaFavoritosUsuario.add(historia.idHistoria)
            userRef.set(hashMapOf("idFavoritas" to FieldValue.arrayUnion(historia.idHistoria)), SetOptions.merge())
                .addOnSuccessListener {
                    historiaRef.set(hashMapOf("numFavoritos" to FieldValue.increment(1)), SetOptions.merge())
                        .addOnSuccessListener { cargarHistoriasDeFirebase() }
                }
        }
    }

    /**
     * Carga las historias correspondientes a la página actual usando cursores de Firestore.
     */
    private fun cargarHistoriasDeFirebase() {
        if (paginaActual == 1) paginasSnapshots.clear()

        var query = db.collection("historias").orderBy("titulo").limit(historiasPorPagina)
        
        // Si no es la primera página, empezar después del último documento de la página anterior
        if (paginaActual > 1) {
            val lastVisible = paginasSnapshots[paginaActual - 1]
            if (lastVisible != null) {
                query = query.startAfter(lastVisible)
            } else {
                cargarDesdeInicioHastaPagina(paginaActual)
                return
            }
        }

        query.get().addOnSuccessListener { documentos ->
            if (documentos.isEmpty) {
                adapter.actualizarDatos(emptyList(), listaFavoritosUsuario)
                actualizarVistaPaginacion()
            } else {
                // Guardar el último snapshot para la siguiente navegación
                paginasSnapshots[paginaActual] = documentos.documents[documentos.size() - 1]
                val lista = documentos.mapNotNull { doc ->
                    doc.toObject(Historia::class.java)?.apply { idHistoria = doc.id }
                }
                adapter.actualizarDatos(lista, listaFavoritosUsuario)
                actualizarVistaPaginacion()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error cargando historias", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Caso de respaldo: reconstruye el mapa de snapshots si se pierde la continuidad.
     */
    private fun cargarDesdeInicioHastaPagina(objetivo: Int) {
        db.collection("historias").orderBy("titulo").limit((objetivo * historiasPorPagina)).get()
            .addOnSuccessListener { documentos ->
                if (!documentos.isEmpty) {
                    for (p in 1..objetivo) {
                        val index = (p * historiasPorPagina.toInt()) - 1
                        if (index < documentos.size()) {
                            paginasSnapshots[p] = documentos.documents[index]
                        }
                    }
                    
                    val startIndex = (objetivo - 1) * historiasPorPagina.toInt()
                    if (startIndex < documentos.size()) {
                        val subListaDocs = documentos.documents.subList(startIndex, documentos.size())
                        val lista = subListaDocs.mapNotNull { doc ->
                            doc.toObject(Historia::class.java)?.apply { idHistoria = doc.id }
                        }
                        adapter.actualizarDatos(lista, listaFavoritosUsuario)
                        actualizarVistaPaginacion()
                    }
                }
            }
    }

    /**
     * Muestra/oculta UI del usuario según si hay sesión activa.
     */
    private fun checkUserSession() {
        val user = auth.currentUser
        if (user != null) {
            binding.llUserLoggedIn.visibility = View.VISIBLE
            db.collection("usuarios").document(user.uid).get().addOnSuccessListener { doc ->
                if (doc.exists()) binding.tvUsername.text = doc.getString("nombre")
            }
        } else {
            binding.llUserLoggedIn.visibility = View.GONE
            aplicarIdiomaPorDefecto()
        }
    }

    private fun aplicarIdiomaPorDefecto() {
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("es"))
        }
    }

    /**
     * Obtiene el conteo total de documentos para determinar cuántas páginas existen.
     */
    private fun calcularTotalPaginasYCargar(mantenerPagina: Boolean) {
        db.collection("historias").count().get(com.google.firebase.firestore.AggregateSource.SERVER)
            .addOnSuccessListener { snapshot ->
                totalPaginas = ceil(snapshot.count.toDouble() / historiasPorPagina).toInt().coerceAtLeast(1)
                
                if (!mantenerPagina) {
                    paginaActual = 1
                    paginasSnapshots.clear()
                } else if (paginaActual > totalPaginas) {
                    paginaActual = totalPaginas
                }
                
                cargarHistoriasDeFirebase()
            }.addOnFailureListener {
                cargarHistoriasDeFirebase()
            }
    }

    /**
     * Muestra el menú emergente de opciones de usuario.
     */
    private fun showUserMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.user_menu, popup.menu)
        
        // Hack para mostrar iconos en PopupMenu si están definidos en el XML
        try {
            val fieldMPopup = PopupMenu::class.java.getDeclaredField("mPopup")
            fieldMPopup.isAccessible = true
            val mPopup = fieldMPopup.get(popup)
            mPopup.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java).invoke(mPopup, true)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error icons", e)
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_ajustes -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
                R.id.menu_buscar -> { startActivity(Intent(this, SearchActivity::class.java)); true }
                R.id.menu_mis_historias -> { 
                    startActivity(Intent(this, MisHistoriasActivity::class.java))
                    true 
                }
                else -> false
            }
        }
        popup.show()
    }

    /**
     * Genera dinámicamente los botones de números de página.
     */
    private fun actualizarVistaPaginacion() {
        binding.btnAnterior.isEnabled = paginaActual > 1
        binding.btnSiguiente.isEnabled = paginaActual < totalPaginas
        binding.llNumerosPaginas.removeAllViews()
        
        for (i in 1..totalPaginas) {
            val btnNum = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            btnNum.text = i.toString()
            btnNum.minWidth = 0; btnNum.minimumWidth = 0; btnNum.setPadding(35, 0, 35, 0)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(4, 0, 4, 0); btnNum.layoutParams = params
            
            if (i == paginaActual) {
                // Resaltado de página actual
                btnNum.setBackgroundColor("#0D47A1".toColorInt()); btnNum.setTextColor(-0x1); btnNum.strokeWidth = 0
            } else {
                btnNum.setTextColor("#0D47A1".toColorInt()); btnNum.setStrokeColorResource(android.R.color.darker_gray)
                btnNum.setOnClickListener { 
                    if (i < paginaActual || !paginasSnapshots.containsKey(i - 1)) {
                        paginasSnapshots.clear()
                    }
                    paginaActual = i
                    cargarHistoriasDeFirebase()
                }
            }
            binding.llNumerosPaginas.addView(btnNum)
        }
    }

    /**
     * Sincroniza las preferencias de tema y lenguaje del usuario guardadas en la nube.
     */
    private fun sincronizarTemaDesdeNube() {
        val user = auth.currentUser ?: return
        db.collection("usuarios").document(user.uid).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val preferencias = document.get("preferencias") as? Map<*, *>
                
                // Aplicar tema
                val temaRemoto = preferencias?.get("tema") as? String
                temaRemoto?.let {
                    val modeRemoto = when (it) {
                        "claro" -> AppCompatDelegate.MODE_NIGHT_NO
                        "oscuro" -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                    val sp = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                    if (sp.getInt("theme_mode", -1) != modeRemoto) {
                        sp.edit { putInt("theme_mode", modeRemoto) }
                        AppCompatDelegate.setDefaultNightMode(modeRemoto)
                    }
                }

                // Aplicar lenguaje
                val langRemoto = (preferencias?.get("leng") as? String) ?: Constants.DEFAULT_LANG
                val androidLangCode = if (langRemoto == Constants.LANG_ES) "es" else "en"
                
                if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != androidLangCode) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(androidLangCode))
                }
            }
        }
    }
}
