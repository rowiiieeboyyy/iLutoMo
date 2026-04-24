package com.example.ilutomo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.ilutomo.databinding.ActivityManualBinding

class ManualActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManualBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManualBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Close the manual and return to Profile
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}