package com.example.ilutomo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.ilutomo.databinding.ItemInventoryBinding

class InventoryAdapter(
    private val inventoryList: List<InventoryItem>,
    private val onItemClick: (InventoryItem) -> Unit
) : RecyclerView.Adapter<InventoryAdapter.InventoryViewHolder>() {

    class InventoryViewHolder(val binding: ItemInventoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryViewHolder {
        val binding = ItemInventoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return InventoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InventoryViewHolder, position: Int) {
        val item = inventoryList[position]
        holder.binding.apply {
            // Displays the ingredient type (e.g., "Annatto Powder")
            tvInventoryIngredient.text = item.ingredient

            // Displays the specific product name (e.g., "Annatto Powder (J&Y)")
            // Changed from itemName to name to match Models.kt
            tvInventoryItemName.text = if (item.name.isNotEmpty()) item.name else item.ingredient

            tvInventoryStock.text = "Stock: ${item.stock}"

            // Formats the price to 2 decimal places (e.g., ₱40.00)
            tvInventoryPrice.text = "₱${String.format("%.2f", item.price)}"

            tvInventorySize.text = item.size
            tvInventoryGrade.text = item.itemGrade

            // Image handling - Changed from imageUrl to img to match Models.kt
            if (item.img.isNotEmpty()) {
                Glide.with(ivInventoryImage.context)
                    .load(item.img)
                    .placeholder(R.drawable.placeholder_food)
                    .error(R.drawable.placeholder_food)
                    .centerCrop()
                    .into(ivInventoryImage)
            } else {
                ivInventoryImage.setImageResource(R.drawable.placeholder_food)
            }

            root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun getItemCount(): Int = inventoryList.size
}