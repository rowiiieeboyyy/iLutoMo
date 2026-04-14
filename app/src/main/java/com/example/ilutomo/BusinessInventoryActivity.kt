package com.example.ilutomo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.example.ilutomo.databinding.ActivityBusinessInventoryBinding
import com.example.ilutomo.databinding.DialogAddInventoryItemBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class BusinessInventoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBusinessInventoryBinding
    private val database = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private val ingredientList = mutableListOf<String>()
    private val inventoryItems = mutableListOf<InventoryItem>()
    private lateinit var adapter: InventoryAdapter
    private var selectedImageUri: Uri? = null
    private var dialogItemImageView: android.widget.ImageView? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            dialogItemImageView?.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusinessInventoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadIngredients()
        loadInventory()
        setupBottomNavigation()

        binding.fabAddItem.setOnClickListener { showAddItemDialog() }
    }

    private fun setupRecyclerView() {
        adapter = InventoryAdapter(inventoryItems) { item -> showEditDeleteDialog(item) }
        binding.rvInventory.layoutManager = GridLayoutManager(this, 2)
        binding.rvInventory.adapter = adapter
    }

    private fun showEditDeleteDialog(item: InventoryItem) {
        val options = arrayOf("Edit", "Delete")
        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditItemDialog(item)
                    1 -> confirmDelete(item)
                }
            }
            .show()
    }

    private fun confirmDelete(item: InventoryItem) {
        val uid = auth.currentUser?.uid ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete ${item.name}?")
            .setPositiveButton("Delete") { _, _ ->
                // Uses unique ID key instead of name key
                database.child("Businesses").child(uid).child("inventory").child(item.id).removeValue()
                    .addOnSuccessListener { Toast.makeText(this, "Item deleted", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditItemDialog(item: InventoryItem) {
        val dialogBinding = DialogAddInventoryItemBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()

        dialogBinding.tvDialogTitle.text = "Edit Inventory Item"

        val ingredientAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ingredientList)
        dialogBinding.actvIngredient.setAdapter(ingredientAdapter)

        // FIX: Ensure text is visible and formatted correctly
        dialogBinding.actvIngredient.setText(item.ingredient, false)
        dialogBinding.etItemName.setText(item.name)
        dialogBinding.etStock.setText(item.stock.toString())
        dialogBinding.etPrice.setText(item.price.toString())

        val grades = arrayOf("Budget", "Premium")
        dialogBinding.spinnerGrade.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, grades)
        dialogBinding.spinnerGrade.setSelection(grades.indexOf(item.itemGrade))

        val sizeParts = item.size.split(" ")
        if (sizeParts.size >= 1) {
            dialogBinding.etSizeValue.setText(sizeParts[0])
            if (sizeParts.size == 2) {
                val units = resources.getStringArray(R.array.size_units)
                val spinnerPosition = units.indexOf(sizeParts[1])
                if (spinnerPosition >= 0) dialogBinding.spinnerUnit.setSelection(spinnerPosition)
            }
        }

        dialogItemImageView = dialogBinding.ivItemImage
        if (item.img.isNotEmpty()) {
            Glide.with(this).load(item.img).placeholder(R.drawable.placeholder_food).into(dialogBinding.ivItemImage)
        }

        dialogBinding.btnAddPhoto.setOnClickListener { imagePickerLauncher.launch("image/*") }
        dialogBinding.ivCloseDialog.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnFinish.setOnClickListener {
            val ing = dialogBinding.actvIngredient.text.toString().trim()
            val grade = dialogBinding.spinnerGrade.selectedItem.toString()
            val nameText = dialogBinding.etItemName.text.toString().trim()
            val stockNum = dialogBinding.etStock.text.toString().toIntOrNull() ?: 0
            val priceNum = dialogBinding.etPrice.text.toString().toDoubleOrNull() ?: 0.0
            val sizeText = "${dialogBinding.etSizeValue.text} ${dialogBinding.spinnerUnit.selectedItem}"

            if (selectedImageUri != null) {
                uploadImageAndSave(ing, grade, nameText, stockNum, sizeText, priceNum, dialog, item.id)
            } else {
                updateDatabase(item.id, ing, grade, nameText, stockNum, sizeText, priceNum, item.img, dialog)
            }
        }
        dialog.show()
    }

    private fun showAddItemDialog() {
        val dialogBinding = DialogAddInventoryItemBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()

        dialogItemImageView = dialogBinding.ivItemImage
        selectedImageUri = null
        val grades = arrayOf("Budget", "Premium")
        dialogBinding.spinnerGrade.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, grades)

        dialogBinding.btnAddPhoto.setOnClickListener { imagePickerLauncher.launch("image/*") }
        dialogBinding.ivCloseDialog.setOnClickListener { dialog.dismiss() }
        dialogBinding.actvIngredient.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ingredientList))

        dialogBinding.btnFinish.setOnClickListener {
            val ing = dialogBinding.actvIngredient.text.toString().trim()
            val grade = dialogBinding.spinnerGrade.selectedItem.toString()
            val nameText = dialogBinding.etItemName.text.toString().trim()
            val stockStr = dialogBinding.etStock.text.toString().trim()
            val priceStr = dialogBinding.etPrice.text.toString().trim()

            if (ing.isEmpty() || nameText.isEmpty() || stockStr.isEmpty() || priceStr.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sizeText = "${dialogBinding.etSizeValue.text} ${dialogBinding.spinnerUnit.selectedItem}"

            if (selectedImageUri != null) {
                uploadImageAndSave(ing, grade, nameText, stockStr.toInt(), sizeText, priceStr.toDouble(), dialog)
            } else {
                saveToDatabase(ing, grade, nameText, stockStr.toInt(), sizeText, priceStr.toDouble(), "", dialog)
            }
        }
        dialog.show()
    }

    private fun uploadImageAndSave(ing: String, grade: String, name: String, stock: Int, size: String, price: Double, dialog: AlertDialog, oldId: String? = null) {
        val ref = storage.child("inventory_images/${UUID.randomUUID()}")
        selectedImageUri?.let { uri ->
            ref.putFile(uri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUrl ->
                    if (oldId != null) {
                        updateDatabase(oldId, ing, grade, name, stock, size, price, downloadUrl.toString(), dialog)
                    } else {
                        saveToDatabase(ing, grade, name, stock, size, price, downloadUrl.toString(), dialog)
                    }
                }
            }
        }
    }

    private fun saveToDatabase(ing: String, grade: String, name: String, stock: Int, size: String, price: Double, url: String, dialog: AlertDialog) {
        val uid = auth.currentUser?.uid ?: return
        val tag = ing.lowercase().replace(" ", "_")

        // Push creates the unique ID automatically
        val itemKey = database.child("Businesses").child(uid).child("inventory").push().key ?: UUID.randomUUID().toString()
        val item = InventoryItem(itemKey, ing, tag, grade, name, stock, size, price, url, System.currentTimeMillis())

        database.child("Businesses").child(uid).child("inventory").child(itemKey).setValue(item)
            .addOnSuccessListener {
                Toast.makeText(this, "Item added!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
    }

    private fun updateDatabase(id: String, ing: String, grade: String, newName: String, stock: Int, size: String, price: Double, url: String, dialog: AlertDialog) {
        val uid = auth.currentUser?.uid ?: return
        val tag = ing.lowercase().replace(" ", "_")
        val item = InventoryItem(id, ing, tag, grade, newName, stock, size, price, url, System.currentTimeMillis())

        database.child("Businesses").child(uid).child("inventory").child(id).setValue(item)
            .addOnSuccessListener {
                Toast.makeText(this, "Item updated!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
    }

    private fun loadInventory() {
        val uid = auth.currentUser?.uid ?: return
        database.child("Businesses").child(uid).child("inventory").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                inventoryItems.clear()
                for (child in snapshot.children) {
                    child.getValue(InventoryItem::class.java)?.let { inventoryItems.add(it) }
                }
                inventoryItems.sortByDescending { it.timestamp }
                adapter.notifyDataSetChanged()
                binding.tvEmptyInventory.visibility = if (inventoryItems.isEmpty()) View.VISIBLE else View.GONE
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadIngredients() {
        database.child("ingredient_library").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ingredientList.clear()
                for (child in snapshot.children) child.key?.let { ingredientList.add(it) }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupBottomNavigation() {
        binding.businessBottomNav.selectedItemId = R.id.nav_business_inventory
        binding.businessBottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_business_dashboard -> { startActivity(Intent(this, BusinessDashboardActivity::class.java)); true }
                R.id.nav_business_inventory -> true
                else -> false
            }
        }
    }
}