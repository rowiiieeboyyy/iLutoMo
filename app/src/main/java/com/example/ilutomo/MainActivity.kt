package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnLogin = findViewById<AppCompatButton>(R.id.btnLogin)
        val btnSignUp = findViewById<AppCompatButton>(R.id.btnSignUp)
        val btnForgot = findViewById<AppCompatButton>(R.id.btnForgotPassword)

        btnLogin.setOnClickListener {
            Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)

            // CRITICAL FIX: Close the Login activity so the app doesn't fall back to it
            finish()
        }

        btnSignUp.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        btnForgot.setOnClickListener {
            Toast.makeText(this, "Reset link sent to your email", Toast.LENGTH_LONG).show()
        }
    }
}