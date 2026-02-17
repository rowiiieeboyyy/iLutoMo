package com.example.ilutomo

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class EditDiaryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_diary)

        // 1. References
        val etCalories = findViewById<EditText>(R.id.etCalories)
        val etProtein = findViewById<EditText>(R.id.etProtein)
        val etCarbs = findViewById<EditText>(R.id.etCarbs)
        val etFats = findViewById<EditText>(R.id.etFats)
        val etSodium = findViewById<EditText>(R.id.etSodium)
        val etSugar = findViewById<EditText>(R.id.etSugar)

        val tvPreviewProtein = findViewById<TextView>(R.id.tvPreviewProtein)
        val tvPreviewCarbs = findViewById<TextView>(R.id.tvPreviewCarbs)
        val tvPreviewFats = findViewById<TextView>(R.id.tvPreviewFats)
        val tvPreviewSodium = findViewById<TextView>(R.id.tvPreviewSodium)

        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnReset = findViewById<Button>(R.id.btnReset)
        val btnBack = findViewById<ImageView>(R.id.btnBack)

        // 2. Real-time updates for breakdown cards
        etProtein.addTextChangedListener(createWatcher(tvPreviewProtein, "g"))
        etCarbs.addTextChangedListener(createWatcher(tvPreviewCarbs, "g"))
        etFats.addTextChangedListener(createWatcher(tvPreviewFats, "g"))
        etSodium.addTextChangedListener(createWatcher(tvPreviewSodium, "mg"))

        // 3. Button Actions
        btnBack.setOnClickListener { finish() }

        btnSave.setOnClickListener {
            val sharedPref = getSharedPreferences("DiaryPrefs", Context.MODE_PRIVATE)
            val editor = sharedPref.edit()
            editor.putString("cal", etCalories.text.toString().ifEmpty { "0" })
            editor.putString("pro", etProtein.text.toString().ifEmpty { "0" })
            editor.putString("carb", etCarbs.text.toString().ifEmpty { "0" })
            editor.putString("fat", etFats.text.toString().ifEmpty { "0" })
            editor.putString("sod", etSodium.text.toString().ifEmpty { "0" })
            editor.putString("sug", etSugar.text.toString().ifEmpty { "0" })
            editor.apply()
            Toast.makeText(this, "Diary Goals Updated", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnReset.setOnClickListener {
            listOf(etCalories, etProtein, etCarbs, etFats, etSodium, etSugar).forEach { it.setText("") }
        }
    }

    // Helper to keep code clean
    private fun createWatcher(target: TextView, unit: String) = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) { target.text = if (s.isNullOrEmpty()) "0$unit" else "${s}$unit" }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }
}