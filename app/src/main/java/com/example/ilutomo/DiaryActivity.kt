package com.example.ilutomo

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class DiaryActivity : AppCompatActivity() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var adapter: DiaryHistoryAdapter
    private val entries = mutableListOf<DiaryEntry>()
    
    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    
    private var calorieGoal = 2000
    private var proteinGoal = 0
    private var carbsGoal = 0
    private var fatsGoal = 0

    private var calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("EEEE, MMM dd", Locale.getDefault())

    private val allRecipes = mutableListOf<Recipe>()
    private val ingredientLibrary = mutableMapOf<String, DataSnapshot>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary)

        findViewById<ImageView>(R.id.btnBack)?.setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvEditDiary)?.setOnClickListener {
            startActivity(Intent(this, EditDiaryActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnAddEntry)?.setOnClickListener {
            showAddEntryDialog()
        }
        
        findViewById<ImageView>(R.id.btnHistory)?.setOnClickListener {
            showHistorySummaryDialog()
        }

        setupDateNavigation()
        setupRecyclerView()
        setupBottomNavigation()
        loadRecipesAndLibrary()
    }

    private fun loadRecipesAndLibrary() {
        database.child("ingredient_library").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ingredientLibrary.clear()
                for (child in snapshot.children) {
                    ingredientLibrary[child.key ?: ""] = child
                }
                loadAllRecipes()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadAllRecipes() {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allRecipes.clear()
                for (child in snapshot.children) {
                    if (child.key?.startsWith("recipe_") == true) {
                        val recipe = child.getValue(Recipe::class.java) ?: continue
                        recipe.id = child.key!!
                        calculateRecipeMacros(recipe)
                        allRecipes.add(recipe)
                    }
                }
                val uid = auth.currentUser?.uid ?: return
                database.child("Users").child(uid).child("AddedRecipes").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        for (child in s.children) {
                            val recipe = child.getValue(Recipe::class.java) ?: continue
                            recipe.id = child.key!!
                            calculateRecipeMacros(recipe)
                            if (allRecipes.none { it.title.equals(recipe.title, true) }) {
                                allRecipes.add(recipe)
                            }
                        }
                    }
                    override fun onCancelled(e: DatabaseError) {}
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun calculateRecipeMacros(recipe: Recipe) {
        var pro = 0.0; var carb = 0.0; var sug = 0.0; var cal = 0.0; var fat = 0.0
        recipe.ingredients?.forEach { (name, amt) ->
            val cleanName = name.split("(")[0].trim()
            val lib = ingredientLibrary.entries.find { it.key.equals(cleanName, true) }?.value
            if (lib != null) {
                val qty = PriceCalculator.extractNumericValue(amt.toString())
                val factor = if (cleanName.contains("Egg", true)) qty else (qty / 50.0)
                pro += factor * (lib.child("pro").value?.toString()?.toDoubleOrNull() ?: 0.0)
                carb += factor * (lib.child("carb").value?.toString()?.toDoubleOrNull() ?: 0.0)
                sug += factor * (lib.child("sugar").value?.toString()?.toDoubleOrNull() ?: 0.0)
                cal += factor * (lib.child("cal").value?.toString()?.toDoubleOrNull() ?: 0.0)
                fat += factor * (lib.child("fat").value?.toString()?.toDoubleOrNull() ?: 0.0)
            }
        }
        recipe.calculatedMacros["Protein"] = pro.toInt()
        recipe.calculatedMacros["Carbs"] = carb.toInt()
        recipe.calculatedMacros["Sugar"] = sug.toInt()
        recipe.calculatedMacros["Calories"] = cal.toInt()
        recipe.calculatedMacros["Fats"] = fat.toInt()
    }

    private fun setupDateNavigation() {
        updateDateDisplay()
        findViewById<ImageView>(R.id.btnPrevDay).setOnClickListener { calendar.add(Calendar.DAY_OF_YEAR, -1); updateDateDisplay(); loadHistory() }
        findViewById<ImageView>(R.id.btnNextDay).setOnClickListener { calendar.add(Calendar.DAY_OF_YEAR, 1); updateDateDisplay(); loadHistory() }
    }

    private fun updateDateDisplay() {
        val today = Calendar.getInstance()
        val dateText = if (isSameDay(calendar, today)) "Today - " + displayFormat.format(calendar.time) else displayFormat.format(calendar.time)
        findViewById<TextView>(R.id.tvCurrentDate).text = dateText
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar) = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)

    private fun setupRecyclerView() {
        rvHistory = findViewById(R.id.rvDiaryHistory)
        rvHistory.layoutManager = LinearLayoutManager(this)
        adapter = DiaryHistoryAdapter(entries, onDelete = { deleteEntry(it) }, onItemClick = { showEditEntryDialog(it) })
        rvHistory.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadGoals()
        loadHistory()
    }

    private fun loadGoals() {
        val sharedPref = getSharedPreferences("DiaryPrefs", Context.MODE_PRIVATE)
        calorieGoal = sharedPref.getString("cal", "2000")?.toIntOrNull() ?: 2000
        proteinGoal = sharedPref.getString("pro", "0")?.toIntOrNull() ?: 0
        carbsGoal = sharedPref.getString("carb", "0")?.toIntOrNull() ?: 0
        fatsGoal = sharedPref.getString("fat", "0")?.toIntOrNull() ?: 0
        findViewById<TextView>(R.id.tvGoalValue)?.text = calorieGoal.toString()
    }

    private fun loadHistory() {
        val uid = auth.currentUser?.uid ?: return
        val dateStr = dateFormat.format(calendar.time)
        database.child("Users").child(uid).child("DailyDiary").child(dateStr)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    entries.clear()
                    var totalCal = 0; var totalPro = 0; var totalCarb = 0; var totalFat = 0
                    for (child in snapshot.children) {
                        val entry = child.getValue(DiaryEntry::class.java) ?: continue
                        entry.id = child.key ?: ""
                        entries.add(entry)
                        totalCal += entry.calories; totalPro += entry.protein; totalCarb += entry.carbs; totalFat += entry.fats
                    }
                    entries.sortByDescending { it.timestamp }
                    adapter.notifyDataSetChanged()
                    updateUI(totalCal, totalPro, totalCarb, totalFat)
                    findViewById<TextView>(R.id.tvNoHistory)?.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateUI(totalCal: Int, totalPro: Int, totalCarb: Int, totalFat: Int) {
        val remaining = calorieGoal - totalCal
        val tvRemainingValue = findViewById<TextView>(R.id.tvRemainingValueRaw)
        val tvFoodValue = findViewById<TextView>(R.id.tvFoodValue)
        
        tvRemainingValue?.text = remaining.coerceAtLeast(0).toString()
        tvFoodValue?.text = totalCal.toString()
        
        val colorRemaining = Color.parseColor("#4B8A34") // Green for Left
        val colorLogged = Color.RED // Red for Logged
        val hitBg = Color.parseColor("#F8FDF7")
        val missBg = Color.parseColor("#FFEBEE")
        
        val isCalHit = totalCal <= calorieGoal || calorieGoal == 0
        
        // Calories Remaining (Left) is Green, turns Red if exceeded
        tvRemainingValue?.setTextColor(if (isCalHit) colorRemaining else colorLogged)
        
        // Food (Logged) is strictly Red as requested
        tvFoodValue?.setTextColor(colorLogged)

        // Using base CardView to avoid ClassCastException
        findViewById<CardView>(R.id.cvCalorieSummary)?.setCardBackgroundColor(if (isCalHit) hitBg else missBg)
        
        val tvProtein = findViewById<TextView>(R.id.tvProteinValue)
        val tvCarbs = findViewById<TextView>(R.id.tvCarbsValue)
        val tvFats = findViewById<TextView>(R.id.tvFatsValue)
        
        tvProtein?.text = "${totalPro}/${proteinGoal}g"
        tvCarbs?.text = "${totalCarb}/${carbsGoal}g"
        tvFats?.text = "${totalFat}/${fatsGoal}g"

        // Hit logic from previous requirement: Green if hit, Red if not
        if (proteinGoal > 0) {
            val proHit = totalPro >= proteinGoal
            tvProtein?.setTextColor(if (proHit) colorRemaining else colorLogged)
            findViewById<CardView>(R.id.cvProtein)?.setCardBackgroundColor(if (proHit) Color.parseColor("#E8F5E9") else missBg)
        }
        
        if (carbsGoal > 0) {
            val carbHit = totalCarb <= carbsGoal
            tvCarbs?.setTextColor(if (carbHit) colorRemaining else colorLogged)
            findViewById<CardView>(R.id.cvCarbs)?.setCardBackgroundColor(if (carbHit) Color.parseColor("#E8F5E9") else missBg)
        }
        
        if (fatsGoal > 0) {
            val fatHit = totalFat <= fatsGoal
            tvFats?.setTextColor(if (fatHit) colorRemaining else colorLogged)
            findViewById<CardView>(R.id.cvFats)?.setCardBackgroundColor(if (fatHit) Color.parseColor("#E8F5E9") else missBg)
        }

        val pb = findViewById<ProgressBar>(R.id.pbCalories)
        // Set progress to percentage of consumed calories.
        // XML drawable shows consumed in Red and remaining in Green.
        pb?.progress = if (calorieGoal > 0) ((totalCal.toFloat() / calorieGoal) * 100).toInt().coerceIn(0, 100) else 0
    }

    private fun showHistorySummaryDialog() {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("DailyDiary").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dates = snapshot.children.mapNotNull { it.key }.sortedDescending()
                if (dates.isEmpty()) {
                    Toast.makeText(this@DiaryActivity, "No history found", Toast.LENGTH_SHORT).show()
                    return
                }
                
                val summaries = dates.map { date ->
                    var dCal = 0; var dPro = 0; var dCarb = 0; var dFat = 0
                    snapshot.child(date).children.forEach {
                        dCal += it.child("calories").value?.toString()?.toIntOrNull() ?: 0
                        dPro += it.child("protein").value?.toString()?.toIntOrNull() ?: 0
                        dCarb += it.child("carbs").value?.toString()?.toIntOrNull() ?: 0
                        dFat += it.child("fats").value?.toString()?.toIntOrNull() ?: 0
                    }
                    val status = if (dCal <= calorieGoal) "✓ HIT" else "✗ OVER"
                    "$date | $status\n$dCal kcal | P:$dPro g | C:$dCarb g | F:$dFat g"
                }.toTypedArray()

                AlertDialog.Builder(this@DiaryActivity)
                    .setTitle("Macro History")
                    .setItems(summaries) { _, which ->
                        val parts = dates[which].split("-")
                        calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                        updateDateDisplay()
                        loadHistory()
                    }
                    .setPositiveButton("Close", null)
                    .show()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showAddEntryDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_diary_entry, null)
        val etFood = dialogView.findViewById<AutoCompleteTextView>(R.id.etFoodName)
        val btnBrowse = dialogView.findViewById<ImageButton>(R.id.btnBrowseRecipes)
        val etCal = dialogView.findViewById<EditText>(R.id.etCalories); val etPro = dialogView.findViewById<EditText>(R.id.etProtein); val etCarb = dialogView.findViewById<EditText>(R.id.etCarbs); val etFat = dialogView.findViewById<EditText>(R.id.etFats)
        val tvServings = dialogView.findViewById<TextView>(R.id.tvDialogServings); val btnMinus = dialogView.findViewById<ImageButton>(R.id.btnDialogMinus); val btnPlus = dialogView.findViewById<ImageButton>(R.id.btnDialogPlus)

        var currentS = 1; var bCal = 0; var bPro = 0; var bCarb = 0; var bFat = 0
        val titles = allRecipes.map { it.title }
        etFood.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, titles))
        
        fun onRecipeSelected(recipeTitle: String) {
            etFood.setText(recipeTitle)
            allRecipes.find { it.title.equals(recipeTitle, true) }?.let {
                bCal = it.calculatedMacros["Calories"] ?: 0
                bPro = it.calculatedMacros["Protein"] ?: 0
                bCarb = it.calculatedMacros["Carbs"] ?: 0
                bFat = it.calculatedMacros["Fats"] ?: 0
                updateDialogMacros(etCal, etPro, etCarb, etFat, bCal, bPro, bCarb, bFat, currentS)
            }
        }

        etFood.setOnItemClickListener { _, _, _, _ -> onRecipeSelected(etFood.text.toString()) }
        btnBrowse.setOnClickListener {
            val items = allRecipes.map { it.title }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Select Recipe").setItems(items) { _, which -> onRecipeSelected(items[which]) }.show()
        }

        btnPlus.setOnClickListener { currentS++; tvServings.text = currentS.toString(); updateDialogMacros(etCal, etPro, etCarb, etFat, bCal, bPro, bCarb, bFat, currentS) }
        btnMinus.setOnClickListener { if (currentS > 1) { currentS--; tvServings.text = currentS.toString(); updateDialogMacros(etCal, etPro, etCarb, etFat, bCal, bPro, bCarb, bFat, currentS) } }

        AlertDialog.Builder(this).setTitle("Log Food").setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val food = etFood.text.toString().trim()
                if (food.isNotEmpty()) saveEntry(food, etCal.text.toString().toIntOrNull() ?: 0, etPro.text.toString().toIntOrNull() ?: 0, etCarb.text.toString().toIntOrNull() ?: 0, etFat.text.toString().toIntOrNull() ?: 0, currentS)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showEditEntryDialog(entry: DiaryEntry) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_diary_entry, null)
        val etFood = dialogView.findViewById<AutoCompleteTextView>(R.id.etFoodName)
        val btnBrowse = dialogView.findViewById<ImageButton>(R.id.btnBrowseRecipes)
        val etCal = dialogView.findViewById<EditText>(R.id.etCalories); val etPro = dialogView.findViewById<EditText>(R.id.etProtein); val etCarb = dialogView.findViewById<EditText>(R.id.etCarbs); val etFat = dialogView.findViewById<EditText>(R.id.etFats)
        val tvServings = dialogView.findViewById<TextView>(R.id.tvDialogServings); val btnMinus = dialogView.findViewById<ImageButton>(R.id.btnDialogMinus); val btnPlus = dialogView.findViewById<ImageButton>(R.id.btnDialogPlus)

        var curS = entry.servings.coerceAtLeast(1)
        var bCal = entry.calories / curS; var bPro = entry.protein / curS; var bCarb = entry.carbs / curS; var bFat = entry.fats / curS
        
        etFood.setText(entry.foodName); tvServings.text = curS.toString()
        updateDialogMacros(etCal, etPro, etCarb, etFat, bCal, bPro, bCarb, bFat, curS)

        fun onRecipeSelected(recipeTitle: String) {
            etFood.setText(recipeTitle)
            allRecipes.find { it.title.equals(recipeTitle, true) }?.let {
                bCal = it.calculatedMacros["Calories"] ?: 0
                bPro = it.calculatedMacros["Protein"] ?: 0
                bCarb = it.calculatedMacros["Carbs"] ?: 0
                bFat = it.calculatedMacros["Fats"] ?: 0
                updateDialogMacros(etCal, etPro, etCarb, etFat, bCal, bPro, bCarb, bFat, curS)
            }
        }

        etFood.setOnItemClickListener { _, _, _, _ -> onRecipeSelected(etFood.text.toString()) }
        btnBrowse.setOnClickListener {
            val items = allRecipes.map { it.title }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Select Recipe").setItems(items) { _, which -> onRecipeSelected(items[which]) }.show()
        }

        btnPlus.setOnClickListener { curS++; tvServings.text = curS.toString(); updateDialogMacros(etCal, etPro, etCarb, etFat, bCal, bPro, bCarb, bFat, curS) }
        btnMinus.setOnClickListener { if (curS > 1) { curS--; tvServings.text = curS.toString(); updateDialogMacros(etCal, etPro, etCarb, etFat, bCal, bPro, bCarb, bFat, curS) } }

        AlertDialog.Builder(this).setTitle("Edit Entry").setView(dialogView)
            .setPositiveButton("Update") { _, _ -> updateEntry(entry.id, etFood.text.toString().trim(), etCal.text.toString().toIntOrNull() ?: 0, etPro.text.toString().toIntOrNull() ?: 0, etCarb.text.toString().toIntOrNull() ?: 0, etFat.text.toString().toIntOrNull() ?: 0, curS) }
            .setNeutralButton("Delete") { _, _ -> deleteEntry(entry) }.setNegativeButton("Cancel", null).show()
    }

    private fun updateDialogMacros(etCal: EditText, etPro: EditText, etCarb: EditText, etFat: EditText, bCal: Int, bPro: Int, bCarb: Int, bFat: Int, s: Int) {
        etCal.setText((bCal * s).toString()); etPro.setText((bPro * s).toString()); etCarb.setText((bCarb * s).toString()); etFat.setText((bFat * s).toString())
    }

    private fun saveEntry(name: String, cal: Int, pro: Int, carb: Int, fat: Int, s: Int) {
        val uid = auth.currentUser?.uid ?: return
        val dateStr = dateFormat.format(calendar.time)
        val entry = DiaryEntry(foodName = name, calories = cal, protein = pro, carbs = carb, fats = fat, servings = s, timestamp = System.currentTimeMillis())
        database.child("Users").child(uid).child("DailyDiary").child(dateStr).push().setValue(entry)
    }

    private fun updateEntry(id: String, name: String, cal: Int, pro: Int, carb: Int, fat: Int, s: Int) {
        val uid = auth.currentUser?.uid ?: return
        val dateStr = dateFormat.format(calendar.time)
        val updates = mapOf("foodName" to name, "calories" to cal, "protein" to pro, "carbs" to carb, "fats" to fat, "servings" to s)
        database.child("Users").child(uid).child("DailyDiary").child(dateStr).child(id).updateChildren(updates)
    }

    private fun deleteEntry(entry: DiaryEntry) {
        val uid = auth.currentUser?.uid ?: return
        val dateStr = dateFormat.format(calendar.time)
        database.child("Users").child(uid).child("DailyDiary").child(dateStr).child(entry.id).removeValue()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav?.selectedItemId = R.id.nav_profile
        bottomNav?.setOnItemSelectedListener { item ->
            val intent = when (item.itemId) {
                R.id.nav_home -> Intent(this, HomeActivity::class.java)
                R.id.nav_recipes -> Intent(this, RecipesActivity::class.java)
                R.id.nav_pantry -> Intent(this, PantryActivity::class.java)
                R.id.nav_profile -> Intent(this, ProfileActivity::class.java)
                else -> null
            }
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); startActivity(intent)
                if (item.itemId != R.id.nav_profile) finish()
            }
            true
        }
    }

    class DiaryHistoryAdapter(private val list: List<DiaryEntry>, private val onDelete: (DiaryEntry) -> Unit, private val onItemClick: (DiaryEntry) -> Unit) : RecyclerView.Adapter<DiaryHistoryAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvEntryFoodName)
            val tvServings: TextView = view.findViewById(R.id.tvEntryServings)
            val tvMacros: TextView = view.findViewById(R.id.tvEntryMacros)
            val tvCal: TextView = view.findViewById(R.id.tvEntryCalories)
            val btnDelete: ImageView = view.findViewById(R.id.btnDeleteEntry)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_diary_entry, parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = list[position]
            holder.tvName.text = entry.foodName
            holder.tvServings.text = "(x${entry.servings})"
            holder.tvCal.text = "${entry.calories} kcal"
            holder.tvMacros.text = "P: ${entry.protein}g • C: ${entry.carbs}g • F: ${entry.fats}g"
            holder.btnDelete.setOnClickListener { onDelete(entry) }
            holder.itemView.setOnClickListener { onItemClick(entry) }
        }
        override fun getItemCount() = list.size
    }
}