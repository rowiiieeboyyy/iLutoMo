package com.example.ilutomo

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView

class IngredientCheckAdapter(
    private val items: List<DisplayIngredient>,
    private val onStatusChanged: () -> Unit
) : RecyclerView.Adapter<IngredientCheckAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cbIngredient: CheckBox = view.findViewById(R.id.cbIngredient)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ingredient_check, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.cbIngredient.setOnCheckedChangeListener(null)
        holder.cbIngredient.text = item.name
        holder.cbIngredient.isChecked = item.isChecked

        // Dim the checkbox text if checked
        if (item.isChecked) {
            holder.cbIngredient.setTextColor(Color.GRAY)
        } else {
            holder.cbIngredient.setTextColor(Color.BLACK)
        }

        holder.cbIngredient.setOnCheckedChangeListener { _, isChecked ->
            item.isChecked = isChecked
            onStatusChanged()
        }
    }

    override fun getItemCount() = items.size
}