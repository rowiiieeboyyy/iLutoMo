package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.ilutomo.databinding.ActivityBusinessOrdersBinding

class BusinessOrdersActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBusinessOrdersBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusinessOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        binding.businessBottomNav.selectedItemId = R.id.nav_business_orders
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
                R.id.nav_business_orders -> true
                R.id.nav_business_manage -> {
                    startActivity(Intent(this, BusinessManageActivity::class.java))
                    finish()
                    true
                }
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