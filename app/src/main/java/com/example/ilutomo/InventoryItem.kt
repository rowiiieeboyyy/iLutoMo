package com.example.ilutomo

data class InventoryItem(
    val id: String = "",
    val ingredient: String = "",
    val itemName: String = "",
    val stock: Int = 0,
    val size: String = "",
    val price: Double = 0.0,
    val imageUrl: String = "",
    val timestamp: Long = 0
)