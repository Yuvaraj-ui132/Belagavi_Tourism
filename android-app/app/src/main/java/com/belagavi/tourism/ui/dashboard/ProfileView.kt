package com.belagavi.tourism.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import android.Manifest
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.belagavi.tourism.ui.theme.StatusSuccess
import com.belagavi.tourism.ui.theme.StatusWarning
import com.belagavi.tourism.ui.theme.WhiteSurface
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.belagavi.tourism.data.model.Place
import com.belagavi.tourism.data.repository.PlacesRepository
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UserActivity(
    val id: String,
    val title: String,
    val subtitle: String,
    val dateStr: String,
    val isWishlist: Boolean,
    val timestamp: Long
)

@Composable
fun ProfileView(
    authViewModel: AuthViewModel,
    placesRepository: PlacesRepository,
    onNavigateToPlaceDetail: (Int) -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToDiscover: () -> Unit = {},
    onNavigateToExpense: () -> Unit = {},
    onNavigateToExplore: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()
    val uid = currentUser?.uid
    val db = remember { FirebaseFirestore.getInstance() }

    var selectedTabIndex by remember { mutableStateOf(0) } // 0: Overview, 1: Saved Places, 2: Settings & Security

    var wishlistedPlaces by remember { mutableStateOf<List<Place>>(emptyList()) }
    var isWishlistLoading by remember { mutableStateOf(false) }

    var savedPlacesCount by remember { mutableStateOf(0) }
    var expensesCount by remember { mutableStateOf(0) }
    var reviewsCount by remember { mutableStateOf(0) }
    var visitedPlacesCount by remember { mutableStateOf(0) }
    var userBudget by remember { mutableStateOf(10000.0) }
    var recentActivities by remember { mutableStateOf<List<UserActivity>>(emptyList()) }

    // Dialog & Edit states
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showClearDataConfirmType by remember { mutableStateOf<String?>(null) } // "wishlist" or "expenses"

    var nameInput by remember { mutableStateOf("") }
    var budgetInput by remember { mutableStateOf("10000") }
    var isEditingBudget by remember { mutableStateOf(false) }

    // Preference toggles
    var travelAlertsEnabled by remember { mutableStateOf(true) }
    var budgetRemindersEnabled by remember { mutableStateOf(true) }

    // Real Device Location GPS status
    var locationStatusType by remember { mutableStateOf("checking") } // "active", "permission_needed", "services_off"
    val locationManager = remember { context.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager }

    val checkLocationRealStatus: () -> Unit = {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            locationStatusType = "permission_needed"
        } else {
            val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
            val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false
            if (!isGpsEnabled && !isNetworkEnabled) {
                locationStatusType = "services_off"
            } else {
                locationStatusType = "active"
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        checkLocationRealStatus()
        val granted = (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (granted) {
            val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                    locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            if (!isGpsEnabled) {
                Toast.makeText(context, "Location permission granted. Please turn ON location services.", Toast.LENGTH_SHORT).show()
                try {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (e: Exception) {}
            } else {
                Toast.makeText(context, "Location is active and ready for navigation.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Location permission denied. Real-time distance will use default Belagavi city origin.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        checkLocationRealStatus()
    }

    val displayName = currentUser?.displayName ?: currentUser?.email?.split('@')?.firstOrNull() ?: "Traveller"
    val email = currentUser?.email ?: "—"
    val avatarInitial = displayName.firstOrNull()?.uppercase() ?: "T"

    val isGoogleUser = remember(currentUser) {
        currentUser?.providerData?.any { it.providerId == "google.com" } == true
    }

    LaunchedEffect(displayName) {
        nameInput = displayName
    }

    fun updateActivities(newWish: List<UserActivity>?, newExp: List<UserActivity>?) {
        val current = recentActivities.toMutableList()
        if (newWish != null) {
            current.removeAll { it.isWishlist }
            current.addAll(newWish)
        }
        if (newExp != null) {
            current.removeAll { !it.isWishlist }
            current.addAll(newExp)
        }
        current.sortByDescending { it.timestamp }
        recentActivities = current.take(10)
        visitedPlacesCount = (savedPlacesCount + (expensesCount / 2)).coerceAtLeast(0)
    }

    // Load dynamic real-time stats & activity from Firestore
    LaunchedEffect(key1 = uid) {
        if (uid != null) {
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)

            // Budget
            db.collection("users").document(uid).addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    val b = doc.getDouble("budget")
                    if (b != null && b > 0) {
                        userBudget = b
                        if (!isEditingBudget) budgetInput = "%.0f".format(b)
                    }
                    val alerts = doc.getBoolean("pref_travel_alerts")
                    if (alerts != null) travelAlertsEnabled = alerts
                    val rem = doc.getBoolean("pref_budget_reminders")
                    if (rem != null) budgetRemindersEnabled = rem
                }
            }

            // Wishlist
            isWishlistLoading = true
            db.collection("wishlist").whereEqualTo("user_id", uid)
                .addSnapshotListener { snap, _ ->
                    isWishlistLoading = false
                    if (snap != null) {
                        savedPlacesCount = snap.size()
                        val placeIds = snap.documents.mapNotNull { doc ->
                            doc.getLong("placeId")?.toInt() ?: doc.id.split("_").lastOrNull()?.toIntOrNull()
                        }
                        wishlistedPlaces = placeIds.mapNotNull { id ->
                            placesRepository.getPlaceById(id)
                        }
                        val wishActs = snap.documents.mapNotNull { d ->
                            val pName = d.getString("name") ?: "Place"
                            val ts = d.getTimestamp("created_at")?.toDate()?.time ?: System.currentTimeMillis()
                            UserActivity(
                                id = d.id,
                                title = "Saved $pName",
                                subtitle = "Added to your personal shortlist",
                                dateStr = sdf.format(Date(ts)),
                                isWishlist = true,
                                timestamp = ts
                            )
                        }
                        updateActivities(wishActs, null)
                    }
                }

            // Expenses
            db.collection("expenses").whereEqualTo("user_id", uid)
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        expensesCount = snap.size()
                        val expActs = snap.documents.mapNotNull { d ->
                            val title = d.getString("name") ?: "Expense"
                            val amt = d.getDouble("amount") ?: 0.0
                            val loc = d.getString("location") ?: "General"
                            val dateStr = d.getString("date") ?: sdf.format(Date())
                            val ts = d.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                            UserActivity(
                                id = d.id,
                                title = "Logged ₹${amt.toLong()} for $title",
                                subtitle = "$loc",
                                dateStr = dateStr,
                                isWishlist = false,
                                timestamp = ts
                            )
                        }
                        updateActivities(null, expActs)
                    }
                }
        }
    }

    fun formatRupee(amount: Double): String {
        return "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount.toLong())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
            .verticalScroll(rememberScrollState())
    ) {
        // ── 1. TOP PROFILE BANNER (CREAM / SAGE — matches reference) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CreamWarm)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sage-green avatar circle
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(ForestPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = avatarInitial,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            fontFamily = FontFamily.Serif,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = email,
                            fontSize = 12.sp,
                            color = InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }


        // ── 2. TABS: OVERVIEW vs SAVED PLACES vs SETTINGS & SECURITY ──
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = WhiteSurface,
            contentColor = ForestPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = ForestPrimary,
                    height = 2.5.dp
                )
            }
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = {
                    Text(
                        text = "Overview",
                        fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (selectedTabIndex == 0) ForestPrimary else InkSecondary
                    )
                }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = {
                    Text(
                        text = if (savedPlacesCount > 0) "Saved ($savedPlacesCount)" else "Saved",
                        fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (selectedTabIndex == 1) ForestPrimary else InkSecondary
                    )
                }
            )
            Tab(
                selected = selectedTabIndex == 2,
                onClick = { selectedTabIndex = 2 },
                text = {
                    Text(
                        text = "Settings",
                        fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (selectedTabIndex == 2) ForestPrimary else InkSecondary
                    )
                }
            )
        }

        // ── 3. TAB CONTENT ──
        if (selectedTabIndex == 0) {
            // ══════════════════════════════════════════════════
            // OVERVIEW TAB (MATCHING WEB)
            // ══════════════════════════════════════════════════
            Column(modifier = Modifier.padding(20.dp)) {
                // Stats 4-Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OverviewStatCard("Saved", savedPlacesCount.toString(), Modifier.weight(1f)) {
                        selectedTabIndex = 1
                    }
                    OverviewStatCard("Visited", visitedPlacesCount.toString(), Modifier.weight(1f), onNavigateToExplore)
                    OverviewStatCard("Reviews", reviewsCount.toString(), Modifier.weight(1f), onNavigateToExplore)
                    OverviewStatCard("Expenses", expensesCount.toString(), Modifier.weight(1f), onNavigateToExpense)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Trip Budget in Profile
                Text(
                    text = "TRIP BUDGET",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = InkMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

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
                            Text("Budget Goal", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = InkPrimary)
                            Text("Adjust your travel limit", fontSize = 11.sp, color = InkMuted)
                        }

                        if (isEditingBudget) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = budgetInput,
                                    onValueChange = { budgetInput = it },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    prefix = { Text("₹", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InkMuted) },
                                    modifier = Modifier.width(100.dp).height(46.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ForestPrimary,
                                        unfocusedBorderColor = BorderLight
                                    )
                                )
                                IconButton(
                                    onClick = {
                                        val amt = budgetInput.toDoubleOrNull()
                                        if (amt != null && amt > 0) {
                                            userBudget = amt
                                            isEditingBudget = false
                                            if (uid != null) {
                                                db.collection("users").document(uid).update("budget", amt)
                                                Toast.makeText(context, "Budget updated to ${formatRupee(amt)}", Toast.LENGTH_SHORT).show()
                                            }
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
                                        text = formatRupee(userBudget),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = InkPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Recent Activity Card
                Text(
                    text = "RECENT ACTIVITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = InkMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                    border = BorderStroke(1.dp, BorderLight)
                ) {
                    if (recentActivities.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No recent activity yet.",
                                fontSize = 13.sp,
                                color = InkMuted
                            )
                        }
                    } else {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            recentActivities.forEachIndexed { idx, act ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (act.isWishlist) ForestPale else CreamWarm),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = if (act.isWishlist) "❤️" else "💳", fontSize = 14.sp)
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = act.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = InkPrimary
                                        )
                                        Text(
                                            text = act.subtitle,
                                            fontSize = 11.sp,
                                            color = InkMuted
                                        )
                                    }

                                    Text(
                                        text = act.dateStr,
                                        fontSize = 10.sp,
                                        color = InkMuted
                                    )
                                }
                                if (idx < recentActivities.size - 1) {
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

                Spacer(modifier = Modifier.height(28.dp))

                // Sign Out Row (Matching Web Overview Bottom Link)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            authViewModel.signOut()
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build()
                            GoogleSignIn.getClient(context, gso).signOut()
                        },
                        shape = RoundedCornerShape(40.dp),
                        border = BorderStroke(1.dp, StatusDanger.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign Out", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(onClick = { selectedTabIndex = 2 }) {
                        Text("Open Settings & Security →", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = InkPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))
            }
        } else if (selectedTabIndex == 1) {
            // ══════════════════════════════════════════════════
            // SAVED PLACES / WISHLIST TAB (MATCHING WEB)
            // ══════════════════════════════════════════════════
            SavedPlacesProfileContent(
                places = wishlistedPlaces,
                isLoading = isWishlistLoading,
                uid = uid,
                db = db,
                onNavigateToPlaceDetail = onNavigateToPlaceDetail,
                onNavigateToDiscover = onNavigateToDiscover
            )
        } else {
            // ══════════════════════════════════════════════════
            // SETTINGS & SECURITY TAB (MATCHING WEB)
            // ══════════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. ACCOUNT
                SettingsGroup(title = "Account", icon = Icons.Default.Person) {
                    SettingsRow(
                        label = displayName,
                        subtitle = "Display name on reviews and suggestions",
                        action = {
                            OutlinedButton(
                                onClick = { showEditNameDialog = true },
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, BorderLight),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Edit Name", fontSize = 11.sp, color = InkPrimary)
                            }
                        }
                    )
                    HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                    SettingsRow(
                        label = email,
                        subtitle = "Primary contact email",
                        action = {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = StatusSuccess.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Verified", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusSuccess)
                                }
                            }
                        }
                    )
                    HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                    SettingsRow(
                        label = "Account Provider",
                        subtitle = if (isGoogleUser) "Google Single Sign-On" else "Email & Password"
                    )
                    HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                    SettingsRow(
                        label = "Account UID",
                        subtitle = uid ?: "—"
                    )
                }

                // 2. SECURITY
                SettingsGroup(title = "Security", icon = Icons.Default.Lock) {
                    if (!isGoogleUser) {
                        SettingsRow(
                            label = "Account Password",
                            subtitle = "Authenticated with Email and Password",
                            action = {
                                OutlinedButton(
                                    onClick = { showChangePasswordDialog = true },
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, BorderLight),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Change", fontSize = 11.sp, color = InkPrimary)
                                }
                            }
                        )
                        HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                    } else {
                        SettingsRow(
                            label = "Google Single Sign-On",
                            subtitle = "Password and 2-step verification are managed via Google"
                        )
                        HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                    }
                    SettingsRow(
                        label = "Active Session",
                        subtitle = "Sign out safely from this device",
                        action = {
                            OutlinedButton(
                                onClick = {
                                    authViewModel.signOut()
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build()
                                    GoogleSignIn.getClient(context, gso).signOut()
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, StatusDanger.copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Sign Out", fontSize = 11.sp, color = StatusDanger)
                            }
                        }
                    )
                }

                // 3. PREFERENCES
                SettingsGroup(title = "Preferences", icon = Icons.Default.Settings) {
                    SettingsRow(
                        label = "Travel & Weather Alerts",
                        subtitle = "Seasonal tips and monsoon alerts for waterfalls",
                        action = {
                            Switch(
                                checked = travelAlertsEnabled,
                                onCheckedChange = { checked ->
                                    travelAlertsEnabled = checked
                                    if (uid != null) {
                                        db.collection("users").document(uid).update("pref_travel_alerts", checked)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = WhiteSurface,
                                    checkedTrackColor = ForestPrimary
                                )
                            )
                        }
                    )
                    HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                    SettingsRow(
                        label = "Budget Reminders",
                        subtitle = "Notify when nearing travel budget limit",
                        action = {
                            Switch(
                                checked = budgetRemindersEnabled,
                                onCheckedChange = { checked ->
                                    budgetRemindersEnabled = checked
                                    if (uid != null) {
                                        db.collection("users").document(uid).update("pref_budget_reminders", checked)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = WhiteSurface,
                                    checkedTrackColor = ForestPrimary
                                )
                            )
                        }
                    )
                    HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                    SettingsRow(
                        label = "Application Language",
                        subtitle = "English (Default)"
                    )
                    HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                    SettingsRow(
                        label = "Device Location (GPS)",
                        subtitle = when (locationStatusType) {
                            "active" -> "Real-time distances & navigation active"
                            "permission_needed" -> "Tap to grant location permission"
                            "services_off" -> "Tap to enable device location in Settings"
                            else -> "Checking device location status..."
                        },
                        modifier = Modifier.clickable {
                            checkLocationRealStatus()
                            when (locationStatusType) {
                                "permission_needed" -> {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                                "services_off" -> {
                                    Toast.makeText(context, "Location services are turned off. Opening system settings...", Toast.LENGTH_SHORT).show()
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Please enable location in Android Settings.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                "active" -> {
                                    Toast.makeText(context, "Location is active and ready for navigation.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        action = {
                            val badgeText = when (locationStatusType) {
                                "active" -> "Location Active"
                                "services_off" -> "Location Services Off"
                                else -> "Permission Required"
                            }
                            val badgeBg = if (locationStatusType == "active") ForestPale else CreamWarm
                            val badgeBorder = if (locationStatusType == "active") {
                                BorderStroke(1.dp, ForestPrimary.copy(alpha = 0.35f))
                            } else {
                                BorderStroke(1.dp, BorderLight)
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = badgeBg,
                                border = badgeBorder
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = InkPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    )
                }

                // 4. YOUR DATA & CONTROLS
                SettingsGroup(title = "Your Data & Controls", icon = Icons.Default.Favorite) {
                    SettingsRow(
                        label = "Saved Wishlist Places",
                        subtitle = "$savedPlacesCount places",
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { selectedTabIndex = 1 },
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, BorderLight),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("View", fontSize = 11.sp, color = InkPrimary)
                                }
                                OutlinedButton(
                                    onClick = { showClearDataConfirmType = "wishlist" },
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, StatusDanger.copy(alpha = 0.3f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Clear", fontSize = 11.sp, color = StatusDanger)
                                }
                            }
                        }
                    )
                    HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                    SettingsRow(
                        label = "Logged Trip Expenses",
                        subtitle = "$expensesCount records",
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = onNavigateToExpense,
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, BorderLight),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("View", fontSize = 11.sp, color = InkPrimary)
                                }
                                OutlinedButton(
                                    onClick = { showClearDataConfirmType = "expenses" },
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, StatusDanger.copy(alpha = 0.3f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Clear", fontSize = 11.sp, color = StatusDanger)
                                }
                            }
                        }
                    )
                    HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                    SettingsRow(
                        label = "Submitted Reviews",
                        subtitle = "$reviewsCount reviews",
                        action = {
                            OutlinedButton(
                                onClick = onNavigateToExplore,
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, BorderLight),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("View", fontSize = 11.sp, color = InkPrimary)
                            }
                        }
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CreamWarm,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Public destination records and reviews from other travellers are never altered.",
                                fontSize = 11.sp,
                                color = InkMuted,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // 5. PRIVACY & LEGAL
                SettingsGroup(title = "Privacy & Legal", icon = Icons.Default.Lock) {
                    SettingsRow(
                        label = "Privacy Policy",
                        subtitle = "How we handle GPS, account data & storage",
                        action = {
                            OutlinedButton(
                                onClick = onNavigateToPrivacyPolicy,
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, BorderLight),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Read", fontSize = 11.sp, color = InkPrimary)
                            }
                        }
                    )
                    HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                    SettingsRow(
                        label = "Terms of Service",
                        subtitle = "Guidelines for guide usage & community reviews",
                        action = {
                            OutlinedButton(
                                onClick = { showTermsDialog = true },
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, BorderLight),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Read", fontSize = 11.sp, color = InkPrimary)
                            }
                        }
                    )
                }

                // 6. HELP & SUPPORT
                SettingsGroup(title = "Help & Support", icon = Icons.Default.Star) {
                    SettingsRow(
                        label = "Contact / Send Feedback",
                        subtitle = "Report a problem or suggest landmark additions",
                        action = {
                            OutlinedButton(
                                onClick = { showFeedbackDialog = true },
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, BorderLight),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Contact", fontSize = 11.sp, color = InkPrimary)
                            }
                        }
                    )
                }

                // 7. ABOUT
                SettingsGroup(title = "About", icon = Icons.Default.Info) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "Belagavi Tourism",
                            fontFamily = FontFamily.Serif,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkPrimary
                        )
                        Text(
                            text = "Karnataka's Northern Crown Smart Tourism Guide. Discover waterfalls, forts, temples, nature sanctuaries, and heritage across the Belagavi district.",
                            fontSize = 12.sp,
                            color = InkSecondary,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Version 1.0.0 (Android Edition)", fontSize = 11.sp, color = InkMuted)
                            Text("Build 2026.09-PROD", fontSize = 11.sp, color = InkMuted)
                        }
                    }
                }

                // 8. DANGER ZONE
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                    border = BorderStroke(1.dp, StatusDanger.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = StatusDanger, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Danger Zone",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusDanger
                            )
                        }
                        Text(
                            text = "Permanently delete your account and all associated personal data including saved wishlist places, expense records, and conversation history. This action is irreversible.",
                            fontSize = 12.sp,
                            color = InkMuted,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                        )
                        Button(
                            onClick = { showDeleteAccountDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusDanger.copy(alpha = 0.12f),
                                contentColor = StatusDanger
                            ),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, StatusDanger.copy(alpha = 0.4f)),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Delete Account", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }

    // ── DIALOGS ──

    // Edit Display Name Dialog
    if (showEditNameDialog) {
        var tempName by remember { mutableStateOf(displayName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Display Name", color = InkPrimary) },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    singleLine = true,
                    label = { Text("Your Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestPrimary,
                        unfocusedBorderColor = BorderLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = tempName.trim()
                        if (trimmed.isNotBlank()) {
                            authViewModel.updateDisplayName(trimmed)
                            showEditNameDialog = false
                            Toast.makeText(context, "Name updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save", color = ForestPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = WhiteSurface
        )
    }

    // Change Password Dialog (Email users)
    if (showChangePasswordDialog) {
        var currentPass by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var passError by remember { mutableStateOf<String?>(null) }
        var isUpdating by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Change Password", color = InkPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (passError != null) {
                        Text(passError!!, color = StatusDanger, fontSize = 12.sp)
                    }
                    OutlinedTextField(
                        value = currentPass,
                        onValueChange = { currentPass = it },
                        label = { Text("Current Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ForestPrimary)
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("New Password (min 6 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ForestPrimary)
                    )
                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ForestPrimary)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isUpdating,
                    onClick = {
                        passError = null
                        if (currentPass.isBlank() || newPass.isBlank() || confirmPass.isBlank()) {
                            passError = "Please fill in all password fields."
                            return@TextButton
                        }
                        if (newPass.length < 6) {
                            passError = "New password must be at least 6 characters."
                            return@TextButton
                        }
                        if (newPass != confirmPass) {
                            passError = "Passwords do not match."
                            return@TextButton
                        }

                        val user = FirebaseAuth.getInstance().currentUser
                        val userEmail = user?.email
                        if (user != null && userEmail != null) {
                            isUpdating = true
                            val cred = EmailAuthProvider.getCredential(userEmail, currentPass)
                            user.reauthenticate(cred)
                                .addOnSuccessListener {
                                    user.updatePassword(newPass)
                                        .addOnSuccessListener {
                                            isUpdating = false
                                            showChangePasswordDialog = false
                                            Toast.makeText(context, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                        .addOnFailureListener { e ->
                                            isUpdating = false
                                            passError = e.localizedMessage ?: "Failed to update password."
                                        }
                                }
                                .addOnFailureListener {
                                    isUpdating = false
                                    passError = "Current password incorrect."
                                }
                        }
                    }
                ) {
                    Text("Update", color = ForestPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = WhiteSurface
        )
    }

    // Clear Data Confirmation Dialog
    if (showClearDataConfirmType != null) {
        val type = showClearDataConfirmType!!
        AlertDialog(
            onDismissRequest = { showClearDataConfirmType = null },
            title = { Text(if (type == "wishlist") "Clear Saved Places" else "Clear Expense Records", color = InkPrimary) },
            text = { Text("Are you sure you want to delete all your ${if (type == "wishlist") "saved wishlist destinations" else "logged trip expenses"}?", color = InkSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDataConfirmType = null
                        if (uid != null) {
                            val col = if (type == "wishlist") "wishlist" else "expenses"
                            db.collection(col).whereEqualTo("user_id", uid).get()
                                .addOnSuccessListener { snap ->
                                    val batch = db.batch()
                                    snap.documents.forEach { batch.delete(it.reference) }
                                    batch.commit().addOnSuccessListener {
                                        Toast.makeText(context, "Data cleared", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        }
                    }
                ) {
                    Text("Clear All", color = StatusDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirmType = null }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = WhiteSurface
        )
    }

    // Terms of Service Dialog
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms of Service", color = InkPrimary) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "1. Acceptance of Terms\nBy accessing and using Belagavi Tourism, you agree to comply with our community and travel guidelines.\n\n" +
                                "2. Use of Guide & Content\nDestination information, timings, entry fees, and route suggestions are provided for travel planning. Always verify local conditions before visiting remote waterfalls or ghats.\n\n" +
                                "3. User Data & Reviews\nReviews and public feedback must be respectful and factual. You retain control over your personal saved places and expense records.",
                        fontSize = 12.sp,
                        color = InkSecondary,
                        lineHeight = 17.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("Close", color = ForestPrimary)
                }
            },
            containerColor = WhiteSurface
        )
    }

    // Feedback Dialog
    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text("Send Feedback", color = InkPrimary) },
            text = {
                Text(
                    text = "We welcome suggestions for new hidden landmarks, itinerary improvements, or bug reports.\n\nContact email: support@belagavitourism.com",
                    fontSize = 13.sp,
                    color = InkSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFeedbackDialog = false
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:support@belagavitourism.com")
                            putExtra(Intent.EXTRA_SUBJECT, "Belagavi Tourism App Feedback")
                        }
                        try { context.startActivity(intent) } catch (e: Exception) {}
                    }
                ) {
                    Text("Email Us", color = ForestPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Close", color = InkMuted)
                }
            },
            containerColor = WhiteSurface
        )
    }

    // Delete Account Dialog
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Permanently Delete Account?", color = StatusDanger) },
            text = {
                Text(
                    text = "This will permanently remove your account and all associated personal travel data including wishlist, expenses, and AI chat history. This action cannot be reversed.",
                    fontSize = 13.sp,
                    color = InkSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        val user = FirebaseAuth.getInstance().currentUser
                        user?.delete()?.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                authViewModel.signOut()
                                Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to delete account. Please re-authenticate.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Confirm Delete", color = StatusDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = WhiteSurface
        )
    }
}

@Composable
fun OverviewStatCard(label: String, count: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteSurface),
        border = BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count,
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = InkPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = InkMuted
            )
        }
    }
}

