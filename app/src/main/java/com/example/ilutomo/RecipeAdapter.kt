package com.example.ilutomo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecipeAdapter(
    private val recipes: List<Recipe>,
    private val onClick: (Recipe) -> Unit
) : RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder>() {

    class RecipeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvRecipeTitle)
        val category: TextView = view.findViewById(R.id.tvRecipeCategory)
        val image: ImageView = view.findViewById(R.id.ivRecipeImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recipe, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]
        holder.title.text = recipe.title
        holder.category.text = recipe.category

        // LOGIC TO LOAD LOCAL IMAGE MANUALLY
        val context = holder.itemView.context
        val imageResId = context.resources.getIdentifier(
            recipe.imageResourceName,
            "drawable",
            context.packageName
        )

        if (imageResId != 0) {
            holder.image.setImageResource(imageResId)
        } else {
            // Fallback to your placeholder if the name doesn't match
            holder.image.setImageResource(R.drawable.placeholder_food)
        }

        holder.itemView.setOnClickListener { onClick(recipe) }
    }

    override fun getItemCount() = recipes.size
}