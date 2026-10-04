package com.belagavi.tourism.ui.dashboard

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.belagavi.tourism.data.model.Place
import com.belagavi.tourism.data.repository.PlacesRepository
import com.belagavi.tourism.ui.auth.AuthViewModel
import com.belagavi.tourism.ui.theme.BorderLight
import com.belagavi.tourism.ui.theme.CreamBackground
import com.belagavi.tourism.ui.theme.CreamWarm
import com.belagavi.tourism.ui.theme.ForestPale
import com.belagavi.tourism.ui.theme.ForestPrimary
import com.belagavi.tourism.ui.theme.InkFaint
import com.belagavi.tourism.ui.theme.InkMuted
import com.belagavi.tourism.ui.theme.InkPrimary
import com.belagavi.tourism.ui.theme.InkSecondary
import com.belagavi.tourism.ui.theme.StatusDanger
import com.belagavi.tourism.ui.theme.StatusExceeded
import com.belagavi.tourism.ui.theme.StatusReached
import com.belagavi.tourism.ui.theme.StatusWarning
import com.belagavi.tourism.ui.theme.WhiteSurface
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExpenseItem(
    val id: String = "",
    val title: String = "",
    val category: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val placeId: String? = null,
    val placeName: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseView(
    authViewModel: AuthViewModel,
    placesRepository: PlacesRepository
) {
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()
    val uid = currentUser?.uid
    val db = remember { FirebaseFirestore.getInstance() }

    var expensesList by remember { mutableStateOf<List<ExpenseItem>>(emptyList()) }
    var totalBudget by remember { mutableStateOf(10000.0) }
    var isEditingBudget by remember { mutableStateOf(false) }
    var budgetInput by remember { mutableStateOf("10000") }

    // Form inputs
    var descriptionText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food") }
    var selectedDestination by remember { mutableStateOf("General") }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var destinationDropdownExpanded by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var expenseToDelete by remember { mutableStateOf<ExpenseItem?>(null) }

    val categories = listOf("Food", "Transport", "Stay", "Entry", "Shopping", "Other")
    val allPlaces = remember { placesRepository.getPlaces() }
    val destinationOptions = remember(allPlaces) {
        listOf("General") + allPlaces.map { it.name }.distinct()
    }

    // Number formatter for Indian Rupee
    val currencyFormatter = remember {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        format.maximumFractionDigits = 0
        format
    }

    fun formatRupee(amount: Double): String {
        return "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount.toLong())
    }

    // Real-time listener for user budget goal
    LaunchedEffect(key1 = uid) {
        if (uid != null) {
            db.collection("users")
                .document(uid)
                .addSnapshotListener { doc, _ ->
                    if (doc != null && doc.exists()) {
                        val b = doc.getDouble("budget")
                        if (b != null && b > 0) {
                            totalBudget = b
                            if (!isEditingBudget) {
                                budgetInput = "%.0f".format(b)
                            }
                        }
                    }
                }
        } else {
            totalBudget = 10000.0
            budgetInput = "10000"
        }
    }

    // Real-time listener for expenses collection
    LaunchedEffect(key1 = uid) {
        if (uid != null) {
            db.collection("expenses")
                .whereEqualTo("user_id", uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        expensesList = snapshot.documents.mapNotNull { doc ->
                            val title = doc.getString("name") ?: doc.getString("title") ?: ""
                            val category = doc.getString("category") ?: "Other"
                            val amount = doc.getDouble("amount") ?: 0.0
                            val date = doc.getString("date") ?: ""
                            val placeId = doc.getString("placeId")
                            val placeName = doc.getString("location") ?: doc.getString("placeName") ?: "General"
                            ExpenseItem(doc.id, title, category, amount, date, placeId, placeName)
                        }
                    }
                }
        } else {
            expensesList = emptyList()
        }
    }

    val totalSpent = remember(expensesList) { expensesList.sumOf { it.amount } }

    // Group expenses by destination (matching web layout)
    val groupedExpenses = remember(expensesList) {
        expensesList.groupBy { it.placeName?.ifBlank { "General" } ?: "General" }
    }

    // Category breakdown totals
    val categoryTotals = remember(expensesList) {
        val map = mutableMapOf<String, Double>()
        expensesList.forEach { exp ->
            val cat = if (exp.category.isNotBlank()) exp.category else "Other"
            map[cat] = (map[cat] ?: 0.0) + exp.amount
        }
        map.toList().filter { it.second > 0 }.sortedByDescending { it.second }
    }

    // Destination breakdown totals
    val destinationTotals = remember(expensesList) {
        val map = mutableMapOf<String, Double>()
        expensesList.forEach { exp ->
            val dest = exp.placeName?.ifBlank { "General" } ?: "General"
            map[dest] = (map[dest] ?: 0.0) + exp.amount
        }
        map.toList().filter { it.second > 0 }.sortedByDescending { it.second }.take(5)
    }

    val categoryIcons = mapOf(
        "Food" to "🍽️",
        "Transport" to "🚌",
        "Stay" to "🏨",
        "Entry" to "🎟️",
        "Tickets" to "🎟️",
        "Shopping" to "🛍️",
        "Other" to "📦",
        "Others" to "📦"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        // Page Title & Subtitle (Web-like typography)
        Text(
            text = "Trip Budget",
            fontFamily = FontFamily.Serif,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = InkPrimary
        )
        Text(
            text = "Track every rupee of your Belagavi adventure",
            fontSize = 13.sp,
            color = InkMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        // ── 1. BUDGET SUMMARY CARD (INK / DARK CARD MATCHING WEB) ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = InkPrimary),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Top Row: Total Spent and Budget Goal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "TOTAL SPENT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = CreamBackground.copy(alpha = 0.55f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = formatRupee(totalSpent),
                            fontFamily = FontFamily.Serif,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Normal,
                            color = CreamBackground
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "BUDGET GOAL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = CreamBackground.copy(alpha = 0.55f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = formatRupee(totalBudget),
                            fontFamily = FontFamily.Serif,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Normal,
                            color = CreamBackground.copy(alpha = 0.85f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 4dp Progress Bar matching web
                val pct = if (totalBudget > 0) (totalSpent / totalBudget).toFloat() else 0f
                val clampedPct = pct.coerceIn(0f, 1f)

                val barColor = when {
                    totalSpent > totalBudget -> StatusExceeded
                    totalSpent == totalBudget -> StatusReached
                    totalSpent >= 0.8 * totalBudget -> StatusWarning
                    else -> CreamBackground
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CreamBackground.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(clampedPct)
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress status text and Alert Badge (Matching Web)
                val isExceeded = totalSpent > totalBudget
                val isReached = totalSpent == totalBudget && totalBudget > 0
                val isWarning = totalSpent >= 0.8 * totalBudget && !isReached && !isExceeded

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val progressText = if (isExceeded) {
                        val over = totalSpent - totalBudget
                        "${formatRupee(totalSpent)} of ${formatRupee(totalBudget)} used (${formatRupee(over)} over)"
                    } else if (isReached) {
                        "${formatRupee(totalSpent)} of ${formatRupee(totalBudget)} used (100%)"
                    } else {
                        "${formatRupee(totalSpent)} of ${formatRupee(totalBudget)} used"
                    }

                    Text(
                        text = progressText,
                        fontSize = 11.sp,
                        color = CreamBackground.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (isExceeded || isReached || isWarning) {
                        val badgeColor = when {
                            isExceeded -> StatusExceeded
                            isReached -> StatusReached
                            else -> StatusWarning
                        }
                        val badgeText = when {
                            isExceeded -> "Exceeded by ${formatRupee(totalSpent - totalBudget)}"
                            isReached -> "100% reached"
                            else -> "Near limit"
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = badgeColor.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = badgeText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = badgeColor
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── 2. SET BUDGET GOAL ROW (MATCHING WEB IN-PAGE ADJUSTMENT) ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            border = BorderStroke(1.dp, BorderLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Set Budget Goal",
                        fontFamily = FontFamily.Default,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkPrimary
                    )
                    Text(
                        text = "Adjust your travel budget limit",
                        fontSize = 11.sp,
                        color = InkMuted
                    )
                }

                if (isEditingBudget) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = budgetInput,
                            onValueChange = { budgetInput = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            prefix = { Text("₹", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = InkMuted) },
                            modifier = Modifier.width(110.dp).height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ForestPrimary,
                                unfocusedBorderColor = BorderLight
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                val newLimit = budgetInput.toDoubleOrNull()
                                if (newLimit != null && newLimit > 0) {
                                    totalBudget = newLimit
                                    isEditingBudget = false
                                    if (uid != null) {
                                        db.collection("users").document(uid).update("budget", newLimit)
                                        Toast.makeText(context, "Budget updated to ${formatRupee(newLimit)}", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Enter valid budget amount", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Save", tint = ForestPrimary)
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CreamWarm,
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.clickable { isEditingBudget = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = formatRupee(totalBudget),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = InkPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit budget",
                                tint = ForestPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 3. ADD EXPENSE FORM CARD (MATCHING WEB) ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            border = BorderStroke(1.dp, BorderLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Add Expense",
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Description
                Text(
                    text = "DESCRIPTION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = InkMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    placeholder = { Text("e.g. Gokak Falls Entry Fee", fontSize = 13.sp, color = InkFaint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CreamBackground,
                        unfocusedContainerColor = CreamBackground,
                        focusedBorderColor = ForestPrimary,
                        unfocusedBorderColor = BorderLight
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Category & Destination in Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Category Dropdown
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CATEGORY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = InkMuted,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Box {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CreamBackground,
                                border = BorderStroke(1.dp, BorderLight),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clickable { categoryDropdownExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val catIcon = categoryIcons[selectedCategory] ?: "📦"
                                    Text(
                                        text = "$catIcon $selectedCategory",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = InkPrimary
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = InkMuted)
                                }
                            }
                            DropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false }
                            ) {
                                categories.forEach { cat ->
                                    val icon = categoryIcons[cat] ?: "📦"
                                    DropdownMenuItem(
                                        text = { Text("$icon $cat", fontSize = 13.sp) },
                                        onClick = {
                                            selectedCategory = cat
                                            categoryDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Destination Dropdown
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DESTINATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = InkMuted,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Box {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CreamBackground,
                                border = BorderStroke(1.dp, BorderLight),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clickable { destinationDropdownExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = selectedDestination,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = InkPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = InkMuted)
                                }
                            }
                            DropdownMenu(
                                expanded = destinationDropdownExpanded,
                                onDismissRequest = { destinationDropdownExpanded = false }
                            ) {
                                destinationOptions.forEach { dest ->
                                    DropdownMenuItem(
                                        text = { Text(dest, fontSize = 13.sp) },
                                        onClick = {
                                            selectedDestination = dest
                                            destinationDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Amount & Add Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AMOUNT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = InkMuted,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            placeholder = { Text("0", fontSize = 13.sp, color = InkFaint) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            prefix = { Text("₹ ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = InkMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CreamBackground,
                                unfocusedContainerColor = CreamBackground,
                                focusedBorderColor = ForestPrimary,
                                unfocusedBorderColor = BorderLight
                            )
                        )
                    }

                    Button(
                        onClick = {
                            val desc = descriptionText.trim()
                            val amt = amountText.toDoubleOrNull()

                            if (desc.isBlank()) {
                                Toast.makeText(context, "Please enter an expense description", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (amt == null || amt <= 0) {
                                Toast.makeText(context, "Please enter a valid expense amount", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (uid == null) {
                                Toast.makeText(context, "Please sign in to track expenses", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
                            val dateStr = sdf.format(Date())

                            val docData = mapOf(
                                "user_id" to uid,
                                "name" to desc,
                                "category" to selectedCategory,
                                "location" to selectedDestination,
                                "amount" to amt,
                                "date" to dateStr,
                                "createdAt" to com.google.firebase.Timestamp.now()
                            )

                            db.collection("expenses").add(docData)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Expense added", Toast.LENGTH_SHORT).show()
                                    descriptionText = ""
                                    amountText = ""
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Failed to save expense", Toast.LENGTH_SHORT).show()
                                }
                        },
                        shape = RoundedCornerShape(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForestPrimary,
                            contentColor = CreamBackground
                        ),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── 4. SPENDING BREAKDOWN (PORTED FROM WEB AESTHETICS) ──
        if (expensesList.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Spending Breakdown",
                    fontFamily = FontFamily.Serif,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary
                )
                Text(
                    text = "See where your trip budget is going",
                    fontSize = 12.sp,
                    color = InkMuted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                )

                // By Category Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                    border = BorderStroke(1.dp, BorderLight)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "By Category",
                            fontFamily = FontFamily.Serif,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkPrimary,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )

                        categoryTotals.forEach { (cat, amt) ->
                            val pctVal = if (totalSpent > 0) (amt / totalSpent) else 0.0
                            val pctFormatted = "%.1f%%".format(pctVal * 100)
                            val icon = categoryIcons[cat] ?: "🏷️"

                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = icon, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = cat,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = InkPrimary
                                        )
                                    }
                                    Text(
                                        text = formatRupee(amt),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = InkPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(CreamWarm)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(pctVal.toFloat().coerceIn(0f, 1f))
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(99.dp))
                                                .background(ForestPrimary)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = pctFormatted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = InkMuted,
                                        modifier = Modifier.width(42.dp),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // By Destination Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                    border = BorderStroke(1.dp, BorderLight)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "By Destination",
                            fontFamily = FontFamily.Serif,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkPrimary,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )

                        destinationTotals.forEach { (dest, amt) ->
                            val pctVal = if (totalSpent > 0) (amt / totalSpent) else 0.0
                            val pctFormatted = "%.1f%%".format(pctVal * 100)

                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = ForestPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = dest,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = InkPrimary
                                        )
                                    }
                                    Text(
                                        text = formatRupee(amt),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = InkPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(CreamWarm)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(pctVal.toFloat().coerceIn(0f, 1f))
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(99.dp))
                                                .background(ForestPrimary)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = pctFormatted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = InkMuted,
                                        modifier = Modifier.width(42.dp),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // ── 5. ALL EXPENSES LIST (GROUPED BY DESTINATION MATCHING WEB) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "All Expenses",
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = InkPrimary
            )
            if (expensesList.isNotEmpty()) {
                TextButton(onClick = { showClearConfirmDialog = true }) {
                    Text("Clear all", fontSize = 12.sp, color = InkMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (expensesList.isEmpty()) {
            // Web Empty State
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "💳", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No expenses yet",
                        fontFamily = FontFamily.Serif,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary
                    )
                    Text(
                        text = "Add your first trip expense above",
                        fontSize = 13.sp,
                        color = InkMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            // Destination Grouped List
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                groupedExpenses.forEach { (destination, items) ->
                    val groupTotal = items.sumOf { it.amount }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                        border = BorderStroke(1.dp, BorderLight)
                    ) {
                        Column {
                            // Location Group Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CreamWarm)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = ForestPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = destination,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = InkPrimary
                                    )
                                }
                                Text(
                                    text = formatRupee(groupTotal),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InkPrimary
                                )
                            }

                            HorizontalDivider(color = BorderLight, thickness = 0.5.dp)

                            // Items in this location
                            items.forEachIndexed { index, exp ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = categoryIcons[exp.category] ?: "💰"
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(CreamBackground),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = icon, fontSize = 16.sp)
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = exp.category,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = InkPrimary
                                        )
                                        Text(
                                            text = "${exp.title} • ${exp.date}",
                                            fontSize = 11.sp,
                                            color = InkMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatRupee(exp.amount),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = InkPrimary
                                        )
                                        Text(
                                            text = "Remove",
                                            fontSize = 11.sp,
                                            color = InkFaint,
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .clickable { expenseToDelete = exp }
                                        )
                                    }
                                }
                                if (index < items.size - 1) {
                                    HorizontalDivider(
                                        color = BorderLight,
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }

    // Delete single expense confirmation
    if (expenseToDelete != null) {
        val target = expenseToDelete!!
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("Remove Expense", color = InkPrimary) },
            text = { Text("Are you sure you want to remove '${target.title}' (${formatRupee(target.amount)})?", color = InkSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = target.id
                        expenseToDelete = null
                        if (id.isNotBlank()) {
                            db.collection("expenses").document(id).delete()
                            Toast.makeText(context, "Expense removed", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Remove", color = StatusDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = WhiteSurface
        )
    }

    // Clear all expenses confirmation
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear All Expenses", color = InkPrimary) },
            text = { Text("Are you sure you want to delete all trip expense records? This cannot be undone.", color = InkSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        if (uid != null) {
                            db.collection("expenses").whereEqualTo("user_id", uid).get()
                                .addOnSuccessListener { snap ->
                                    val batch = db.batch()
                                    snap.documents.forEach { doc -> batch.delete(doc.reference) }
                                    batch.commit().addOnSuccessListener {
                                        Toast.makeText(context, "All expenses cleared", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        }
                    }
                ) {
                    Text("Clear All", color = StatusDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = WhiteSurface
        )
    }
}
