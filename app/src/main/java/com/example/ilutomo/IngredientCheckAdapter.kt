package com.example.ilutomo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class IngredientCheckAdapter(
    private val ingredients: List<DisplayIngredient>,
    private val onCheckChanged: () -> Unit
) : RecyclerView.Adapter<IngredientCheckAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkBox: CheckBox = view.findViewById(R.id.cbIngredient)
        val tvName: TextView = view.findViewById(R.id.tvIngredientName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ingredient_check, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = ingredients[position]

        // 1. CLEAR the listener before setting the state to avoid recycling bugs
        holder.checkBox.setOnCheckedChangeListener(null)

        holder.tvName.text = "${item.name} (${item.amount})"
        holder.checkBox.isChecked = item.isChecked

        // 2. SET the listener to update the actual data object
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            item.isChecked = isChecked
            onCheckChanged() // Refreshes the strikethrough/summary in the Activity
        }
    }

    override fun getItemCount() = ingredients.size
}