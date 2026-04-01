package com.example.ilutomo

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        sharedPreferences = getSharedPreferences("iLutoMoPrefs", Context.MODE_PRIVATE)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val cbRememberMe = findViewById<CheckBox>(R.id.cbRememberMe)
        val btnLogin = findViewById<AppCompatButton>(R.id.btnLogin)
        val btnSignUp = findViewById<AppCompatButton>(R.id.btnSignUp)
        val btnForgot = findViewById<AppCompatButton>(R.id.btnForgotPassword)

        // --- PRE-FILL LOGIC (Instead of Auto-Login) ---
        val savedEmail = sharedPreferences.getString("saved_email", "")
        val savedPassword = sharedPreferences.getString("saved_password", "")
        val isRemembered = sharedPreferences.getBoolean("isRemembered", false)

        if (isRemembered) {
            etEmail.setText(savedEmail)
            etPassword.setText(savedPassword)
            cbRememberMe.isChecked = true
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // --- SAVE CREDENTIALS IF CHECKED ---
                        val editor = sharedPreferences.edit()
                        if (cbRememberMe.isChecked) {
                            editor.putString("saved_email", email)
                            editor.putString("saved_password", password)
                            editor.putBoolean("isRemembered", true)
                        } else {
                            // Clear them if the user unchecks the box
                            editor.clear()
                        }
                        editor.apply()

                        val uid = auth.currentUser?.uid
                        if (uid != null) checkUserTypeAndRedirect(uid)
                    } else {
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
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

    private fun checkUserTypeAndRedirect(uid: String) {
        db.collection("users").document(uid).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val type = document.getString("accountType")
                val intent = if (type == "Business") {
                    Intent(this, BusinessDashboardActivity::class.java)
                } else {
                    Intent(this, HomeActivity::class.java)
                }
                startActivity(intent)
                // We don't finish() here so they can come back if they logout
                finish()
            }
        }
    }
}