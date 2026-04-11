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
        holder.price.visibility = View.GONE

        // UPDATED: Pulling from calculatedMacros map created in HomeActivity
        val p = recipe.calculatedMacros["Protein"] ?: 0
        val s = recipe.calculatedMacros["Sugar"] ?: 0
        val c = recipe.calculatedMacros["Carbs"] ?: 0

        // Displaying the calculated numbers
        holder.macros.text = "P: ${p}g | S: ${s}g | C: ${c}g"

        val context = holder.itemView.context
        val imageResId = context.resources.getIdentifier(
            recipe.imageResourceName, "drawable", context.packageName
        )
        holder.img.setImageResource(if (imageResId != 0) imageResId else android.R.drawable.ic_menu_gallery)

        holder.btnAdd.setOnClickListener {
            onAddClick(recipe)
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(context, RecipeDetailsActivity::class.java)
            // Passing the recipe object which now contains the calculatedMacros
            intent.putExtra("RECIPE", recipe)
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = recipes.size
}