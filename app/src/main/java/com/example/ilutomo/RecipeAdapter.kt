package com.example.ilutomo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecipeAdapter(
    private val recipeList: List<Recipe>,
    private val onAddClick: (Recipe) -> Unit,
    private val onItemClick: (Recipe) -> Unit
) : RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder>() {

    class RecipeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvRecipeTitle)
        val image: ImageView = view.findViewById(R.id.ivRecipeImage)
        val category: TextView = view.findViewById(R.id.tvRecipeDescription)
        val btnAdd: Button = view.findViewById(R.id.btnAddRecipe)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recipe, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipeList[position]
        holder.title.text = recipe.title
        holder.category.text = recipe.category

        val context = holder.itemView.context
        val imageResId = context.resources.getIdentifier(recipe.imageResourceName, "drawable", context.packageName)

        if (imageResId != 0) {
            holder.image.setImageResource(imageResId)
        } else {
            // Default image if drawable name doesn't match
            holder.image.setImageResource(android.R.drawable.ic_menu_report_image)
        }

        holder.btnAdd.setOnClickListener { onAddClick(recipe) }
        holder.itemView.setOnClickListener { onItemClick(recipe) }
    }

    override fun getItemCount() = recipeList.size
}