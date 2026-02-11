package com.example.ilutomo

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

class SignUpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val btnCancel = findViewById<AppCompatButton>(R.id.btnCancel)
        val btnSubmitSignUp = findViewById<AppCompatButton>(R.id.btnSubmitSignUp)

        // Goes back to Login screen
        btnCancel.setOnClickListener {
            finish()
        }

        // Placeholder for Sign Up success
        btnSubmitSignUp.setOnClickListener {
            Toast.makeText(this, "Account Created!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}