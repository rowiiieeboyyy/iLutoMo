package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.ilutomo.databinding.ActivityBusinessManageBinding

class BusinessManageActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBusinessManageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusinessManageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        binding.businessBottomNav.selectedItemId = R.id.nav_business_manage
        binding.businessBottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_business_dashboard -> {
                    startActivity(Intent(this, BusinessDashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_business_inventory -> {
                    startActivity(Intent(this, BusinessInventoryActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_business_orders -> {
                    startActivity(Intent(this, BusinessOrdersActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_business_manage -> true
                R.id.nav_business_profile -> {
                    startActivity(Intent(this, BusinessProfileActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}