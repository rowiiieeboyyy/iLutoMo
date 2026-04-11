package com.example.ilutomo

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecipeAdapter(
    private var recipes: List<Recipe>,
    private val onAddClick: (Recipe) -> Unit
) : RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder>() {

    class RecipeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.idRecipeImg)
        val title: TextView = view.findViewById(R.id.tvRecipeTitle)
        val category: TextView = view.findViewById(R.id.tvRecipeCategory)
        val macros: TextView = view.findViewById(R.id.tvRecipeMacros)
        val btnAdd: ImageView = view.findViewById(R.id.btnAddRecipe)

        // Portion UI IDs from item_recipe_card.xml
        val tvServings: TextView = view.findViewById(R.id.tvHomeServings)
        val btnPlus: ImageButton = view.findViewById(R.id.btnHomePlus)
        val btnMinus: ImageButton = view.findViewById(R.id.btnHomeMinus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        // Inflating the card layout we updated earlier
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recipe_card, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]

        holder.title.text = recipe.title.ifEmpty { "Untitled Dish" }
        holder.category.text = recipe.category

        // Update the servings counter display
        holder.tvServings.text = recipe.servings.toString()

        // MULTIPLY logic: Base macros * the servings multiplier
        val multiplier = recipe.servings
        val p = (recipe.calculatedMacros["Protein"] ?: 0) * multiplier
        val s = (recipe.calculatedMacros["Sugar"] ?: 0) * multiplier
        val c = (recipe.calculatedMacros["Carbs"] ?: 0) * multiplier
        val cal = (recipe.calculatedMacros["Calories"] ?: 0) * multiplier

        // Update the macro text display in real-time
        holder.macros.text = "P: ${p}g | S: ${s}g | C: ${c}g | ${cal} kcal"

        val context = holder.itemView.context
        val imageResId = context.resources.getIdentifier(
            recipe.imageResourceName, "drawable", context.packageName
        )
        holder.img.setImageResource(if (imageResId != 0) imageResId else android.R.drawable.ic_menu_gallery)

        // --- PORTION CONTROL LISTENERS ---
        holder.btnPlus.setOnClickListener {
            recipe.servings++
            // notifyItemChanged updates only the numbers on this specific card
            notifyItemChanged(position)
        }

        holder.btnMinus.setOnClickListener {
            if (recipe.servings > 1) {
                recipe.servings--
                notifyItemChanged(position)
            }
        }

        holder.btnAdd.setOnClickListener {
            onAddClick(recipe)
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(context, RecipeDetailsActivity::class.java)
            // Passing the recipe object (which now carries the updated servings)
            intent.putExtra("RECIPE", recipe)
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = recipes.size

    /**
     * Call this from your RecipesActivity when filtering/searching
     * to refresh the list safely.
     */
    fun filterList(filteredList: List<Recipe>) {
        this.recipes = filteredList
        notifyDataSetChanged()
    }
}