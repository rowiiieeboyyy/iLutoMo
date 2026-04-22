package com.example.ilutomo

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class RecipeAdapter(
    private var recipes: List<Recipe>,
    private val onAddClick: (Recipe) -> Unit
) : RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder>() {

    class RecipeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.idRecipeImg)
        val title: TextView = view.findViewById(R.id.tvRecipeTitle)
        val category: TextView = view.findViewById(R.id.tvRecipeCategory)
        val macros: TextView = view.findViewById(R.id.tvRecipeMacros)
        val totalTime: TextView = view.findViewById(R.id.tvRecipePrepTime)
        val tags: TextView = view.findViewById(R.id.tvRecipeTags)
        val tvMatchScore: TextView? = view.findViewById(R.id.tvMatchScore)
        val btnAdd: ImageView = view.findViewById(R.id.btnAddRecipe)
        val tvServings: TextView = view.findViewById(R.id.tvHomeServings)
        val btnPlus: ImageButton = view.findViewById(R.id.btnHomePlus)
        val btnMinus: ImageButton = view.findViewById(R.id.btnHomeMinus)
        val tvPrice: TextView? = view.findViewById(R.id.tvRecipePrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recipe_card, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]
        val multiplier = recipe.servings

        holder.title.text = recipe.title.ifEmpty { "Untitled" }
        holder.category.text = recipe.category
        holder.tvServings.text = multiplier.toString()

        holder.totalTime.text = if (recipe.totalTime > 0) "🕒 ${recipe.totalTime} mins" else "🕒 N/A"

        val activeTags = recipe.tasteProfile.filter { it.value }.keys.map {
            it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() }
        }
        holder.tags.text = if (activeTags.isNotEmpty()) "🏷️ ${activeTags.joinToString(", ")}" else "🏷️ No tags"

        holder.tvMatchScore?.let {
            if (recipe.matchScore > 0) {
                it.visibility = View.VISIBLE
                it.text = "${recipe.matchScore.toInt()}% Match"
            } else {
                it.visibility = View.GONE
            }
        }

        // REMOVED TOTAL PRICE DISPLAY on the Home Page card
        holder.tvPrice?.visibility = View.GONE

        // Macros calculation
        val p = (recipe.calculatedMacros["Protein"] ?: 0) * multiplier
        val s = (recipe.calculatedMacros["Sugar"] ?: 0) * multiplier
        val c = (recipe.calculatedMacros["Carbs"] ?: 0) * multiplier
        val cal = (recipe.calculatedMacros["Calories"] ?: 0) * multiplier
        holder.macros.text = "P: ${p}g | S: ${s}g | C: ${c}g | ${cal} kcal"

        // Image loading
        val context = holder.itemView.context
        val resId = context.resources.getIdentifier(recipe.imageResourceName, "drawable", context.packageName)
        holder.img.setImageResource(if (resId != 0) resId else android.R.drawable.ic_menu_gallery)

        // Listeners
        holder.btnPlus.setOnClickListener {
            recipe.servings++
            notifyItemChanged(position)
        }
        holder.btnMinus.setOnClickListener {
            if (recipe.servings > 1) {
                recipe.servings--
                notifyItemChanged(position)
            }
        }
        holder.btnAdd.setOnClickListener { onAddClick(recipe) }
        holder.itemView.setOnClickListener {
            val intent = Intent(context, RecipeDetailsActivity::class.java)
            intent.putExtra("RECIPE", recipe)
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = recipes.size

    fun filterList(filteredList: List<Recipe>) {
        this.recipes = filteredList
        notifyDataSetChanged()
    }
}