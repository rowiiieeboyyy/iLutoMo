package com.example.ilutomo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecipeAdapter(
    private val recipes: List<Recipe>,
    private val onAddClick: (Recipe) -> Unit
) : RecyclerView.Adapter<RecipeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgRecipe: ImageView = view.findViewById(R.id.idRecipeImg)
        val tvTitle: TextView = view.findViewById(R.id.tvRecipeTitle)
        val tvCategory: TextView = view.findViewById(R.id.tvRecipeCategory)
        val btnAdd: ImageView = view.findViewById(R.id.btnAddRecipe)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recipe_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recipe = recipes[position]
        holder.tvTitle.text = recipe.title
        holder.tvCategory.text = recipe.category

        val context = holder.itemView.context
        val imageId = context.resources.getIdentifier(recipe.imageResourceName, "drawable", context.packageName)

        if (imageId != 0) {
            holder.imgRecipe.setImageResource(imageId)
        } else {
            holder.imgRecipe.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        holder.btnAdd.setOnClickListener { onAddClick(recipe) }
    }

    override fun getItemCount() = recipes.size
}