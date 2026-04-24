package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private var selectedAccountType: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val btnPersonal = findViewById<AppCompatButton>(R.id.btnPersonal)
        val btnBusiness = findViewById<AppCompatButton>(R.id.btnBusiness)
        val btnCancel = findViewById<AppCompatButton>(R.id.btnCancel)
        val btnSubmitSignUp = findViewById<AppCompatButton>(R.id.btnSubmitSignUp)

        btnPersonal.setOnClickListener {
            selectedAccountType = "Personal"
            highlightButton(btnPersonal, btnBusiness)
        }

        btnBusiness.setOnClickListener {
            selectedAccountType = "Business"
            highlightButton(btnBusiness, btnPersonal)
        }

        btnCancel.setOnClickListener { finish() }

        btnSubmitSignUp.setOnClickListener {
            if (selectedAccountType.isEmpty()) {
                Toast.makeText(this, "Please select an account type first", Toast.LENGTH_SHORT).show()
            } else {
                // FIX 1: Force Account Picker by signing out of the local Google Client first
                googleSignInClient.signOut().addOnCompleteListener {
                    val signInIntent = googleSignInClient.signInIntent
                    startActivityForResult(signInIntent, 1002)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1002) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google Sign-Up failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                user?.let { checkUserInFirestore(it.uid, it.email ?: "") }
            }
        }
    }

    // FIX 2: Check if the user exists before logging "Registration"
    private fun checkUserInFirestore(uid: String, email: String) {
        db.collection("users").document(uid).get().addOnSuccessListener { document ->
            if (document.exists()) {
                // Existing User: Just log Login and Redirect
                val userType = document.getString("accountType") ?: selectedAccountType
                logToAdmin(email, "Login", "User logged back into $userType account")
                redirectToDashboard(userType)
            } else {
                // New User: Save data, log Registration, and Redirect
                saveNewUser(uid, email)
            }
        }
    }

    private fun saveNewUser(uid: String, email: String) {
        val userMap = hashMapOf(
            "email" to email,
            "accountType" to selectedAccountType,
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        db.collection("users").document(uid).set(userMap)
            .addOnSuccessListener {
                logToAdmin(email, "Registration", "New $selectedAccountType account created")
                redirectToDashboard(selectedAccountType)
            }
    }

    // Helper for Admin Dashboard Logging
    private fun logToAdmin(email: String, action: String, details: String) {
        val log = hashMapOf(
            "userEmail" to email,
            "action" to action,
            "details" to details,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        db.collection("UserActivities").add(log)
    }

    private fun redirectToDashboard(type: String) {
        val intent = if (type == "Business") {
            Intent(this, BusinessDashboardActivity::class.java)
        } else {
            Intent(this, HomeActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    private fun highlightButton(selected: AppCompatButton, unselected: AppCompatButton) {
        selected.setBackgroundResource(R.drawable.button_solid)
        selected.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        unselected.setBackgroundResource(R.drawable.button_outline)
        unselected.setTextColor(ContextCompat.getColor(this, R.color.dark_green))
    }
}