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

        // Configure Google Sign-In
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
                val signInIntent = googleSignInClient.signInIntent
                startActivityForResult(signInIntent, 1002)
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
                user?.let { saveUserToFirestore(it.uid, it.email ?: "") }
            }
        }
    }

    private fun saveUserToFirestore(uid: String, email: String) {
        val userMap = hashMapOf(
            "email" to email,
            "accountType" to selectedAccountType,
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        db.collection("users").document(uid).set(userMap)
            .addOnSuccessListener {
                // Log activity for your PHP Admin Panel
                val log = hashMapOf(
                    "userEmail" to email,
                    "action" to "Registration",
                    "details" to "New $selectedAccountType account created",
                    "timestamp" to com.google.firebase.Timestamp.now()
                )
                db.collection("UserActivities").add(log)

                // Redirect
                val intent = if (selectedAccountType == "Business") {
                    Intent(this, BusinessDashboardActivity::class.java)
                } else {
                    Intent(this, HomeActivity::class.java)
                }
                startActivity(intent)
                finish()
            }
    }

    private fun highlightButton(selected: AppCompatButton, unselected: AppCompatButton) {
        selected.setBackgroundResource(R.drawable.button_solid)
        selected.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        unselected.setBackgroundResource(R.drawable.button_outline)
        unselected.setTextColor(ContextCompat.getColor(this, R.color.dark_green))
    }
}