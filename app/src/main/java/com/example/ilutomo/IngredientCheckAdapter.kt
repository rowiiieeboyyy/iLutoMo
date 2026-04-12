package com.example.ilutomo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class IngredientCheckAdapter(
    private val ingredients: List<DisplayIngredient>,
    private val multiplier: Int, // Added multiplier parameter
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

        // 1. CLEAR the listener before setting state to avoid recycling bugs
        holder.checkBox.setOnCheckedChangeListener(null)

        // 2. SCALE the amount for display based on current servings
        val scaledAmount = scaleAmount(item.amount, multiplier)
        holder.tvName.text = "${item.name} ($scaledAmount)"
        holder.checkBox.isChecked = item.isChecked

        // 3. SET the listener to update the data object
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            item.isChecked = isChecked
            onCheckChanged() // Notifies RecipesActivity to update the Needed List & Total Price
        }
    }

    override fun getItemCount() = ingredients.size

    /**
     * Helper function to multiply the numeric part of the amount string.
     * E.g., "100g" with multiplier 2 becomes "200g"
     */
    private fun scaleAmount(amount: String, multiplier: Int): String {
        val numberRegex = "([0-9]*\\.?[0-9]+)".toRegex()
        val match = numberRegex.find(amount)
        return if (match != null) {
            val scaledValue = match.value.toDouble() * multiplier
            val formatted = if (scaledValue % 1 == 0.0) {
                scaledValue.toInt().toString()
            } else {
                "%.1f".format(scaledValue)
            }
            amount.replaceFirst(match.value, formatted)
        } else {
            amount
        }
    }
}