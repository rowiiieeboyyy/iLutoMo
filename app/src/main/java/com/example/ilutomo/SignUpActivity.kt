package com.example.ilutomo

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var selectedAccountType: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // UI References - These now match the XML IDs perfectly
        val etEmail = findViewById<EditText>(R.id.etSignUpEmail)
        val etPassword = findViewById<EditText>(R.id.etSignUpPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnPersonal = findViewById<AppCompatButton>(R.id.btnPersonal)
        val btnBusiness = findViewById<AppCompatButton>(R.id.btnBusiness)
        val btnCancel = findViewById<AppCompatButton>(R.id.btnCancel)
        val btnSubmitSignUp = findViewById<AppCompatButton>(R.id.btnSubmitSignUp)

        // Account Type Logic
        btnPersonal.setOnClickListener {
            selectedAccountType = "Personal"
            highlightButton(btnPersonal, btnBusiness)
        }

        btnBusiness.setOnClickListener {
            selectedAccountType = "Business"
            highlightButton(btnBusiness, btnPersonal)
        }

        btnCancel.setOnClickListener {
            finish()
        }

        btnSubmitSignUp.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedAccountType.isEmpty()) {
                Toast.makeText(this, "Please select an account type", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 8) {
                Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val uid = auth.currentUser?.uid
                        if (uid != null) {
                            saveUserToFirestore(uid, email, selectedAccountType)
                        }
                    } else {
                        Toast.makeText(this, "Sign up failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun saveUserToFirestore(uid: String, email: String, accountType: String) {
        val userMap = hashMapOf(
            "email" to email,
            "accountType" to accountType,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("users").document(uid)
            .set(userMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Account Created Successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun highlightButton(selected: AppCompatButton, unselected: AppCompatButton) {
        selected.setBackgroundResource(R.drawable.button_solid)
        selected.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        unselected.setBackgroundResource(R.drawable.button_outline)
        unselected.setTextColor(ContextCompat.getColor(this, R.color.dark_green))
    }
}