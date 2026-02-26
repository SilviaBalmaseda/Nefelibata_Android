package com.example.nefelibata.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.nefelibata.R
import com.example.nefelibata.ui.auth.LoginActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var tvName: TextView
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Antes de inflar la vista, aseguramos el tema
        sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val modeSaved = sharedPreferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(modeSaved)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val mainSettings = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.main_settings)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(mainSettings) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())

            // Aplicamos el margen a toda la pantalla por igual (como en tu MainActivity)
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)

            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        tvName = findViewById(R.id.tv_settings_username)
        val ivEdit = findViewById<ImageView>(R.id.iv_edit_name)
        val btnLogout = findViewById<MaterialButton>(R.id.btn_logout)
        val ivBack = findViewById<ImageView>(R.id.iv_back)
        val toggleTheme = findViewById<MaterialButtonToggleGroup>(R.id.toggle_theme)

        when (modeSaved) {
            AppCompatDelegate.MODE_NIGHT_NO -> toggleTheme.check(R.id.btn_theme_light)
            AppCompatDelegate.MODE_NIGHT_YES -> toggleTheme.check(R.id.btn_theme_dark)
            else -> toggleTheme.check(R.id.btn_theme_system)
        }

        val user = auth.currentUser
        if (user != null) {
            db.collection("usuarios").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        tvName.text = document.getString("nombre") ?: "Usuario"
                        val preferencias = document.get("preferencias") as? Map<*, *>
                        val temaRemoto = preferencias?.get("tema") as? String
                        temaRemoto?.let { aplicarTemaDesdeString(it) }
                    }
                }
        }

        ivBack.setOnClickListener { finish() }

        val editListener = { mostrarDialogoEdicion() }
        tvName.setOnClickListener { editListener() }
        ivEdit.setOnClickListener { editListener() }

        toggleTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val temaString = when (checkedId) {
                    R.id.btn_theme_light -> "claro"
                    R.id.btn_theme_dark -> "oscuro"
                    else -> "sistema"
                }
                guardarPreferenciaTema(temaString)
            }
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun aplicarTemaDesdeString(tema: String) {
        val mode = when (tema) {
            "claro" -> AppCompatDelegate.MODE_NIGHT_NO
            "oscuro" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            sharedPreferences.edit().putInt("theme_mode", mode).apply()
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    private fun guardarPreferenciaTema(tema: String) {
        val mode = when (tema) {
            "claro" -> AppCompatDelegate.MODE_NIGHT_NO
            "oscuro" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        
        sharedPreferences.edit().putInt("theme_mode", mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)

        val user = auth.currentUser
        if (user != null) {
            val data = hashMapOf("preferencias" to hashMapOf("tema" to tema))
            db.collection("usuarios").document(user.uid).set(data, SetOptions.merge())
        }
    }

    private fun mostrarDialogoEdicion() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Cambiar nombre de usuario")
        val input = TextInputEditText(this)
        input.setPadding(50, 40, 50, 40)
        input.setText(tvName.text)
        builder.setView(input)
        builder.setPositiveButton("Guardar") { _, _ ->
            val nuevoNombre = input.text.toString().trim()
            if (nuevoNombre.isNotEmpty()) {
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    db.collection("usuarios").document(userId).update("nombre", nuevoNombre)
                        .addOnSuccessListener {
                            tvName.text = nuevoNombre
                            Toast.makeText(this, "Nombre actualizado", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }
        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }
        builder.show()
    }
}