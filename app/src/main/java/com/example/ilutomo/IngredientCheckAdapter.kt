package com.example.ilutomo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class IngredientCheckAdapter(
    private val ingredients: List<DisplayIngredient>,
    private val onIngredientToggled: () -> Unit
) : RecyclerView.Adapter<IngredientCheckAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cbIngredient: CheckBox = view.findViewById(R.id.cbIngredient)
        val tvIngredientName: TextView = view.findViewById(R.id.tvIngredientName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ingredient_check, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ingredient = ingredients[position]
        holder.tvIngredientName.text = "${ingredient.name} (${ingredient.amount})"

        // Remove listener before setting state to prevent accidental triggers
        holder.cbIngredient.setOnCheckedChangeListener(null)
        holder.cbIngredient.isChecked = ingredient.isChecked

        holder.cbIngredient.setOnCheckedChangeListener { _, isChecked ->
            ingredient.isChecked = isChecked
            onIngredientToggled() // This triggers renderNeededList in RecipesActivity
        }
    }

    override fun getItemCount() = ingredients.size
}