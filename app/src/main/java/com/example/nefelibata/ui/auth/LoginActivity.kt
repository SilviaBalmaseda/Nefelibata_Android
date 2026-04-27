package com.example.nefelibata.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.example.nefelibata.MainActivity
import com.example.nefelibata.R
import com.example.nefelibata.databinding.ActivityLoginBinding
import com.example.nefelibata.utils.Constants
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

/**
 * Pantalla de inicio de sesión y registro de usuarios.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var credentialManager: CredentialManager

    private var isLoginMode = true // Controla si la vista muestra Login o Registro

    override fun onStart() {
        super.onStart()
        // Si el usuario ya está logueado, redirigir directamente a la pantalla principal
        if (auth.currentUser != null) {
            irAMainActivity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        credentialManager = CredentialManager.create(this)

        setupListeners()
    }

    private fun setupListeners() {
        // Alternar entre modo Login y Registro
        binding.toggleAuthMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_tab_login -> {
                        isLoginMode = true
                        binding.tilNombre.visibility = View.GONE
                        binding.etEmail.hint = getString(R.string.email_username_hint)
                        binding.btnAction.text = getString(R.string.login_button)
                    }
                    R.id.btn_tab_register -> {
                        isLoginMode = false
                        binding.tilNombre.visibility = View.VISIBLE
                        binding.etEmail.hint = getString(R.string.email_hint)
                        binding.btnAction.text = getString(R.string.register_button)
                    }
                }
            }
        }

        // Botón principal (Acceder / Registrarse)
        binding.btnAction.setOnClickListener {
            val inputIdentificador = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val nombre = binding.etNombre.text.toString().trim()

            // 1. Validación de campos vacíos (Critico para ambos modos)
            if (inputIdentificador.isEmpty() || password.isEmpty() || (!isLoginMode && nombre.isEmpty())) {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. Validaciones específicas para el modo registro
            if (!isLoginMode) {
                // El email debe tener un formato válido en registro
                if (!Patterns.EMAIL_ADDRESS.matcher(inputIdentificador).matches()) {
                    Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show() // O un error de email inválido si existiera
                    return@setOnClickListener
                }
                
                // Longitud mínima de nombre
                if (nombre.replace(" ", "").length < Constants.MIN_NAME_LENGTH) {
                    Toast.makeText(this, getString(R.string.min_name_chars, Constants.MIN_NAME_LENGTH), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // Longitud mínima de contraseña
                if (password.length < 6) {
                    Toast.makeText(this, getString(R.string.password_too_short), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            binding.btnAction.isEnabled = false

            if (isLoginMode) {
                realizarLogin(inputIdentificador, password)
            } else {
                realizarRegistro(inputIdentificador, password, nombre)
            }
        }

        binding.btnGoogle.setOnClickListener { iniciarSesionConGoogle() }
    }

    /**
     * Procesa el login intentando por email o por nombre de usuario si no se detecta formato email.
     */
    private fun realizarLogin(identificador: String, pass: String) {
        if (Patterns.EMAIL_ADDRESS.matcher(identificador).matches()) {
            loginConEmail(identificador, pass)
        } else {
            // Si no es un email, buscamos el nombre de usuario en Firestore para obtener su email asociado
            db.collection("usuarios").whereEqualTo("nombre", identificador).get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val emailEncontrado = documents.documents[0].getString("email") ?: ""
                        loginConEmail(emailEncontrado, pass)
                    } else {
                        binding.btnAction.isEnabled = true
                        Toast.makeText(this, getString(R.string.user_not_found), Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    binding.btnAction.isEnabled = true
                    Toast.makeText(this, getString(R.string.auth_error), Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun realizarRegistro(email: String, pass: String, nombre: String) {
        // Verificar disponibilidad del nombre de usuario antes de registrar en Auth
        db.collection("usuarios").whereEqualTo("nombre", nombre).get().addOnSuccessListener { docs ->
            if (docs.isEmpty) {
                auth.createUserWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                            guardarUsuarioEnFirestore(userId, nombre, email)
                        } else {
                            binding.btnAction.isEnabled = true
                            Toast.makeText(this, task.exception?.localizedMessage, Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                binding.btnAction.isEnabled = true
                Toast.makeText(this, getString(R.string.name_already_exists), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loginConEmail(email: String, pass: String) {
        auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            binding.btnAction.isEnabled = true
            if (task.isSuccessful) irAMainActivity()
            else Toast.makeText(this, getString(R.string.login_error), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Inicia el flujo de autenticación con Google usando CredentialManager.
     */
    private fun iniciarSesionConGoogle() {
        val serverClientId = "1048728494011-la208k90k2iqha6aplvoe5tebp601985.apps.googleusercontent.com"
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@LoginActivity, request)
                val credential = result.credential
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    auth.signInWithCredential(firebaseCredential).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser!!
                            // Verificamos si el usuario de Google ya existe en nuestra DB de Firestore
                            db.collection("usuarios").document(user.uid).get().addOnSuccessListener { doc ->
                                if (!doc.exists()) guardarUsuarioEnFirestore(user.uid, user.displayName ?: "Usuario", user.email ?: "")
                                else irAMainActivity()
                            }
                        }
                    }
                }
            } catch (e: Exception) { Log.e("LoginActivity", "Error Google", e) }
        }
    }

    /**
     * Crea el documento inicial del usuario en Firestore.
     */
    private fun guardarUsuarioEnFirestore(userId: String, nombre: String, email: String) {
        val nuevoUsuario = hashMapOf(
            "idUsuario" to userId,
            "nombre" to nombre,
            "email" to email,
            "idFavoritas" to emptyList<String>(),
            "fotoUser" to "",
            "numSeguidor" to 0
        )
        db.collection("usuarios").document(userId).set(nuevoUsuario)
            .addOnSuccessListener { irAMainActivity() }
            .addOnFailureListener { binding.btnAction.isEnabled = true }
    }

    private fun irAMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).apply { 
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK 
        })
        finish()
    }
}