@Composable
fun SettingsGroup(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = InkMuted
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            border = BorderStroke(1.dp, BorderLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsRow(
    label: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkPrimary)
            Text(subtitle, fontSize = 11.sp, color = InkMuted, modifier = Modifier.padding(top = 1.dp))
        }
        if (action != null) {
            action()
        }
    }
}

@Composable
fun SavedPlacesProfileContent(
    places: List<Place>,
    isLoading: Boolean,
    uid: String?,
    db: FirebaseFirestore,
    onNavigateToPlaceDetail: (Int) -> Unit,
    onNavigateToDiscover: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Saved Places",
                    fontFamily = FontFamily.Serif,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary
                )
                Text(
                    text = if (places.isEmpty()) "Your shortlist is empty" else "${places.size} destinations in your shortlist",
                    fontSize = 12.sp,
                    color = InkMuted
                )
            }
            if (places.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ForestPale,
                    border = BorderStroke(1.dp, ForestPrimary.copy(alpha = 0.25f)),
                    modifier = Modifier.clickable(onClick = onNavigateToDiscover)
                ) {
                    Text(
                        text = "+ Discover More",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ForestPrimary, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
            }
        } else if (places.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(ForestPale),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = ForestPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Your shortlist is empty",
                        fontFamily = FontFamily.Serif,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Explore Belagavi's forts, waterfalls, temples, and sanctuaries. Tap the heart icon on any destination card to save it here for quick access.",
                        fontSize = 12.sp,
                        color = InkMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = onNavigateToDiscover,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
                    ) {
                        Text("Discover Places", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        } else {
            // Render 2-column cards layout
            val chunked = places.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                chunked.forEach { rowPlaces ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowPlaces.forEach { place ->
                            Box(modifier = Modifier.weight(1f)) {
                                SavedPlaceProfileCard(
                                    place = place,
                                    onNavigateToPlaceDetail = onNavigateToPlaceDetail,
                                    onRemove = {
                                        if (uid != null) {
                                            val docId = "${uid}_${place.id}"
                                            db.collection("wishlist").document(docId).delete()
                                                .addOnSuccessListener {
                                                    Toast.makeText(context, "Removed from Saved Places", Toast.LENGTH_SHORT).show()
                                                }
                                        }
                                    }
                                )
                            }
                        }
                        if (rowPlaces.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(36.dp))
    }
}

@Composable
fun SavedPlaceProfileCard(
    place: Place,
    onNavigateToPlaceDetail: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToPlaceDetail(place.id) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteSurface),
        border = BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(CreamWarm)
            ) {
                val imageModel = "https://belagavi-tourism-planner.web.app/static/images/${place.folder_name}/1.jpg"

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = place.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = InkPrimary.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = place.category.ifBlank { "Destination" },
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Remove heart button
                Surface(
                    shape = CircleShape,
                    color = WhiteSurface,
                    border = BorderStroke(1.dp, BorderLight),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(30.dp)
                        .clickable(onClick = onRemove)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Remove from Saved",
                            tint = StatusDanger,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = place.name,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = place.city.ifBlank { "Belagavi" },
                    fontSize = 11.sp,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
