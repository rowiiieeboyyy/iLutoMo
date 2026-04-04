package com.example.ilutomo

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.RecyclerView

class RecipeAdapter(
    private val recipes: List<Recipe>,
    private val onAddClick: (Recipe) -> Unit
) : RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder>() {

    class RecipeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.ivRecipeImage)
        val title: TextView = view.findViewById(R.id.tvRecipeTitle)
        val category: TextView = view.findViewById(R.id.tvRecipeCategory)
        val price: TextView = view.findViewById(R.id.tvRecipePrice)
        val macros: TextView = view.findViewById(R.id.tvRecipeMacros)
        val btnAdd: AppCompatButton = view.findViewById(R.id.btnAddRecipe)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recipe, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]

        holder.title.text = recipe.title.ifEmpty { "Untitled Dish" }
        holder.category.text = recipe.category

        // Prices removed from Home main view per previous request, but we keep the field
        holder.price.visibility = View.GONE

        val p = getMacro(recipe.macros, "Protein")
        val s = getMacro(recipe.macros, "Sugar")
        val c = getMacro(recipe.macros, "Carbs")
        holder.macros.text = "P: $p | S: $s | C: $c"

        val context = holder.itemView.context
        val imageResId = context.resources.getIdentifier(
            recipe.imageResourceName, "drawable", context.packageName
        )
        holder.img.setImageResource(if (imageResId != 0) imageResId else android.R.drawable.ic_menu_gallery)

        holder.btnAdd.setOnClickListener { onAddClick(recipe) }
        
        // NEW: Click on item to view details
        holder.itemView.setOnClickListener {
            val intent = Intent(context, RecipeDetailsActivity::class.java)
            intent.putExtra("RECIPE", recipe)
            context.startActivity(intent)
        }
    }

    private fun getMacro(map: Map<String, String>?, key: String): String {
        return map?.entries?.find { it.key.equals(key, ignoreCase = true) }?.value ?: "0g"
    }

    override fun getItemCount() = recipes.size
}