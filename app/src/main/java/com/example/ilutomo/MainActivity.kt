package com.example.ilutomo

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        sharedPreferences = getSharedPreferences("iLutoMoPrefs", Context.MODE_PRIVATE)

        // --- GOOGLE SIGN-IN CONFIG ---
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val cbRememberMe = findViewById<CheckBox>(R.id.cbRememberMe)
        val btnLogin = findViewById<AppCompatButton>(R.id.btnLogin)
        val btnGoogle = findViewById<AppCompatButton>(R.id.btnGoogleLogin)
        val btnSignUp = findViewById<AppCompatButton>(R.id.btnSignUp)
        val btnForgot = findViewById<AppCompatButton>(R.id.btnForgotPassword)

        // --- PRE-FILL LOGIC ---
        val savedEmail = sharedPreferences.getString("saved_email", "")
        val savedPassword = sharedPreferences.getString("saved_password", "")
        if (sharedPreferences.getBoolean("isRemembered", false)) {
            etEmail.setText(savedEmail)
            etPassword.setText(savedPassword)
            cbRememberMe.isChecked = true
        }

        // 1. MANUAL LOGIN
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    saveCredentials(email, password, cbRememberMe.isChecked)
                    val userEmail = auth.currentUser?.email ?: email
                    logActivityToAdmin(userEmail, "Login", "Manual Email/Password Login")
                    auth.currentUser?.uid?.let { checkUserTypeAndRedirect(it) }
                } else {
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 2. GOOGLE LOGIN
        btnGoogle.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                startActivityForResult(signInIntent, 1001)
            }
        }

        btnSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        btnForgot.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isNotEmpty()) {
                auth.sendPasswordResetEmail(email).addOnSuccessListener {
                    Toast.makeText(this, "Reset link sent!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Enter email first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                user?.let { checkUserTypeAndRedirect(it.uid) }
            } else {
                Toast.makeText(this, "Firebase Auth failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCredentials(email: String, pass: String, remember: Boolean) {
        val editor = sharedPreferences.edit()
        if (remember) {
            editor.putString("saved_email", email)
            editor.putString("saved_password", pass)
            editor.putBoolean("isRemembered", true)
        } else {
            editor.clear()
        }
        editor.apply()
    }

    private fun checkUserTypeAndRedirect(uid: String) {
        val currentUserEmail = auth.currentUser?.email ?: "Unknown"

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val type = document.getString("accountType") ?: "Personal"
                    logActivityToAdmin(currentUserEmail, "Login", "Login into $type account")

                    val intent = if (type == "Business") {
                        Intent(this, BusinessDashboardActivity::class.java)
                    } else {
                        Intent(this, HomeActivity::class.java)
                    }
                    startActivity(intent)
                    finish()
                } else {
                    logActivityToAdmin(currentUserEmail, "Auth Initialized", "New user redirected to SignUp")
                    val intent = Intent(this, SignUpActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e("FIRESTORE_LOGIN", "Error fetching user doc", e)
                Toast.makeText(this, "Database error: ${e.message}. Check your Firestore permissions.", Toast.LENGTH_LONG).show()
                // Log them out so they can try again once permissions are fixed
                auth.signOut()
            }
    }

    private fun logActivityToAdmin(email: String, action: String, details: String) {
        val log = hashMapOf(
            "userEmail" to email,
            "action" to action,
            "details" to details,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        db.collection("UserActivities").add(log)
            .addOnFailureListener { e -> Log.w("LOG_ADMIN", "Failed to log activity", e) }
    }
}