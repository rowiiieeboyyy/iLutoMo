package com.example.ilutomo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class BusinessInventoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBusinessInventoryBinding
    private val database = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val ingredientList = mutableListOf<String>()
    private val inventoryItems = mutableListOf<InventoryItem>()
    private lateinit var adapter: InventoryAdapter
    private var selectedImageUri: Uri? = null
    private var dialogItemImageView: android.widget.ImageView? = null
    
    private var businessName: String? = null

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
        
        // START LOADING IMMEDIATELY using UID
        loadInventory()
        fetchBusinessInfo()
        
        setupBottomNavigation()

        binding.fabAddItem.setOnClickListener {
            if (businessName.isNullOrEmpty()) {
                Toast.makeText(this, "Business Name not found. Please update Profile.", Toast.LENGTH_LONG).show()
            } else {
                showAddItemDialog()
            }
        }
    }

    private fun fetchBusinessInfo() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                businessName = document.getString("businessName")?.trim()
                if (businessName.isNullOrEmpty()) {
                    // Fallback to RTDB if Firestore is empty
                    database.child("Businesses").child(uid).child("details").child("businessName")
                        .get().addOnSuccessListener { snapshot ->
                            businessName = snapshot.value?.toString()?.trim()
                        }
                }
            }
    }

    private fun setupRecyclerView() {
        adapter = InventoryAdapter(inventoryItems) { item ->
            showEditDeleteDialog(item)
        }
        binding.rvInventory.layoutManager = GridLayoutManager(this, 2)
        binding.rvInventory.adapter = adapter
    }

    private fun showEditDeleteDialog(item: InventoryItem) {
        val options = arrayOf("Edit", "Delete")
        AlertDialog.Builder(this)
            .setTitle(item.ingredient)
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
                database.child("Businesses").child(uid).child("inventory").child(item.name).removeValue()
                    .addOnSuccessListener { Toast.makeText(this, "Item deleted", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditItemDialog(item: InventoryItem) {
        val dialogBinding = DialogAddInventoryItemBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tvDialogTitle.text = "Edit Inventory Item"
        dialogBinding.actvIngredient.setText(item.ingredient)
        dialogBinding.etItemName.setText(item.name)
        dialogBinding.etStock.setText(item.stock.toString())
        dialogBinding.etPrice.setText(item.price.toString())

        val sizeParts = item.size.split(" ")
        if (sizeParts.size == 2) {
            dialogBinding.etSizeValue.setText(sizeParts[0])
            val unit = sizeParts[1]
            val units = resources.getStringArray(R.array.size_units)
            val spinnerPosition = units.indexOf(unit)
            if (spinnerPosition >= 0) {
                dialogBinding.spinnerUnit.setSelection(spinnerPosition)
            }
        }

        dialogItemImageView = dialogBinding.ivItemImage
        selectedImageUri = null

        if (item.img.isNotEmpty()) {
            Glide.with(this).load(item.img).placeholder(R.drawable.placeholder_food).into(dialogBinding.ivItemImage)
        }

        dialogBinding.btnAddPhoto.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        dialogBinding.ivCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        val autocompleteAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ingredientList)
        dialogBinding.actvIngredient.setAdapter(autocompleteAdapter)
        dialogBinding.actvIngredient.setOnClickListener { dialogBinding.actvIngredient.showDropDown() }

        dialogBinding.btnFinish.setOnClickListener {
            val ingredient = dialogBinding.actvIngredient.text.toString().trim()
            
            if (!ingredientList.contains(ingredient)) {
                Toast.makeText(this, "Please select an ingredient from the list", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val itemName = dialogBinding.etItemName.text.toString().trim()
            val stockStr = dialogBinding.etStock.text.toString().trim()
            val sizeValueStr = dialogBinding.etSizeValue.text.toString().trim()
            val unit = dialogBinding.spinnerUnit.selectedItem.toString()
            val priceStr = dialogBinding.etPrice.text.toString().trim()

            if (ingredient.isEmpty() || itemName.isEmpty() || stockStr.isEmpty() || sizeValueStr.isEmpty() || priceStr.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val stock = stockStr.toIntOrNull() ?: 0
            val sizeValue = sizeValueStr.toDoubleOrNull() ?: 0.0
            val price = priceStr.toDoubleOrNull() ?: 0.0
            val size = "$sizeValue $unit"

            if (selectedImageUri != null) {
                uploadImageAndSave(ingredient, itemName, stock, size, price, dialog, item.name)
            } else {
                updateDatabase(item.name, ingredient, itemName, stock, size, price, item.img, dialog)
            }
        }

        dialog.show()
    }

    private fun updateDatabase(oldItemName: String, ingredient: String, newItemName: String, stock: Int, size: String, price: Double, imageUrl: String, dialog: AlertDialog) {
        val uid = auth.currentUser?.uid ?: return
        val updatedItem = mapOf(
            "ingredient" to ingredient,
            "name" to newItemName,
            "stock" to stock,
            "size" to size,
            "price" to price,
            "img" to imageUrl,
            "timestamp" to System.currentTimeMillis()
        )

        if (oldItemName != newItemName) {
            database.child("Businesses").child(uid).child("inventory").child(oldItemName).removeValue()
        }

        database.child("Businesses").child(uid).child("inventory").child(newItemName).updateChildren(updatedItem)
            .addOnSuccessListener {
                Toast.makeText(this, "Item updated!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
    }

    private fun loadInventory() {
        val uid = auth.currentUser?.uid ?: return
        // Screenshot confirms path: Businesses/[UID]/inventory
        database.child("Businesses").child(uid).child("inventory").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                inventoryItems.clear()
                for (child in snapshot.children) {
                    val item = child.getValue(InventoryItem::class.java)
                    if (item != null) {
                        inventoryItems.add(item.copy(id = child.key ?: ""))
                    }
                }
                inventoryItems.sortByDescending { it.timestamp }
                adapter.notifyDataSetChanged()
                
                if (inventoryItems.isEmpty()) {
                    binding.tvEmptyInventory.text = "Inventory is empty"
                    binding.tvEmptyInventory.visibility = View.VISIBLE
                } else {
                    binding.tvEmptyInventory.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@BusinessInventoryActivity, "Failed to load inventory", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadIngredients() {
        database.child("ingredient_library").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ingredientList.clear()
                for (child in snapshot.children) {
                    child.key?.let { ingredientList.add(it) }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showAddItemDialog() {
        val dialogBinding = DialogAddInventoryItemBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogItemImageView = dialogBinding.ivItemImage
        selectedImageUri = null

        dialogBinding.ivCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnAddPhoto.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        val autocompleteAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ingredientList)
        dialogBinding.actvIngredient.setAdapter(autocompleteAdapter)
        dialogBinding.actvIngredient.setOnClickListener { dialogBinding.actvIngredient.showDropDown() }

        dialogBinding.btnFinish.setOnClickListener {
            val ingredient = dialogBinding.actvIngredient.text.toString().trim()
            
            if (!ingredientList.contains(ingredient)) {
                Toast.makeText(this, "Please select an ingredient from the list", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val itemName = dialogBinding.etItemName.text.toString().trim()
            val stockStr = dialogBinding.etStock.text.toString().trim()
            val sizeValueStr = dialogBinding.etSizeValue.text.toString().trim()
            val unit = dialogBinding.spinnerUnit.selectedItem.toString()
            val priceStr = dialogBinding.etPrice.text.toString().trim()

            if (ingredient.isEmpty() || itemName.isEmpty() || stockStr.isEmpty() || sizeValueStr.isEmpty() || priceStr.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val stock = stockStr.toIntOrNull() ?: 0
            val sizeValue = sizeValueStr.toDoubleOrNull() ?: 0.0
            val price = priceStr.toDoubleOrNull() ?: 0.0

            if (selectedImageUri != null) {
                uploadImageAndSave(ingredient, itemName, stock, "$sizeValue $unit", price, dialog)
            } else {
                saveToDatabase(ingredient, itemName, stock, "$sizeValue $unit", price, "", dialog)
            }
        }

        dialog.show()
    }

    private fun uploadImageAndSave(ingredient: String, itemName: String, stock: Int, size: String, price: Double, dialog: AlertDialog, oldItemName: String? = null) {
        val fileName = UUID.randomUUID().toString()
        val ref = storage.child("inventory_images/$fileName")

        selectedImageUri?.let { uri ->
            ref.putFile(uri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUrl ->
                    if (oldItemName != null) {
                        updateDatabase(oldItemName, ingredient, itemName, stock, size, price, downloadUrl.toString(), dialog)
                    } else {
                        saveToDatabase(ingredient, itemName, stock, size, price, downloadUrl.toString(), dialog)
                    }
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Image upload failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveToDatabase(ingredient: String, itemName: String, stock: Int, size: String, price: Double, imageUrl: String, dialog: AlertDialog) {
        val uid = auth.currentUser?.uid ?: return
        val newItem = InventoryItem(
            ingredient = ingredient,
            name = itemName,
            stock = stock,
            size = size,
            price = price,
            img = imageUrl,
            timestamp = System.currentTimeMillis()
        )

        database.child("Businesses").child(uid).child("inventory").child(itemName).setValue(newItem)
            .addOnSuccessListener {
                Toast.makeText(this, "Item added!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save item", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupBottomNavigation() {
        binding.businessBottomNav.selectedItemId = R.id.nav_business_inventory
        binding.businessBottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_business_dashboard -> { startActivity(Intent(this, BusinessDashboardActivity::class.java)); finish(); true }
                R.id.nav_business_inventory -> true
                R.id.nav_business_orders -> { startActivity(Intent(this, BusinessOrdersActivity::class.java)); finish(); true }
                R.id.nav_business_manage -> { startActivity(Intent(this, BusinessManageActivity::class.java)); finish(); true }
                R.id.nav_business_profile -> { startActivity(Intent(this, BusinessProfileActivity::class.java)); finish(); true }
                else -> false
            }
        }
    }
}