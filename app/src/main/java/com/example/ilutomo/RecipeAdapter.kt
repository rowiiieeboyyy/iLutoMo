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
) : RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder>() {

    class RecipeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.idRecipeImg)
        val title: TextView = view.findViewById(R.id.tvRecipeTitle)
        val category: TextView = view.findViewById(R.id.tvRecipeCategory)
        val btnAdd: ImageView = view.findViewById(R.id.btnAddRecipe)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        // Ensure this layout filename is correct (e.g., item_recipe_card.xml)
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recipe_card, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]

        holder.title.text = if (recipe.title.isNotEmpty()) recipe.title else "Untitled Dish"
        holder.category.text = recipe.category

        // Dynamic image loading from drawable
        val context = holder.itemView.context
        val imageResId = context.resources.getIdentifier(
            recipe.imageResourceName, "drawable", context.packageName
        )

        if (imageResId != 0) {
            holder.img.setImageResource(imageResId)
        } else {
            holder.img.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // Listener for the green '+' button in your XML
        holder.btnAdd.setOnClickListener {
            onAddClick(recipe)
        }
    }

    override fun getItemCount() = recipes.size
}