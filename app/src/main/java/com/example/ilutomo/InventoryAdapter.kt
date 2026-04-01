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
            tvInventoryIngredient.text = item.ingredient
            tvInventoryItemName.text = item.itemName
            tvInventoryStock.text = "Stock: ${item.stock}"
            tvInventoryPrice.text = "₱${String.format("%.2f", item.price)}"
            tvInventorySize.text = item.size

            Glide.with(ivInventoryImage.context)
                .load(item.imageUrl)
                .placeholder(R.drawable.placeholder_food)
                .into(ivInventoryImage)

            root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun getItemCount(): Int = inventoryList.size
}