package com.belagavi.tourism.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.belagavi.tourism.data.model.Place
import com.belagavi.tourism.data.repository.PlacesRepository
import com.belagavi.tourism.ui.auth.AuthViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.*

data class WeatherInfo(
    val temp: Double,
    val alert: String,
    val badge: String
)

data class ReviewItem(
    val id: String = "",
    val username: String = "",
    val rating: Double = 5.0,
    val comment: String = "",
    val date: String = ""
)

fun getCategoryAdvisoryTip(category: String): String {
    val tips = mapOf(
        "waterfall" to "🌊 Waterfall Safety:\nRocks may be slippery. Avoid standing near cliff edges during monsoon seasons.",
        "temple" to "🛕 Temple Visit Tip:\nRemove footwear, dress modestly, and visit during morning or evening for a peaceful experience.",
        "fort" to "🏰 Heritage Site Tip:\nCarry water and wear comfortable shoes for walking long distances.",
        "nature" to "🌿 Nature Trekking Tip:\nStay on marked paths, carry drinking water, and avoid isolated areas after sunset.",
        "lake" to "🌅 Lake Visit Tip:\nBest visited during sunrise or sunset. Be cautious near slippery banks.",
        "reservoir" to "🌅 Reservoir Visit Tip:\nEnjoy the scenic views safely. Do not enter the water and stay on designated paths.",
        "park" to "🌿 Park Visit Tip:\nKeep the surroundings clean. Walk along paved paths and enjoy the scenery.",
        "wildlife" to "🦅 Wildlife Sanctuary Tip:\nKeep vehicle windows rolled up, do not feed or disturb wild animals, and stick to safari tracks."
    )
    return tips[category.lowercase()] ?: "📍 Belagavi Discovery:\nRespect local culture, keep locations clean, and discover responsibly!"
}

fun getHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0 // km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

// Generate travel suggestion based on distance (km)
fun formatTravelSuggestion(distanceKm: Double): String {
    return when {
        distanceKm <= 50.0 -> "Recommended Travel Modes (0–50 km):\n• bike\n• auto\n• local bus\n• self drive"
        distanceKm <= 250.0 -> "Recommended Travel Modes (50–250 km):\n• KSRTC/private bus\n• self drive\n• nearest major town route"
        else -> "Recommended Travel Modes (250+ km):\n• train recommendation"
    }
}

fun getDynamicReachText(
    userLat: Double,
    userLng: Double,
    isFallback: Boolean,
    placeLat: Double,
    placeLng: Double,
    placeName: String,
    approxDistance: Double
): String {
    val city = if (isFallback) {
        "Belagavi"
    } else {
        val cities = listOf(
            Triple("Belagavi", 15.8497, 74.4977),
            Triple("Bengaluru", 12.9716, 77.5946),
            Triple("Hubli", 15.3647, 75.1240),
            Triple("Pune", 18.5204, 73.8567)
        )
        var minDist = Double.MAX_VALUE
        var closest = "Belagavi"
        for (c in cities) {
            val d = getHaversineDistance(userLat, userLng, c.second, c.third)
            if (d < minDist) {
                minDist = d
                closest = c.first
            }
        }
        closest
    }

    val distStr = String.format(Locale.US, "%.1f", approxDistance)
    return when (city) {
        "Belagavi" -> "Departure from Belagavi. To reach $placeName (approx. $distStr km away):\nTake a local bus from CBT, hire an auto-rickshaw, or self-drive via local routes."
        "Bengaluru" -> "Departure from Bengaluru. To reach $placeName (approx. $distStr km away):\nTake an overnight train (e.g., Rani Chennamma Express) or a KSRTC/private sleeper bus from Bengaluru to Belagavi, then proceed to the destination."
        "Hubli" -> "Departure from Hubli. To reach $placeName (approx. $distStr km away):\nTake a KSRTC express bus or a passenger/express train from Hubli to Belagavi, then proceed to the destination."
        "Pune" -> "Departure from Pune. To reach $placeName (approx. $distStr km away):\nTake an express train or NH48 KSRTC/private bus from Pune to Belagavi, then proceed to the destination."
        else -> "Departure from your live location. To reach $placeName (approx. $distStr km away):\nNavigate using state highway networks. Ensure vehicle suitability for local terrain."
    }
}


fun processWeatherData(temp: Double, code: Int): WeatherInfo {
    var weatherAlert = "✨ Optimal Weather: Conditions are optimal for outdoor sightseeing today!"
    var weatherBadge = "✨ Perfect weather for outdoors!"
    
    if (code in 51..67) {
        weatherAlert = "🌧️ Rain Alert: Light rain or drizzle detected. Nature trails and ghat road surfaces may be highly slippery. Drive with care."
        weatherBadge = "🌧️ Light rain alert"
    } else if (code in 71..86) {
        weatherAlert = "⛈️ Storm Alert: Heavy rain active. Access roads to waterfalls and ghat sections may experience blockages. Postpone hikes."
        weatherBadge = "⛈️ Storm alert"
    } else if (temp > 34.0) {
        weatherAlert = "🔥 Midday Heat Warning: High heat index. Wear sunscreen/hats, carry plenty of hydration, and avoid peak sun hikes."
        weatherBadge = "🔥 Midday heat alert"
    }
    return WeatherInfo(temp, weatherAlert, weatherBadge)
}

suspend fun fetchPlaceLiveWeather(lat: Double, lon: Double): Pair<Double, Int>? = withContext(Dispatchers.IO) {
    try {
        val url = java.net.URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true")
        val response = url.readText()
        val tempRegex = "\"temperature\":\\s*([\\d.-]+)".toRegex()
        val codeRegex = "\"weathercode\":\\s*(\\d+)".toRegex()
        val tempMatch = tempRegex.find(response)
        val codeMatch = codeRegex.find(response)
        if (tempMatch != null && codeMatch != null) {
            val temp = tempMatch.groupValues[1].toDoubleOrNull() ?: 24.5
            val code = codeMatch.groupValues[1].toIntOrNull() ?: 0
            Pair(temp, code)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailView(
    placeId: Int,
    placesRepository: PlacesRepository,
    authViewModel: AuthViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val place = remember(placeId) { placesRepository.getPlaceById(placeId) }
    val currentUser by authViewModel.currentUser.collectAsState()
    val uid = currentUser?.uid
    val db = remember { FirebaseFirestore.getInstance() }

    // Location States
    var userLatitude by remember { mutableStateOf<Double?>(null) }
    var userLongitude by remember { mutableStateOf<Double?>(null) }
    var locationStatus by remember { mutableStateOf("Belagavi, Karnataka (Default Origin)") }
    var isFallbackOrigin by remember { mutableStateOf(true) }
    var isGpsServicesOff by remember { mutableStateOf(false) }
    var isLocationFetching by remember { mutableStateOf(false) }

    // Live Weather States
    var liveTemp by remember { mutableStateOf<Double?>(null) }
    var weatherAlertMsg by remember { mutableStateOf("Checking weather and safety tips...") }
    var weatherBadgeMsg by remember { mutableStateOf("Connecting to open-meteo...") }
    var isWeatherLoading by remember { mutableStateOf(true) }

    // Reviews list & rating state
    var reviewsList by remember { mutableStateOf<List<ReviewItem>>(emptyList()) }
    var isReviewsLoading by remember { mutableStateOf(false) }
    var reviewRating by remember { mutableIntStateOf(5) }
    var reviewComment by remember { mutableStateOf("") }
    var isSubmittingReview by remember { mutableStateOf(false) }

    var isWishlisted by remember { mutableStateOf(false) }
    var showDeepHistoryDialog by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Check wishlist status from Firestore (matches web app root wishlist collection)
    LaunchedEffect(key1 = uid, key2 = placeId) {
        if (uid != null && place != null) {
            val docId = "${uid}_${placeId}"
            db.collection("wishlist")
                .document(docId)
                .get()
                .addOnSuccessListener { doc ->
                    isWishlisted = doc.exists()
                }
        }
    }

    // Load reviews in real-time from Firestore (matches web app path: reviews/{placeId}/comments)
    LaunchedEffect(key1 = placeId) {
        isReviewsLoading = true
        db.collection("reviews")
            .document(placeId.toString())
            .collection("comments")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                isReviewsLoading = false
                if (snapshot != null) {
                    reviewsList = snapshot.documents.mapNotNull { doc ->
                        val username = doc.getString("username") ?: "Anonymous"
                        val rating = doc.getDouble("rating") ?: 5.0
                        val comment = doc.getString("text") ?: doc.getString("comment") ?: ""
                        val ts = doc.getTimestamp("timestamp")
                        val date = if (ts != null) {
                            java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US).format(ts.toDate())
                        } else {
                            doc.getString("date") ?: "Recently"
                        }
                        ReviewItem(doc.id, username, rating, comment, date)
                    }
                }
            }
    }

    // Load dynamic weather from Open-Meteo
    LaunchedEffect(key1 = placeId) {
        if (place != null) {
            isWeatherLoading = true
            val weatherData = fetchPlaceLiveWeather(place.lat, place.lon)
            isWeatherLoading = false
            if (weatherData != null) {
                liveTemp = weatherData.first
                val processed = processWeatherData(weatherData.first, weatherData.second)
                weatherAlertMsg = processed.alert
                weatherBadgeMsg = processed.badge
            } else {
                weatherAlertMsg = "⚠️ Weather Offline: Could not connect to real-time weather stations. Assess the sky before departing!"
                weatherBadgeMsg = "Live weather forecast unavailable"
            }
        }
    }

    // Helper to fetch live location
    val fetchLiveLocation: () -> Unit = {
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            isGpsServicesOff = true
            isLocationFetching = false
            locationStatus = "⚠️ Location services are turned OFF"
            isFallbackOrigin = true
            // Fallback Belagavi coordinates
            userLatitude = 15.8527
            userLongitude = 74.5042
        } else {
            isGpsServicesOff = false
            isLocationFetching = true
            locationStatus = "Fetching live location..."
            try {
                fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        isLocationFetching = false
                        if (location != null) {
                            userLatitude = location.latitude
                            userLongitude = location.longitude
                            locationStatus = "🟢 Live Location (${String.format(Locale.US, "%.4f", location.latitude)}, ${String.format(Locale.US, "%.4f", location.longitude)})"
                            isFallbackOrigin = false
                        } else {
                            // Fallback to lastLocation if current location is null
                            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                                if (lastLoc != null) {
                                    userLatitude = lastLoc.latitude
                                    userLongitude = lastLoc.longitude
                                    locationStatus = "🟢 Live Location (${String.format(Locale.US, "%.4f", lastLoc.latitude)}, ${String.format(Locale.US, "%.4f", lastLoc.longitude)})"
                                    isFallbackOrigin = false
                                } else {
                                    locationStatus = "Belagavi, Karnataka (Default Origin)"
                                    isFallbackOrigin = true
                                    userLatitude = 15.8527
                                    userLongitude = 74.5042
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        isLocationFetching = false
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                            if (lastLoc != null) {
                                userLatitude = lastLoc.latitude
                                userLongitude = lastLoc.longitude
                                locationStatus = "🟢 Live Location (${String.format(Locale.US, "%.4f", lastLoc.latitude)}, ${String.format(Locale.US, "%.4f", lastLoc.longitude)})"
                                isFallbackOrigin = false
                            } else {
                                locationStatus = "Belagavi, Karnataka (Default Origin)"
                                isFallbackOrigin = true
                                userLatitude = 15.8527
                                userLongitude = 74.5042
                            }
                        }
                    }
            } catch (_: SecurityException) {
                isLocationFetching = false
                locationStatus = "Belagavi, Karnataka (GPS Access Blocked)"
                isFallbackOrigin = true
                userLatitude = 15.8527
                userLongitude = 74.5042
            }
        }
    }

    // Location Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            fetchLiveLocation()
        } else {
            locationStatus = "Belagavi, Karnataka (GPS Permission Denied)"
            isFallbackOrigin = true
            isGpsServicesOff = false
            isLocationFetching = false
        }
    }

    // Auto-fetch location on load
    LaunchedEffect(key1 = placeId) {
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            fetchLiveLocation()
        } else {
            // Do NOT automatically prompt system permission popup on load to prevent interruptive UX/tab-switch
            locationStatus = "Belagavi, Karnataka (GPS Permission Required)"
            isFallbackOrigin = true
            isGpsServicesOff = false
            isLocationFetching = false
        }
    }

    if (place == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Place not found", color = MaterialTheme.colorScheme.outline)
        }
        return
    }

    val finalUserLat = userLatitude ?: 15.8527 // Belagavi lat
    val finalUserLng = userLongitude ?: 74.5042 // Belagavi lon

    // Dynamic routing calculation using straight-line x 1.3 winding highway approximation
    val straightDist = getHaversineDistance(finalUserLat, finalUserLng, place.lat, place.lon)
    val approxRoadDistance = (straightDist * 1.3).coerceAtLeast(1.0)
    val driveTimeHours = approxRoadDistance / 40.0 // ~40km/h average in ghats
    val driveTimeText = if (driveTimeHours < 1.0) {
        "${round(driveTimeHours * 60).toInt()} mins"
    } else {
        val h = floor(driveTimeHours).toInt()
        val m = round((driveTimeHours - h) * 60).toInt()
        if (m == 0) "$h hr" else "$h hr $m mins"
    }
    // Compute travel suggestion based on distance
    val travelSuggestion = formatTravelSuggestion(approxRoadDistance)

    val dynamicRatingText = remember(reviewsList) {
        if (reviewsList.isEmpty()) "No ratings yet" else String.format(Locale.US, "%.1f ★", reviewsList.map { it.rating }.average())
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = place.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        if (uid != null) {
                            IconButton(onClick = {
                                val newState = !isWishlisted
                                isWishlisted = newState
                                // Use root wishlist collection with {uid}_{placeId} doc ID — matches web app
                                val docId = "${uid}_${placeId}"
                                val docRef = db.collection("wishlist").document(docId)

                                if (newState) {
                                    val data = hashMapOf(
                                        "placeId" to placeId,
                                        "name" to place.name,
                                        "category" to place.category,
                                        "city" to place.city,
                                        "folder_name" to place.folder_name,
                                        "user_id" to uid,
                                        "created_at" to com.google.firebase.Timestamp.now()
                                    )
                                    docRef.set(data).addOnFailureListener {
                                        isWishlisted = false
                                        Toast.makeText(context, "Failed to save bookmark", Toast.LENGTH_SHORT).show()
                                    }
                                    Toast.makeText(context, "${place.name} bookmarked!", Toast.LENGTH_SHORT).show()
                                } else {
                                    docRef.delete().addOnFailureListener {
                                        isWishlisted = true
                                        Toast.makeText(context, "Failed to remove bookmark", Toast.LENGTH_SHORT).show()
                                    }
                                    Toast.makeText(context, "Removed from wishlist", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(
                                    imageVector = if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Wishlist",
                                    tint = if (isWishlisted) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.75.dp)
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            val originStr = if (isFallbackOrigin) "Belagavi, Karnataka" else "$finalUserLat,$finalUserLng"
                            val destStr = "${place.lat},${place.lon}"
                            val gmmIntentUri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(originStr)}&destination=${Uri.encode(destStr)}&travelmode=driving")
                            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                            mapIntent.setPackage("com.google.android.apps.maps")
                            try {
                                context.startActivity(mapIntent)
                            } catch (e: Exception) {
                                // Fallback if native maps app is not available
                                val webIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                context.startActivity(webIntent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Nav", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Live Route Navigation", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            // High-End Banner Card with swipeable real-world image gallery carousel loading from production server with fallback
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    state = androidx.compose.foundation.lazy.rememberLazyListState()
                ) {
                    items(3) { index ->
                        Box(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .fillMaxHeight()
                        ) {
                            var imageModel by remember(place.id, index) {
                                mutableStateOf(
                                    if (index == 0) "https://belagavi-tourism-planner.web.app/static/images/${place.folder_name}/1.jpg"
                                    else "https://belagavi-tourism-planner.web.app/static/images/${place.folder_name}/${index + 1}.jpg"
                                )
                            }
                            AsyncImage(
                                model = imageModel,
                                contentDescription = "${place.name} Image ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                onError = {
                                    if (index > 0) {
                                        imageModel = "https://belagavi-tourism-planner.web.app/static/images/${place.folder_name}/1.jpg"
                                    }
                                },
                                placeholder = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_gallery),
                                error = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_gallery)
                            )
                            // Elegant glassmorphism dark gradient overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.5f)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                        )
                                    )
                            )
                        }
                    }
                }
                
                // Floating indicators or tags on top of image
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = place.city,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    
                    Text(
                        text = "Swipe for Gallery ➔",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Name & Rating Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = place.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = place.category,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "•",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = place.city,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (reviewsList.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Rating",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = dynamicRatingText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // AI Travel Guide Contextual CTA Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController.navigate("ai_chat?placeId=${place.id}")
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Curious about ${place.name}?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Ask AI Guide about history, timings, entry fee & travel tips",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Ask AI Guide",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 2. Weather Check Widget
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = "Weather Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live Weather Check",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isWeatherLoading) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fetching live temperature...", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (liveTemp != null) "%,.1f°C".format(liveTemp) else "--°C",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = weatherBadgeMsg,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // 3. Smart Route & Travel Planner Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CompassCalibration,
                                contentDescription = "Compass",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Smart Route & Travel Planner",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // GPS Services Disabled Warning Card
                        if (isGpsServicesOff) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CompassCalibration,
                                            contentDescription = "GPS Disabled",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Location services are turned OFF",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Please turn on GPS to get live travel routes and accurate distance.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                try {
                                                    val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Could not open settings: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Text("Open Location Settings", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                fetchLiveLocation()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp)
                                        ) {
                                            Text("Try Again", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        // Origin Location Widget
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "📍 DEPARTURE ORIGIN",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isLocationFetching) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.5.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Fetching live location...",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Text(
                                                text = locationStatus,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                                Button(
                                    onClick = {
                                        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                        if (hasFine || hasCoarse) {
                                            fetchLiveLocation()
                                        } else {
                                            val permissions = arrayOf(
                                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                            locationPermissionLauncher.launch(permissions)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), contentColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Retry GPS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Route Metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Distance", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                    Text("%,.1f km".format(approxRoadDistance), fontSize = 14.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Drive Time", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                    Text(driveTimeText, fontSize = 14.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1.2f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Route Link", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                    Text(
                                        text = if (isFallbackOrigin) "Direct Road Est." else "NH-48 Highway",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Professional Open Route in Google Maps button
                        Button(
                            onClick = {
                                val originStr = if (isFallbackOrigin) "Belagavi, Karnataka" else "$finalUserLat,$finalUserLng"
                                val destStr = "${place.lat},${place.lon}"
                                val mapIntentUri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(originStr)}&destination=${Uri.encode(destStr)}&travelmode=driving")
                                val mapIntent = Intent(Intent.ACTION_VIEW, mapIntentUri)
                                mapIntent.setPackage("com.google.android.apps.maps")
                                try {
                                    context.startActivity(mapIntent)
                                } catch (e: Exception) {
                                    // Fallback if native maps app is not available
                                    val webIntent = Intent(Intent.ACTION_VIEW, mapIntentUri)
                                    context.startActivity(webIntent)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Map icon",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Open Route in Google Maps",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                // Travel Suggestion Card (compact)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = travelSuggestion,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                        Spacer(modifier = Modifier.height(16.dp))

                        // Live Safety Advisory
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Advisory",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Live Safety Advisory",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = weatherAlertMsg,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(top = 2.dp),
                                        lineHeight = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Local Travel Advice",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = getCategoryAdvisoryTip(place.category),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(top = 2.dp),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Travel Expense Estimator (replicated web logic)
                        Text(
                            text = "⛽ Smart Travel Expense Estimator (Approx.)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("🏍️ Bike Fuel Cost", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Text("₹%,.0f".format(approxRoadDistance * 2.5), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("🚗 Car Fuel Cost", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Text("₹%,.0f".format(approxRoadDistance * 7.0), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("🚌 Bus Estimate (avg. KSRTC)", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Text("₹%,.0f".format(approxRoadDistance * 1.5), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                Text(
                                    text = "Rates: Bike (~₹2.5/km), Car (~₹7.0/km), Bus (~₹1.5/km).",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // 4. Dynamic Category-Aware Short Info Cards (Compact 4-Card View)
                val categoryLower = place.category.lowercase()

                if (categoryLower == "waterfall" || categoryLower == "nature" || categoryLower == "wildlife" || categoryLower == "forest" || categoryLower == "lake" || categoryLower == "reservoir" || categoryLower == "park") {
                    // --- WATERFALL / NATURE SHORT VIEW (4 COMPACT CARDS) ---

                    // Card 1: About Place
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "🌊 About Place",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = place.description.ifBlank { "A spectacular natural landmark inside the scenic forest ranges of Belagavi." },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card 2: Key Facts
                    val heightVal = when {
                        place.name.contains("Godchinamalaki", ignoreCase = true) -> "45m Two-Tier Cascade"
                        place.name.contains("Gokak", ignoreCase = true) -> "52m Vertical Plunge"
                        place.name.contains("Vajra", ignoreCase = true) -> "60m Forest Plunge"
                        place.name.contains("Jalavane", ignoreCase = true) -> "Multi-Tier Jungle Flow"
                        place.name.contains("Jamboti", ignoreCase = true) -> "Rocky Forest Flow"
                        place.name.contains("Sada", ignoreCase = true) -> "200ft Volcanic Plunge"
                        place.name.contains("Surala", ignoreCase = true) -> "300ft Canyon Plunge"
                        else -> "Scenic Natural Plunge"
                    }
                    val difficultyVal = when {
                        place.name.contains("Godchinamalaki", ignoreCase = true) -> "Easy to Moderate"
                        place.name.contains("Gokak", ignoreCase = true) -> "Easy (Road Access)"
                        place.name.contains("Vajra", ignoreCase = true) -> "Hard / Strenuous"
                        place.name.contains("Jalavane", ignoreCase = true) -> "Moderate to Hard"
                        place.name.contains("Jamboti", ignoreCase = true) -> "Easy Walk"
                        place.name.contains("Sada", ignoreCase = true) -> "Hard (4km Trek)"
                        place.name.contains("Surala", ignoreCase = true) -> "Easy to Viewpoint"
                        else -> "Moderate"
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "📍 Key Facts",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Height / Flow", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Text(heightVal, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Best Season", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Text(place.best_time.ifBlank { "Monsoons (July-Oct)" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Trek Difficulty", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Text(difficultyVal, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card 3: Safety & Travel
                    val monsoonSafety = when {
                        place.name.contains("Gokak", ignoreCase = true) -> "Stay behind high fences; bridge closed during heavy wind."
                        place.name.contains("Godchinamalaki", ignoreCase = true) -> "Highly slippery rocky layers; do not attempt to swim."
                        place.name.contains("Vajra", ignoreCase = true) -> "Dense forest paths prone to sudden monsoonal flash floods."
                        place.name.contains("Jalavane", ignoreCase = true) -> "Leech caution on paths; wet rocks are highly slippery."
                        place.name.contains("Jamboti", ignoreCase = true) -> "High monsoonal fog on ghat roads; drive carefully."
                        place.name.contains("Sada", ignoreCase = true) -> "Strenuous stream crossings; flash flood caution."
                        place.name.contains("Surala", ignoreCase = true) -> "Sheer canyon edge drops; stay strictly on viewpoints."
                        else -> "Wet rocks are slippery; do not enter deep water currents."
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "⚠️ Safety & Travel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Monsoon Safety: $monsoonSafety",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Quick Tip: ${place.local_tips.ifBlank { "Wear high-grip shoes and carry personal drinking water." }}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card 4: Nearby Nature
                    val nearbyNatureVal = when {
                        place.name.contains("Gokak", ignoreCase = true) -> "Gokak Sandstone Gorges, hanging bridge, and Ghataprabha Bird Sanctuary."
                        place.name.contains("Godchinamalaki", ignoreCase = true) -> "Markandeya River Valley Forests and terraced volcanic shelves."
                        place.name.contains("Vajra", ignoreCase = true) -> "Bhimgad Forest Reserve canopy and Mandovi River basin."
                        place.name.contains("Jalavane", ignoreCase = true) -> "Bhimgad Wildlife Sanctuary bio-corridor."
                        place.name.contains("Jamboti", ignoreCase = true) -> "Lush Jamboti hills and high-altitude springs."
                        place.name.contains("Sada", ignoreCase = true) -> "Western Ghats deep Khanapur forest ranges."
                        place.name.contains("Surala", ignoreCase = true) -> "Mhadei Wildlife Reserve and V-shaped Virdi canyon."
                        else -> "Lush semi-evergreen Western Ghats forest biosphere."
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "🌿 Nearby Nature",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = nearbyNatureVal,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                lineHeight = 16.sp
                            )
                        }
                    }

                } else if (categoryLower == "temple") {
                    // --- TEMPLE SHORT VIEW (4 COMPACT CARDS) ---

                    // Card 1: Short History
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "🛕 About Temple",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = place.description.ifBlank { "An ancient stone temple showcasing the outstanding spiritual and architectural heritage of Belagavi." },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card 2: Main Deity
                    val deityVal = when {
                        place.name.contains("Kamal", ignoreCase = true) -> "Lord Neminatha (22nd Jain Tirthankara, black basalt stone)"
                        place.name.contains("Kapileshwar", ignoreCase = true) -> "Lord Shiva (Swayambhu Natural Stone Lingam)"
                        place.name.contains("Kopeshwara", ignoreCase = true) -> "Lord Shiva (Kopeshwar) & Lord Vishnu (Dhopeshwar)"
                        place.name.contains("Military", ignoreCase = true) -> "Lord Shiva (MLIRC Consecrated Lingam)"
                        place.name.contains("Siddheshwar", ignoreCase = true) -> "Lord Shiva (Siddheshwar Cave Lingam)"
                        place.name.contains("Yellamma", ignoreCase = true) -> "Goddess Renuka Yellamma (Renuka Devi)"
                        else -> "Lord Shiva"
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("✨ Main Deity: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(deityVal, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card 3: Built By & Century
                    val builtByVal = when {
                        place.name.contains("Kamal", ignoreCase = true) -> "Bichana (Ratta Dynasty)"
                        place.name.contains("Kapileshwar", ignoreCase = true) -> "Ratta Dynasty & Devagiri Yadavas"
                        place.name.contains("Kopeshwara", ignoreCase = true) -> "Silahara Dynasty Kings"
                        place.name.contains("Military", ignoreCase = true) -> "Maratha Light Infantry (MLIRC)"
                        place.name.contains("Siddheshwar", ignoreCase = true) -> "Local Feudal Rulers"
                        place.name.contains("Yellamma", ignoreCase = true) -> "Rashtrakuta Dynasty & Bommappa Nayaka"
                        else -> "Medieval Feudal Kings"
                    }
                    val centuryVal = when {
                        place.name.contains("Kamal", ignoreCase = true) -> "12th Century (1204 AD)"
                        place.name.contains("Kapileshwar", ignoreCase = true) -> "Ancient / 12th Century"
                        place.name.contains("Kopeshwara", ignoreCase = true) -> "12th Century (1109 AD)"
                        place.name.contains("Military", ignoreCase = true) -> "Modern (1960s/1990s)"
                        place.name.contains("Siddheshwar", ignoreCase = true) -> "Medieval Era"
                        place.name.contains("Yellamma", ignoreCase = true) -> "10th-11th Century"
                        else -> "12th Century"
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "👑 Patronage & Era",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Built By", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Text(builtByVal, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Era / Century", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Text(centuryVal, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card 4: Architecture Style
                    val archStyle = when {
                        place.name.contains("Kamal", ignoreCase = true) -> "Chalukyan black basalt with 72-petal stone lotus ceiling."
                        place.name.contains("Kapileshwar", ignoreCase = true) -> "Traditional Deccan stone layout with holy pool."
                        place.name.contains("Kopeshwara", ignoreCase = true) -> "Late Chalukyan / Hoysala Swarga Mandap open hall."
                        place.name.contains("Military", ignoreCase = true) -> "Modern clean marble sanctum and shikhar tower."
                        place.name.contains("Siddheshwar", ignoreCase = true) -> "Natural hill cave shrine with stone steps path."
                        place.name.contains("Yellamma", ignoreCase = true) -> "Rashtrakuta-Chalukyan fusion fortified walls."
                        else -> "Kadamba-Dravidian border stone masonry."
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "🏛️ Architecture Style",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = archStyle,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                lineHeight = 16.sp
                            )
                        }
                    }

                } else {
                    // --- FORTS / MONUMENTS / GENERAL SHORT VIEW (4 COMPACT CARDS) ---

                    // Card 1: Short History
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "🏰 About Fort",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = place.description.ifBlank { "A massive historical fortress representing the outstanding military defense heritage of the region." },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card 2: Dynasty & Builders
                    val dynastyVal = when {
                        place.name.contains("Belagavi", ignoreCase = true) -> "Ratta Dynasty, Adil Shahis, Mughals, Marathas, British"
                        place.name.contains("Kittur", ignoreCase = true) -> "Kittur Desai Dynasty (vassals of Bijapur/Marathas)"
                        place.name.contains("Parasgad", ignoreCase = true) -> "Ratta Dynasty & Shivaji Maharaj"
                        place.name.contains("Yellur", ignoreCase = true) -> "Ratta Dynasty & Maratha Sardars"
                        place.name.contains("Ramdurga", ignoreCase = true) -> "Marathas & Bhave Dynasty"
                        place.name.contains("Vidhana", ignoreCase = true) -> "State Government of Karnataka"
                        else -> "Ratta & Maratha Feudal Chieftains"
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "👑 Dynasty & Builders",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dynastyVal,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card 3: Century & Era
                    val centuryVal = when {
                        place.name.contains("Belagavi", ignoreCase = true) -> "13th Century (1204 AD)"
                        place.name.contains("Kittur", ignoreCase = true) -> "17th Century (1650s)"
                        place.name.contains("Parasgad", ignoreCase = true) -> "10th Century / 17th Century"
                        place.name.contains("Yellur", ignoreCase = true) -> "12th Century / 17th Century"
                        place.name.contains("Ramdurga", ignoreCase = true) -> "17th Century (1753 AD)"
                        place.name.contains("Vidhana", ignoreCase = true) -> "21st Century (2012)"
                        else -> "13th Century"
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("📅 Consecration / Century: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(centuryVal, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card 4: Historical Importance
                    val importanceVal = when {
                        place.name.contains("Belagavi", ignoreCase = true) -> "A religious syncretism marvel housing Jaina, Hindu, and Islamic heritage inside a single fortress shield."
                        place.name.contains("Kittur", ignoreCase = true) -> "Historic battlefield of Rani Chennamma's legendary armed rebellion of 1824 against British rule."
                        place.name.contains("Parasgad", ignoreCase = true) -> "Strategic hilltop watchtower peak reinforced by Chhatrapati Shivaji Maharaj."
                        place.name.contains("Yellur", ignoreCase = true) -> "Also called Rajhansgad, a 360-degree panoramic watchpost that guarded South Belagavi."
                        place.name.contains("Ramdurga", ignoreCase = true) -> "The defensive stronghold and administrative capital of the Ramdurg Princely State."
                        place.name.contains("Vidhana", ignoreCase = true) -> "Suvarna Vidhana Soudha, monument to democratic governance and regional administration."
                        else -> "Vital regional security post controlling crucial medieval ghat routes."
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "🛡️ Importance",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = importanceVal,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // --- COLLAPSIBLE DETAILED SECTION TRIGGERS ---
                Spacer(modifier = Modifier.height(12.dp))

                var showDetailedModal by remember { mutableStateOf(false) }

                val exploreBtnText = when {
                    categoryLower == "temple" -> "📖 Explore Temple History"
                    categoryLower == "fort" || place.name.contains("Fort", ignoreCase = true) -> "🏰 Explore Fort History"
                    else -> "📖 Explore Detailed Information"
                }

                Button(
                    onClick = { showDetailedModal = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Text(text = exploreBtnText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                // Full Detailed Modal Sheet
                if (showDetailedModal) {
                    AlertDialog(
                        onDismissRequest = { showDetailedModal = false },
                        title = {
                            Text(
                                text = exploreBtnText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        text = {
                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 480.dp)
                                    .verticalScroll(scrollState)
                            ) {
                                // First add deep history description if available
                                if (place.detailed_history.isNotBlank()) {
                                    Text(
                                        text = "📖 Deep History Overview",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = place.detailed_history,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 18.sp
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                    )
                                }

                                // Add category-specific fields dynamically
                                getExtendedHistoryDetails(place).forEach { (sectionTitle, sectionText) ->
                                    if (sectionText.isNotBlank()) {
                                        Text(
                                            text = sectionTitle,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = sectionText,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 18.sp
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { showDetailedModal = false },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Close")
                            }
                        },
                        shape = RoundedCornerShape(18.dp)
                    )
                }

                // 5. Quick Facts Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "📌 Quick Facts",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Visit Duration", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text(place.visit_duration.ifBlank { "2 Hours" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Entry Fee", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text(place.entry_fee.ifBlank { "Free" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Best Season", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text(place.best_time.ifBlank { "Year-round" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Nearby Spots Card (Parity with Web App!)
                val nearbySpots = remember(placeId) {
                    placesRepository.getPlaces()
                        .filter { it.id != place.id }
                        .map { p ->
                            p to getHaversineDistance(place.lat, place.lon, p.lat, p.lon)
                        }
                        .sortedBy { it.second }
                        .take(3)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "📍 Nearby Spots",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            nearbySpots.forEach { (nearbyPlace, dist) ->
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            navController.navigate("place/${nearbyPlace.id}") {
                                                popUpTo("place/$placeId") { inclusive = true }
                                            }
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AsyncImage(
                                            model = "https://belagavi-tourism-planner.web.app/static/images/${nearbyPlace.folder_name}/1.jpg",
                                            contentDescription = nearbyPlace.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(70.dp),
                                            placeholder = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_gallery),
                                            error = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_gallery)
                                        )
                                        Column(modifier = Modifier.padding(6.dp)) {
                                            Text(
                                                text = nearbyPlace.name,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Text(
                                                text = "%,.1f km".format(dist),
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Reach & Transport details (Fully Populated & Compact)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "🚌 How to Reach & Local Info",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // 1. Nearest City
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏙️ Nearest City / Taluk: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(place.city, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        
                        // 2. Distance from City
                        if (place.transport.distance_from_city.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📏 Distance from City: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(place.transport.distance_from_city, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        // 3. Railway / Auto info
                        if (place.transport.auto_taxi.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("🛺 Auto, Railway & Taxi Availability:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(place.transport.auto_taxi, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
                        }

                        // 4. Drive & Parking Quality
                        if (place.transport.drive.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("🚗 Road Quality & Parking:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(place.transport.drive, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
                        }

                        // 5. Ideal Route info (how_to_reach) - Dynamically adapted from user live location
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("🗺️ Recommended Route Planner:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        val dynamicReachText = getDynamicReachText(
                            userLat = finalUserLat,
                            userLng = finalUserLng,
                            isFallback = isFallbackOrigin,
                            placeLat = place.lat,
                            placeLng = place.lon,
                            placeName = place.name,
                            approxDistance = approxRoadDistance
                        )
                        Text(dynamicReachText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp), lineHeight = 16.sp)


                        // 6. Local Travel Tips
                        if (place.local_tips.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("💡 Essential Local Travel Tips:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(place.local_tips, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp), lineHeight = 16.sp)
                        }

                        // 7. Bus Availability details
                        if (place.transport.bus.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "🚌 Regular Bus Connections:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            place.transport.bus.forEach { bus ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsBus,
                                        contentDescription = "Bus",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(bus.route, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = "Freq: ${bus.frequency} • Fare: ${bus.fare}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 7. Reviews & Ratings Section (Firestore backed)
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⭐ Reviews & Ratings",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Avg: $dynamicRatingText",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    // Write a review card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Write a Review",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            // Stars Selector
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                (1..5).forEach { starVal ->
                                    val icon = if (reviewRating >= starVal) Icons.Default.Star else Icons.Default.StarBorder
                                    IconButton(
                                        onClick = { reviewRating = starVal },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = "Star $starVal",
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = reviewComment,
                                onValueChange = { reviewComment = it },
                                placeholder = { Text("Share your experience at ${place.name}...", fontSize = 13.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
                                maxLines = 3,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (reviewComment.isBlank()) {
                                        Toast.makeText(context, "Review text cannot be empty.", Toast.LENGTH_SHORT).show()
                                    } else if (uid == null) {
                                        Toast.makeText(context, "You must be logged in to leave a review.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isSubmittingReview = true
                                        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                                        val dateStr = sdf.format(Date())

                                        val newReview = hashMapOf(
                                            "userId" to uid,
                                            "username" to (currentUser?.displayName ?: currentUser?.email?.split('@')?.firstOrNull() ?: "Traveller"),
                                            "rating" to reviewRating.toDouble(),
                                            "text" to reviewComment.trim(),     // matches web app field name
                                            "comment" to reviewComment.trim(),  // kept for compatibility
                                            "date" to dateStr,
                                            "timestamp" to com.google.firebase.Timestamp.now() // matches web serverTimestamp type
                                        )

                                        // Use same path as web app: reviews/{placeId}/comments
                                        db.collection("reviews")
                                            .document(placeId.toString())
                                            .collection("comments")
                                            .add(newReview)
                                            .addOnSuccessListener {
                                                isSubmittingReview = false
                                                reviewComment = ""
                                                reviewRating = 5
                                                Toast.makeText(context, "Review posted!", Toast.LENGTH_SHORT).show()
                                            }
                                            .addOnFailureListener { e ->
                                                isSubmittingReview = false
                                                Toast.makeText(context, "Failed to submit review: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isSubmittingReview,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.height(40.dp)
                            ) {
                                if (isSubmittingReview) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Submit", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Post Review", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Reviews List
                    if (isReviewsLoading) {
                        Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (reviewsList.isEmpty()) {
                        Text(
                            text = "No reviews yet. Be the first to write one!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        )
                    } else {
                        reviewsList.forEach { review ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = review.username,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = review.date,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        (1..5).forEach { star ->
                                            Icon(
                                                imageVector = if (review.rating >= star) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = "Star",
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = review.comment,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(top = 6.dp),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getExtendedHistoryDetails(place: Place): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()
    val category = place.category.lowercase()

    when {
        category == "temple" -> {
            val about = when {
                place.name.contains("Kamal", ignoreCase = true) -> "A magnificent 12th-century Jain temple inside the Belagavi Fort, celebrated for its architectural precision and status as a protected monument under the Archaeological Survey of India."
                place.name.contains("Kapileshwar", ignoreCase = true) -> "Revered as the oldest Hindu shrine in Belagavi city, the temple houses a self-manifested (Swayambhu) Shiva Lingam and is traditionally called the 'Dakshina Kashi' (Kashi of the South)."
                place.name.contains("Kopeshwara", ignoreCase = true) -> "An architectural marvel of the 12th century situated on the banks of the Krishna River in Khidrapur, celebrated globally for its unique open-to-sky hall and exquisite carvings."
                place.name.contains("Military", ignoreCase = true) -> "A beautifully maintained modern temple complex inside the Belagavi Cantonment, built and managed under army discipline by the Maratha Light Infantry Regimental Centre."
                place.name.contains("Siddheshwar", ignoreCase = true) -> "A highly revered hilltop cave shrine located on the rocky outskirts of Kanbargi village, offering a tranquil and meditative environment."
                place.name.contains("Yellamma", ignoreCase = true) -> "A legendary Shakti Peetha atop the Yellammagudda hill in Saundatti, dedicated to Goddess Renuka Yellamma and deeply revered across South-Western India."
                else -> "A sacred and historic temple in the Belagavi region, representing the rich spiritual heritage and traditional stone architecture of Northern Karnataka."
            }
            list.add("ℹ️ About" to about)

            val deity = when {
                place.name.contains("Kamal", ignoreCase = true) -> "Neminatha (the 22nd Jain Tirthankara, sculpted in a seated meditative posture from polished black stone)."
                place.name.contains("Kapileshwar", ignoreCase = true) -> "Lord Shiva (worshipped in the form of a sacred natural stone Swayambhu Lingam)."
                place.name.contains("Kopeshwara", ignoreCase = true) -> "Lord Shiva (worshipped as Kopeshwar - the angry Shiva) and Lord Vishnu (worshipped as Dhopeshwar)."
                place.name.contains("Military", ignoreCase = true) -> "Lord Shiva (worshipped in the form of a consecrated stone Lingam)."
                place.name.contains("Siddheshwar", ignoreCase = true) -> "Lord Shiva (worshipped as Siddheshwar inside a cool natural cave)."
                place.name.contains("Yellamma", ignoreCase = true) -> "Goddess Renuka Yellamma (Renuka Devi, wife of Sage Jamadagni)."
                else -> "Lord Shiva"
            }
            list.add("🛕 Main Deity" to deity)

            val origin = when {
                place.name.contains("Kamal", ignoreCase = true) -> "According to historical records and stone inscriptions, the temple and the central idol of Neminatha were commissioned and consecrated in 1204 AD by Bichana (Bichiraja), a devout Jain minister serving under King Kartavirya IV of the Ratta Dynasty."
                place.name.contains("Kapileshwar", ignoreCase = true) -> "Local sthala purana maintains that the great Vedic Sage Kapila resided in this tranquil grove, installed the sacred Lingam, and performed intense penance here to attain spiritual liberation."
                place.name.contains("Kopeshwara", ignoreCase = true) -> "Puranic legend states that after Sati's self-immolation in Daksha's sacrifice, Lord Shiva was flying in a blind rage. Lord Vishnu brought Him to this peaceful riverbank spot to soothe His anger (Kopa), leading to the dual presence of both deities."
                place.name.contains("Military", ignoreCase = true) -> "Established by the Maratha Light Infantry (MLIRC) regiment officers and jawans as a military chapel/shrine to foster spiritual resilience and serve as a central place of worship for the defense services."
                place.name.contains("Siddheshwar", ignoreCase = true) -> "Local folklore recounts that a natural Swayambhu Shiva Lingam was discovered deep inside a rock cleft by grazing village shepherds, who consecrated the site for daily prayers."
                place.name.contains("Yellamma", ignoreCase = true) -> "Sthalapurana describes this hill as the sacred site of Renuka Devi's intense penance. Her son Lord Parashurama established the shrine here following the divine command of his father and the subsequent miracle of her spiritual restoration."
                else -> ""
            }
            if (origin.isNotBlank()) {
                list.add("📜 How the Deity Came There / Origin Story" to origin)
            }

            val timeline = when {
                place.name.contains("Kamal", ignoreCase = true) -> "• 1204 AD: Built by Ratta minister Bichana inside the fort.\n• 16th Century: Fortified during the expansion of the Belagavi Fort by Yakub Adil Shah.\n• 1914 AD: Declared a protected historical site by the British administration.\n• Modern Era: Carefully maintained under the Archaeological Survey of India (ASI)."
                place.name.contains("Kapileshwar", ignoreCase = true) -> "• Ancient Era: Cave/shrine established by local Shaivite hermits.\n• 12th-13th Century: Reinforcements added by Ratta and Yadava kings.\n• 1760s: Received structural patronages and maintenance grants under the Maratha Peshwa administration.\n• 1980s: Development of the modern temple complex and pushkarini water tank."
                place.name.contains("Kopeshwara", ignoreCase = true) -> "• 1109 AD: Construction initiated under Silahara King Gandaraditya.\n• 1150s: Extensively expanded and completed by Yadava King Singhanadeva.\n• 14th Century: Survived architectural damages during Deccan Sultanate military expeditions due to its hidden forest location.\n• Modern Era: Recognized as an architectural treasure under national preservation."
                place.name.contains("Military", ignoreCase = true) -> "• 1960s: Simple worship shrine setup by military jawans.\n• 1990s: Expanded into a grand temple structure surrounded by landscaped lawns.\n• 2012: Addition of children's toy train and eco-park facilities."
                place.name.contains("Siddheshwar", ignoreCase = true) -> "• Pre-modern: Used as a natural silent cave retreat by wandering ascetics.\n• 19th Century: Villagers built the first stone steps to climb the hillock.\n• Modern: Upgraded with modern pathways and regular pilgrim amenities."
                place.name.contains("Yellamma", ignoreCase = true) -> "• 10th-11th Century: Foundation stone laid by the early Rashtrakuta kings.\n• 1514 AD: Rebuilt and expanded by Bommappa Nayaka of Rayabag.\n• 17th-18th Century: Massive defensive perimeter walls built by Maratha Sardars.\n• Modern Era: Managed by a dedicated state temple board serving millions of annual pilgrims."
                else -> "• 12th Century: Stone shrine established by local rulers.\n• 18th Century: Reconstructed during Maratha rule.\n• Modern: Revitalized with local community grants."
            }
            list.add("📅 Historical Timeline" to timeline)

            val builders = when {
                place.name.contains("Kamal", ignoreCase = true) -> "Ratta Dynasty of Saundatti, with outstanding contributions by Jain merchants and administrators."
                place.name.contains("Kapileshwar", ignoreCase = true) -> "Supported by Ratta Dynasty, Devagiri Yadavas, and later Peshwa administrators."
                place.name.contains("Kopeshwara", ignoreCase = true) -> "Silahara Dynasty and Yadavas of Devagiri."
                place.name.contains("Military", ignoreCase = true) -> "Indian Army (Maratha Light Infantry Regimental Centre)."
                place.name.contains("Siddheshwar", ignoreCase = true) -> "Built and maintained by the local Kanbargi village community and devotees."
                place.name.contains("Yellamma", ignoreCase = true) -> "Rashtrakuta Dynasty, Chalukyas, and later Nayaka governors."
                else -> "Local Kadamba and Chalukya feudal chieftains."
            }
            list.add("👑 Dynasties / Builders" to builders)

            val arch = when {
                place.name.contains("Kamal", ignoreCase = true) -> "A supreme masterpiece of Later Chalukyan architecture built of fine black basalt stone. It features the famous Swarga Mandap (assembly hall) with 24 beautifully carved pillars and an extraordinary ceiling featuring an inverted, monolithic 72-petaled stone lotus dome."
                place.name.contains("Kapileshwar", ignoreCase = true) -> "Traditional Deccan stone temple style featuring a low-level Garbhagriha, a pillared Navaranga hall, and a sacred holy water tank (Kapila Teertha) adjacent to the main steps."
                place.name.contains("Kopeshwara", ignoreCase = true) -> "Ornate Late Chalukyan / Hoysala transition style built of hard stone. Features the Swarga Mandap (a circular open-to-sky hall supported by 48 intricately carved pillars), massive elephant-frieze plinths, and highly detailed wall sculptures of deities."
                place.name.contains("Military", ignoreCase = true) -> "Modern North Indian temple architecture featuring a clean white marble sanctum, a high shikhar tower, and exceptionally neat paved surroundings."
                place.name.contains("Siddheshwar", ignoreCase = true) -> "A unique natural rock-cave temple. Features a simple modern South Indian style gopura at the entrance, while the main deity sits inside a natural cave chamber."
                place.name.contains("Yellamma", ignoreCase = true) -> "Built in a beautiful blend of Chalukyan and Rashtrakuta styles. Features high fortified stone walls, ornate stone gateways, carved pillars, and a sacred holy pool (Jogula Bhavi) nearby."
                else -> "Kadamba-Nagara and Dravidian style stone masonry, characteristic of medieval northern Karnataka borderlands."
            }
            list.add("🏛️ Architectural Design & Heritage" to arch)

            val importance = when {
                place.name.contains("Kamal", ignoreCase = true) -> "An important Digambara Jain pilgrimage site in Northern Karnataka, representing the heights of medieval Jaina influence and cultural harmony in the region."
                place.name.contains("Kapileshwar", ignoreCase = true) -> "Highly sacred pilgrimage center. According to local belief, a pilgrimage to the twelve Jyotirlingas of India is considered incomplete without seeking blessings at this temple first."
                place.name.contains("Kopeshwara", ignoreCase = true) -> "Unique among Shiva temples because it lacks a Nandi statue at the entrance (as Nandi stayed to console Parvati) and integrates Vaishnavite and Shaivite traditions seamlessly."
                place.name.contains("Military", ignoreCase = true) -> "A unique bridge of friendship between the military personnel and the civil population of Belagavi, known for its absolute peace, cleanliness, and order."
                place.name.contains("Siddheshwar", ignoreCase = true) -> "Highly popular local spiritual retreat where people visit to escape urban noise and seek mental peace."
                place.name.contains("Yellamma", ignoreCase = true) -> "An extremely sacred Shakti Peetha, considered the family deity by millions of rural families in Karnataka, Maharashtra, and Goa."
                else -> "An active community temple promoting regional harmony, traditional spiritual values, and local folk beliefs."
            }
            list.add("🏺 Sacred & Cultural Importance" to importance)

            val rituals = when {
                place.name.contains("Kamal", ignoreCase = true) -> "Mahavir Jayanti, annual Panchamruta Abhishek, Diwali Deepotsava, and special prayers on full moon days."
                place.name.contains("Kapileshwar", ignoreCase = true) -> "Maha Shivaratri (featuring night-long continuous milk abhishek and chariot processions), Shravan Mondays, and Karthika Deepotsava."
                place.name.contains("Kopeshwara", ignoreCase = true) -> "Maha Shivaratri, annual river pujas, and special astronomical viewing days during full moons."
                place.name.contains("Military", ignoreCase = true) -> "Maha Shivaratri, Shravan Mondays, and special commemorative prayers on Army Day and Kargil Vijay Diwas."
                place.name.contains("Siddheshwar", ignoreCase = true) -> "Special Shravana Mondays, annual Kanbargi village fair, and Shivaratri abhishek."
                place.name.contains("Yellamma", ignoreCase = true) -> "The legendary Saundatti Yellamma Jatre held on Banada Hunnime (Dec/Jan), drawing over a million pilgrims. Daily Jogula and Aarati rituals."
                else -> "Daily morning and evening Aaratis, and annual community chariot festival (Rathotsava)."
            }
            list.add("🎉 Festivals & Rituals" to rituals)

            val timings = when {
                place.name.contains("Kamal", ignoreCase = true) -> "8:00 AM to 6:00 PM daily. Respectful attire is required inside the sanctum."
                place.name.contains("Kapileshwar", ignoreCase = true) -> "5:00 AM to 9:00 PM daily. Long queues are common on Shravan Mondays and holidays."
                place.name.contains("Kopeshwara", ignoreCase = true) -> "6:00 AM to 8:00 PM daily. Hiring an ASI guide is highly recommended."
                place.name.contains("Military", ignoreCase = true) -> "6:00 AM to 8:00 PM daily. Strict discipline and neatness must be maintained."
                place.name.contains("Siddheshwar", ignoreCase = true) -> "6:00 AM to 7:00 PM daily. Requires climbing a moderate flight of stone steps."
                place.name.contains("Yellamma", ignoreCase = true) -> "5:00 AM to 10:00 PM. Highly crowded on Tuesdays, Fridays, and full moon nights."
                else -> "6:00 AM to 8:00 PM daily."
            }
            list.add("⏱️ Temple Timings / Visitor Notes" to timings)
        }

        category == "fort" || category == "building" || place.name.contains("Fort", ignoreCase = true) -> {
            val about = when {
                place.name.contains("Belagavi", ignoreCase = true) -> "An extraordinary oval land fort in the heart of Belagavi, featuring a deep defensive moat, massive stone ramparts, and a rich history of multi-dynastic conquests."
                place.name.contains("Kittur", ignoreCase = true) -> "The historic stronghold that served as the capital of the Kittur Desai principality, immortalized as the battlefield of Queen Chennamma's historic 1824 rebellion."
                place.name.contains("Parasgad", ignoreCase = true) -> "A rugged and ancient hilltop fortress built of irregular stone blocks near Saundatti, offering majestic valley overlooks."
                place.name.contains("Ramdurga", ignoreCase = true) -> "A grand Maratha hill fortress with thick stone walls and watchtowers, offering scenic panoramic views over the Ramdurg valley."
                place.name.contains("Yellur", ignoreCase = true) -> "Also known as Rajhansgad, this stunning hilltop fortress offers an absolute 360-degree panoramic overlook of the Belagavi countryside."
                place.name.contains("Vidhana", ignoreCase = true) -> "Suvarna Vidhana Soudha is the majestic legislative assembly building of Karnataka in Belagavi, built as a monument to decentralization."
                else -> "A prominent historical fortification in the Belagavi district, representing medieval defense strategy."
            }
            list.add("🏰 About Fort" to about)

            val timeline = when {
                place.name.contains("Belagavi", ignoreCase = true) -> "• 1204 AD: Built by Ratta Dynasty chieftain Jaya Raya using mud and stone.\n• 1519 AD: Massively fortified with stone walls and a moat by Yakub Adil Shah of Bijapur.\n• 1686 AD: Captured by Mughal Emperor Aurangzeb.\n• 1770s: Held by Hyder Ali and later integrated into the Maratha Empire.\n• 1818 AD: Annexed by the British East India Company after a 21-day siege."
                place.name.contains("Kittur", ignoreCase = true) -> "• 1650s: Construction initiated by Kittur Desai dynasty founder Allappa Gowda Sardesaim.\n• 1824 AD: Witnessed the legendary armed rebellion led by Kittur Rani Chennamma against the British Doctrine of Lapse.\n• 1825 AD: Captured and heavily dismantled by British forces after the second siege."
                place.name.contains("Parasgad", ignoreCase = true) -> "• 10th Century: Built by the early Ratta Dynasty to control the Saundatti plains.\n• 1674 AD: Extensively reinforced and garrisoned by Chhatrapati Shivaji Maharaj.\n• 18th Century: Held by Maratha Sardars and later abandoned during British annexation."
                place.name.contains("Ramdurga", ignoreCase = true) -> "• 17th Century: Built as a strategic outpost by Maratha commanders.\n• 1753 AD: Became the administrative seat of the Ramdurg Princely State under the Bhave family.\n• 1948 AD: Merged into the Indian Union post-independence."
                place.name.contains("Yellur", ignoreCase = true) -> "• 12th Century: Initially established as a watchtower by the Ratta dynasty.\n• 17th Century: Rebuilt and fortified with stone ramparts under the Maratha administration.\n• 1818 AD: Briefly occupied by the British forces during the fall of the Peshwas."
                place.name.contains("Vidhana", ignoreCase = true) -> "• 2007: Foundation stone laid by the Government of Karnataka.\n• 2012: Inaugurated by President Pranab Mukherjee.\n• Modern: Hosts the annual winter session of the Karnataka State Legislature."
                else -> "• 13th Century: Constructed under local feudal rule.\n• 17th Century: Integrated into regional Maratha defenses."
            }
            list.add("📅 Historical Timeline" to timeline)

            val builders = when {
                place.name.contains("Belagavi", ignoreCase = true) -> "Ratta Dynasty, Adil Shahis of Bijapur, Mughals, Marathas, British."
                place.name.contains("Kittur", ignoreCase = true) -> "Kittur Desai Dynasty (vassals of Adil Shahis and later Marathas), British Empire."
                place.name.contains("Parasgad", ignoreCase = true) -> "Ratta Dynasty, Adil Shahis, Maratha Empire (Shivaji Maharaj)."
                place.name.contains("Ramdurga", ignoreCase = true) -> "Maratha Empire, Bhave Dynasty (ruler of Ramdurg Princely State)."
                place.name.contains("Yellur", ignoreCase = true) -> "Ratta Dynasty, Peshwas, Marathas (Rajhans family)."
                place.name.contains("Vidhana", ignoreCase = true) -> "Democratic Government of Karnataka."
                else -> "Local dynasties and regional governors."
            }
            list.add("👑 Dynasties & Rulers" to builders)

            val battles = when {
                place.name.contains("Belagavi", ignoreCase = true) -> "The Siege of Belgaum (1818) by British forces, and the brief imprisonment of Mahatma Gandhi here in 1924 during the Belgaum Congress session."
                place.name.contains("Kittur", ignoreCase = true) -> "The historic Battle of Kittur (October 1824) where Rani Chennamma's forces defeated the British and killed Dharwad political agent John Thackeray."
                place.name.contains("Parasgad", ignoreCase = true) -> "Used as a strategic Maratha military outpost to defend against Mughal expansions and local chieftains."
                place.name.contains("Ramdurga", ignoreCase = true) -> "The fort defended the Ramdurg principality against local Nawabs and Hyder Ali's forces."
                place.name.contains("Yellur", ignoreCase = true) -> "Served as a vital military watchtower during the Anglo-Maratha wars, monitoring troop movements approaching Belagavi city."
                place.name.contains("Vidhana", ignoreCase = true) -> "Symbolizes the administrative integration and developmental focus on Northern Karnataka."
                else -> "Fought over during medieval transitions between regional sultans, local Desais, and Maratha forces."
            }
            list.add("💥 Battles & Historical Events" to battles)

            val arch = when {
                place.name.contains("Belagavi", ignoreCase = true) -> "Oval land fort constructed using heavy volcanic basalt. Features double ramparts, a massive 100-foot wide protective water moat, large circular bastions with gun-slits, and complex zigzag defensive gates."
                place.name.contains("Kittur", ignoreCase = true) -> "Built in the Peshwa-Islamic fusion style of black basalt stone. Features a triple-layer fortification system, a majestic multi-storeyed palace (now in ruins), and high watchtowers."
                place.name.contains("Parasgad", ignoreCase = true) -> "Hill-top fort constructed using heavy irregular stone blocks without mortar (dry stone masonry). Features steep rocky cliffs, high watchtowers, and a deep rock-cut water tank (Ramtirth)."
                place.name.contains("Ramdurga", ignoreCase = true) -> "Built in classic Maratha military style, featuring sloping stone bastions, massive gun platforms, and defensive trenches carved directly into the hilltop rock."
                place.name.contains("Yellur", ignoreCase = true) -> "A compact, highly well-preserved hill fort built of local laterite and granite blocks. Features high crenellated stone walls, circular bastions, a beautiful temple dedicated to Lord Shiva inside, and a historic sweet-water well."
                place.name.contains("Vidhana", ignoreCase = true) -> "Ornate neo-Dravidian architecture inspired by the Bengaluru Vidhana Soudha. Features a massive central dome, Greek-style pillars, and elaborate stone carvings."
                else -> "Rugged stone fortifications utilizing local laterite and granite blocks."
            }
            list.add("🏛️ Architectural Design & Heritage" to arch)

            val strategic = when {
                place.name.contains("Belagavi", ignoreCase = true) -> "Guarded the primary trade and military routes connecting the Deccan Plateau to the Arabian Sea ports of Goa and Konkan."
                place.name.contains("Kittur", ignoreCase = true) -> "Strategically positioned to control the wealthy commercial trade routes between Hubli, Belagavi, and the Goa coast."
                place.name.contains("Parasgad", ignoreCase = true) -> "Rising 300 meters above the plains, it provided absolute surveillance over the crucial Saundatti-Hubli highway and trade pass."
                place.name.contains("Ramdurga", ignoreCase = true) -> "Positioned atop a rocky hill range to control the northern border passes of the Malaprabha River valley."
                place.name.contains("Yellur", ignoreCase = true) -> "Its high elevation provides complete visual dominance over the entire southern Belagavi valley and approaching roads."
                place.name.contains("Vidhana", ignoreCase = true) -> "Acts as the administrative power center for Northern Karnataka."
                else -> "Built on high ground to overlook vital road networks."
            }
            list.add("🛡️ Strategic Importance" to strategic)

            val layout = when {
                place.name.contains("Belagavi", ignoreCase = true) -> "Land-based fort, oval in shape, covering over 100 acres, surrounded by a deep protective moat, housing Kamal Basadi, Ramakrishna Mission, Safa Masjid, and ancient garrison quarters."
                place.name.contains("Kittur", ignoreCase = true) -> "Land-based fort spanning a moderate area, featuring a complex palace layout, deep water cisterns, administrative quarters, and a dedicated armory hall."
                place.name.contains("Parasgad", ignoreCase = true) -> "Hill fort, long and narrow along a rocky ridge, featuring high perimeter walls, ruined garrison barracks, and a sacred cave shrine."
                place.name.contains("Ramdurga", ignoreCase = true) -> "Hill fort, occupying the summit of a rocky plateau with robust walls, watchtowers, and an administrative durbar chamber."
                place.name.contains("Yellur", ignoreCase = true) -> "Circular hilltop fort, featuring a single large gate, a paved perimeter walkway, a central Shiva temple, and a deep reservoir."
                place.name.contains("Vidhana", ignoreCase = true) -> "Sprawling 127-acre campus along the NH48 highway, featuring a massive assembly hall, cabinet chambers, and administrative offices."
                else -> "Hilltop fortification featuring strong gates, inner bastions, and barracks."
            }
            list.add("🗺️ Area & Layout" to layout)

            val heritage = when {
                place.name.contains("Belagavi", ignoreCase = true) -> "A beautiful symbol of religious syncretism, containing Jaina, Hindu, Islamic, and Christian heritage structures inside a single fortress."
                place.name.contains("Kittur", ignoreCase = true) -> "Revered across India as the cradle of early armed resistance against British colonial rule, representing regional pride and courage."
                place.name.contains("Parasgad", ignoreCase = true) -> "Associated with the military genius of Shivaji Maharaj and offers panoramic views of the Yellamma hills."
                place.name.contains("Ramdurga", ignoreCase = true) -> "Represents the administrative and military heritage of the Ramdurg Princely State."
                place.name.contains("Yellur", ignoreCase = true) -> "A beloved local trekking landmark, representing the military architecture of the Maratha watchposts."
                place.name.contains("Vidhana", ignoreCase = true) -> "A modern heritage monument showcasing the state's architectural continuity and administrative unity."
                else -> "An important regional heritage site that serves as a tangible link to medieval history."
            }
            list.add("🏺 Cultural/Heritage Importance" to heritage)
        }

        category == "waterfall" -> {
            val about = when {
                place.name.contains("Godchinamalaki", ignoreCase = true) -> "A beautiful, terraced two-tiered cascade hidden inside a rocky valley, away from the commercial crowds of Gokak."
                place.name.contains("Gokak", ignoreCase = true) -> "Famed as the 'Niagara of India', this magnificent horseshoe-shaped plunge waterfall drops over high red sandstone cliffs."
                place.name.contains("Jalavane", ignoreCase = true) -> "A pristine, seasonal waterfall hidden deep in the dense forests of the Mhadei/Bhimgad wildlife corridor."
                place.name.contains("Jamboti", ignoreCase = true) -> "A beautiful, family-friendly cascade surrounded by lush forest vegetation and rocky pools near Jamboti village."
                place.name.contains("Sada", ignoreCase = true) -> "An adventurous, volcanic canyon waterfall hidden deep in the forest near the Goa border, requiring a scenic trek."
                place.name.contains("Surala", ignoreCase = true) -> "A breathtaking seasonal cascade that plunges down a sheer rock cliff into the V-shaped Sural Valley canyon."
                place.name.contains("Vajrashkala", ignoreCase = true) || place.name.contains("Vajrapoha", ignoreCase = true) -> "A spectacular, wild waterfall on the Mandovi River, plunging down a sheer 200-foot basalt cliff face in absolute solitude."
                else -> "A pristine natural waterfall nestled in the Western Ghats region of Belagavi, fed by rich seasonal rainfall."
            }
            list.add("🌊 About Waterfall" to about)

            val source = when {
                place.name.contains("Godchinamalaki", ignoreCase = true) -> "Markandeya River."
                place.name.contains("Gokak", ignoreCase = true) -> "Ghataprabha River."
                place.name.contains("Jalavane", ignoreCase = true) -> "Local forest stream in the Mhadei River basin."
                place.name.contains("Jamboti", ignoreCase = true) -> "High altitude streams in the Mandovi River basin."
                place.name.contains("Sada", ignoreCase = true) -> "Streams originating in the high Western Ghats of Khanapur."
                place.name.contains("Surala", ignoreCase = true) -> "Sural river basin in the Western Ghats."
                place.name.contains("Vajrashkala", ignoreCase = true) || place.name.contains("Vajrapoha", ignoreCase = true) -> "Mandovi (Mahadayi) River."
                else -> "A local high-altitude stream in the Western Ghats."
            }
            list.add("💧 Water Source" to source)

            val height = when {
                place.name.contains("Godchinamalaki", ignoreCase = true) -> "Two-tiered cascade; the river first drops 25 meters over a rocky step, then slides 20 meters down a second volcanic tier."
                place.name.contains("Gokak", ignoreCase = true) -> "A massive 52-meter (170 feet) vertical sheer plunge."
                place.name.contains("Jalavane", ignoreCase = true) -> "Multi-tiered seasonal cascade sliding down green jungle slopes."
                place.name.contains("Jamboti", ignoreCase = true) -> "Seasonal cascading flow over natural rocky shelves."
                place.name.contains("Sada", ignoreCase = true) -> "A dramatic 200-foot single-plunge cascade carved through volcanic rock fissures."
                place.name.contains("Surala", ignoreCase = true) -> "Sheer plunge of over 300 feet into a deep forested valley."
                place.name.contains("Vajrashkala", ignoreCase = true) || place.name.contains("Vajrapoha", ignoreCase = true) -> "A magnificent 60-meter (200 feet) sheer vertical plunge."
                else -> "A beautiful cascade dropping between 20 to 50 meters."
            }
            list.add("📏 Height / Flow Type" to height)

            val season = "July to October (during the peak southwest monsoon)."
            list.add("📅 Best Season" to season)

            val difficulty = when {
                place.name.contains("Godchinamalaki", ignoreCase = true) -> "Easy to Moderate. A short walk through scenic rocky terrains."
                place.name.contains("Gokak", ignoreCase = true) -> "Easy. Directly accessible by road."
                place.name.contains("Jalavane", ignoreCase = true) -> "Moderate to Hard. Requires trekking through dense jungle paths."
                place.name.contains("Jamboti", ignoreCase = true) -> "Easy. A short walk along a forest path."
                place.name.contains("Sada", ignoreCase = true) -> "Hard. Requires a strenuous 4 km trek crossing streams."
                place.name.contains("Surala", ignoreCase = true) -> "Easy to reach the main viewpoint."
                place.name.contains("Vajrashkala", ignoreCase = true) || place.name.contains("Vajrapoha", ignoreCase = true) -> "Hard / Strenuous. Requires navigation through remote forests."
                else -> "Moderate."
            }
            list.add("🥾 Trek Difficulty" to difficulty)

            val safety = when {
                place.name.contains("Gokak", ignoreCase = true) -> "High safety fences are installed; do not cross them. The suspension bridge may have limited entry during heavy winds."
                else -> "Extremely slippery rocks. Swimming is strictly prohibited due to dangerous undercurrents and sudden rise in water level during monsoon."
            }
            list.add("🚨 Monsoon Safety" to safety)

            val photo = when {
                place.name.contains("Godchinamalaki", ignoreCase = true) -> "Use a slow shutter speed (0.5s - 2s) with a tripod to capture the silky terraced water flow. Best shot during morning golden hour."
                place.name.contains("Gokak", ignoreCase = true) -> "Capture the entire sweep of the fall from the hanging bridge or the old hydro-power viewpoint."
                else -> "Use a slow shutter speed and a polarizer filter to capture the vibrant greens of the wet forest canopy."
            }
            list.add("📸 Photography Tips" to photo)

            val nearby = when {
                place.name.contains("Gokak", ignoreCase = true) -> "Red sandstone gorges, historic temples, and Asia's oldest hydroelectric generation plant."
                else -> "Surrounded by dry deciduous scrub forest hills and rocky gorges."
            }
            list.add("🌳 Nearby Nature" to nearby)

            val ecology = when {
                place.name.contains("Godchinamalaki", ignoreCase = true) -> "Formed over ancient volcanic basaltic steps of the Deccan trap formation, which creates the unique terraced staircase flow."
                place.name.contains("Gokak", ignoreCase = true) -> "The river cuts through a narrow gorge of ancient Kaladgi sandstone rock formations, creating a dramatic canyon."
                else -> "Tucked inside the high-precipitation evergreen zone of the Western Ghats."
            }
            list.add("🌋 Ecology / Geography" to ecology)

            val travel = "Bring your own drinking water and snacks. Wear shoes with excellent grip."
            list.add("🚗 Travel Tips" to travel)
        }

        category == "nature" || category == "park" -> {
            val about = when {
                place.name.contains("Chorla", ignoreCase = true) -> "A spectacular high mountain pass snaking through the lush tri-junction of Karnataka, Goa, and Maharashtra, offering dramatic valleys and heavy fog."
                place.name.contains("Jamboti", ignoreCase = true) -> "A pristine high-altitude green watershed region celebrated as the geographic headwaters and primary source of the Mandovi River."
                place.name.contains("Kote Kere", ignoreCase = true) -> "A beautifully developed historic water moat encircling the Belagavi Fort, modernized into a peaceful urban lake park."
                else -> "A peaceful natural attraction in the Belagavi district, showcasing the rich green landscapes."
            }
            list.add("ℹ️ About" to about)

            val range = when {
                place.name.contains("Kote Kere", ignoreCase = true) -> "Belagavi City Plains."
                else -> "Sahyadri (Western Ghats) Range."
            }
            list.add("⛰️ Mountain Range / Region" to range)

            val elevation = when {
                place.name.contains("Chorla", ignoreCase = true) -> "Approximately 800 meters (2,620 feet) above sea level."
                place.name.contains("Jamboti", ignoreCase = true) -> "Approximately 750 meters above sea level."
                place.name.contains("Kote Kere", ignoreCase = true) -> "750 meters above sea level."
                else -> "Ranges between 700 to 900 meters above sea level."
            }
            list.add("📈 Elevation" to elevation)

            val climate = when {
                place.name.contains("Kote Kere", ignoreCase = true) -> "Pleasant, moderate city weather."
                else -> "Cool, humid, and pleasant. Rains are exceptionally heavy during the monsoon, covering the ghats in dense fog."
            }
            list.add("🌦️ Climate" to climate)

            val forest = when {
                place.name.contains("Kote Kere", ignoreCase = true) -> "Developed municipal garden, lawns, and urban wetland trees."
                else -> "Tropical wet evergreen and semi-evergreen forest canopy."
            }
            list.add("🌲 Forest Type" to forest)

            val flora = when {
                place.name.contains("Kote Kere", ignoreCase = true) -> "Migratory winter ducks, kingfishers, egrets, and domestic garden flora."
                else -> "Home to endangered Nilgiri wood-pigeons, Malabar giant squirrels, wild leopards, tree frogs, and diverse orchids."
            }
            list.add("🐾 Flora & Fauna" to flora)

            val diff = "Easy (mostly paved flat walking pathways or roadside view stops)."
            list.add("🥾 Trek Difficulty" to diff)

            val points = when {
                place.name.contains("Kote Kere", ignoreCase = true) -> "Sunset lakeside deck, views of the ancient fort ramparts."
                else -> "Chorla Valley overlook, sunset ridges, and various roadside seasonal waterfalls."
            }
            list.add("🌅 Scenic Viewpoints" to points)

            val acts = when {
                place.name.contains("Kote Kere", ignoreCase = true) -> "Pedal boating, high-speed boating, evening jogging, and enjoying the daily musical fountain shows."
                else -> "Misty driving, high-altitude trekking, birdwatching, and nature photography."
            }
            list.add("🏕️ Activities" to acts)

            val eco = when {
                place.name.contains("Kote Kere", ignoreCase = true) -> "An essential urban lung space that helps maintain the city's water table and provides refuge to urban birdlife."
                else -> "Part of the highly eco-sensitive Sahyadri range, recognized as a global UNESCO World Heritage biodiversity hotspot."
            }
            list.add("🔬 Ecology & Biodiversity" to eco)

            val photo = "Capture long exposure mist flow through valley gaps. Protect lenses from heavy humidity."
            list.add("📸 Photography Tips" to photo)

            val near = when {
                place.name.contains("Kote Kere", ignoreCase = true) -> "Belagavi Fort gardens and Military Mahadev parklands."
                else -> "Mhadei Wildlife Sanctuary, Anjunem Reservoir, and Sada village ruins."
            }
            list.add("🌳 Nearby Nature" to near)
        }

        category == "wildlife" || category == "forest" -> {
            val about = when {
                place.name.contains("Bhimagad", ignoreCase = true) -> "A highly protected, eco-sensitive forest reserve in the Western Ghats, famous as the exclusive global breeding ground of critically endangered bats."
                place.name.contains("Ghataprabha", ignoreCase = true) -> "A beautiful riverine wetland sanctuary along the Ghataprabha River, offering safe shelter to thousands of migrating birds."
                place.name.contains("Khanapur", ignoreCase = true) -> "A dense deciduous jungle corridor in the Western Ghats, serving as a vital natural passageway for tigers and elephants."
                place.name.contains("Tilari", ignoreCase = true) -> "A dense, evergreen wildlife corridor bordering Maharashtra, famous for its rich biodiversity and active elephant corridors."
                else -> "A vital protected wilderness reserve in the Belagavi region."
            }
            list.add("ℹ️ About" to about)

            val ecoType = when {
                place.name.contains("Ghataprabha", ignoreCase = true) -> "Riparian wetlands, shallow river banks, and scrub forests."
                else -> "Tropical semi-evergreen and moist deciduous forest canopy."
            }
            list.add("🌳 Ecosystem Type" to ecoType)

            val species = when {
                place.name.contains("Bhimagad", ignoreCase = true) -> "Wroughton's Free-tailed Bat (critically endangered and endemic to this area), Bengal Tiger, and Indian Gaur."
                place.name.contains("Ghataprabha", ignoreCase = true) -> "Migratory Demoiselle Cranes and European White Storks."
                place.name.contains("Khanapur", ignoreCase = true) -> "Indian Elephant, Bengal Tiger, and Gaur."
                place.name.contains("Tilari", ignoreCase = true) -> "Asian Elephant, Tiger, and Malabar Giant Squirrel."
                else -> "Indian Gaur, Leopard, and spotted deer."
            }
            list.add("🐆 Protected Species" to species)

            val flora = when {
                place.name.contains("Ghataprabha", ignoreCase = true) -> "Riparian reeds, acacia trees, local fish species, otters, and marsh birds."
                else -> "Majestic rosewood, teak, wild cinnamon, flying squirrels, pangolins, and rare forest reptiles."
            }
            list.add("🐾 Flora & Fauna" to flora)

            val wildlife = when {
                place.name.contains("Bhimagad", ignoreCase = true) -> "Sloth bear, leopards, wild dogs (dholes), and the elusive black panther."
                place.name.contains("Ghataprabha", ignoreCase = true) -> "Smooth-coated otters, jackals, and mongoose."
                place.name.contains("Khanapur", ignoreCase = true) -> "Migrating elephant herds, leopards, and barking deer."
                place.name.contains("Tilari", ignoreCase = true) -> "Wild elephant herds, gaurs (bison), and leopards."
                else -> "Spotted deer, langurs, and wild boars."
            }
            list.add("🐅 Famous Wildlife" to wildlife)

            val birds = when {
                place.name.contains("Ghataprabha", ignoreCase = true) -> "Demoiselle Cranes, European Storks, Bar-headed Geese, Ibises, and Spoonbills."
                else -> "Malabar Grey Hornbill, Emerald Dove, Malabar Trogon, and Sunbirds."
            }
            list.add("🐦 Birdwatching" to birds)

            val conservation = when {
                place.name.contains("Ghataprabha", ignoreCase = true) -> "Protects critical wintering grounds for trans-Himalayan migrating bird species."
                else -> "Crucial wildlife corridor connecting Karnataka's forests with Goa's Bhagwan Mahaveer Sanctuary."
            }
            list.add("🛡️ Conservation Importance" to conservation)

            val eco = when {
                place.name.contains("Ghataprabha", ignoreCase = true) -> "Riverside walking trails, birdwatching decks near the river banks."
                else -> "Limited, controlled safari drives and guided forest treks."
            }
            list.add("🚶 Eco Tourism" to eco)

            val safety = "Obtain mandatory forest permits. Do not enter core reserve zones without guards. Stay inside vehicles."
            list.add("🚨 Safety Guidelines" to safety)

            val bestSeason = when {
                place.name.contains("Ghataprabha", ignoreCase = true) -> "November to February (peak migration window)."
                else -> "October to March (post-monsoon winter months)."
            }
            list.add("⏱️ Best Visiting Season" to bestSeason)

            val photo = "Use telephoto lenses (300mm+) for wildlife. Obtain permission for photography."
            list.add("📸 Photography & Trek Advice" to photo)
        }

        category == "reservoir" -> {
            val about = when {
                place.name.contains("Hidkal", ignoreCase = true) -> "A massive reservoir built across the Ghataprabha River, offering spectacular backwater views and seasonal bird watching."
                place.name.contains("Naviltirtha", ignoreCase = true) -> "A spectacular deep gorge dam built across the Malaprabha River, famous for the scenic rocky cliffs that host a peacock sanctuary."
                place.name.contains("Rakaskop", ignoreCase = true) -> "A beautiful drinking-water dam built on the Markandeya River, surrounded by peaceful forest hills and local myths."
                else -> "A scenic reservoir in the Belagavi district, serving crucial municipal and irrigation purposes."
            }
            list.add("ℹ️ About" to about)

            val source = when {
                place.name.contains("Hidkal", ignoreCase = true) -> "Ghataprabha River (Krishna River Basin)."
                place.name.contains("Naviltirtha", ignoreCase = true) -> "Malaprabha River (Krishna River Basin)."
                place.name.contains("Rakaskop", ignoreCase = true) -> "Markandeya River (Ghataprabha Basin)."
                else -> "Local river systems."
            }
            list.add("💧 Water Source / River Basin" to source)

            val capacity = when {
                place.name.contains("Hidkal", ignoreCase = true) -> "Storage capacity of approximately 51 TMC, used for irrigation, drinking water, and hydro-power."
                place.name.contains("Naviltirtha", ignoreCase = true) -> "Multi-purpose irrigation reservoir and drinking water supply dam."
                place.name.contains("Rakaskop", ignoreCase = true) -> "Drinking water reservoir exclusively catering to the municipal needs of Belagavi City."
                else -> "Vast water storage facility."
            }
            list.add("⚙️ Capacity / Purpose" to capacity)

            val irrigation = when {
                place.name.contains("Rakaskop", ignoreCase = true) -> "Focuses primarily on municipal water supply rather than large-scale crop irrigation."
                place.name.contains("Hidkal", ignoreCase = true) -> "The primary agricultural lifeline irrigating lakhs of acres of sugarcane, paddy, and cotton lands."
                else -> "Essential water lifeline for dry agricultural belt of Dharwad, Gadag, and Belagavi districts."
            }
            list.add("🌾 Irrigation Importance" to irrigation)

            val scenic = when {
                place.name.contains("Naviltirtha", ignoreCase = true) -> "Breathtaking narrow rocky gorge where high cliffs rise on both sides of the river, forming a natural canyon."
                else -> "Sprawling tranquil water sheet flanked by low rocky hills."
            }
            list.add("🌅 Scenic Value" to scenic)

            val sunset = "Outstanding sunset views from the primary embankment dyke."
            list.add("🌅 Sunset / Sunrise Points" to sunset)

            val boating = when {
                place.name.contains("Hidkal", ignoreCase = true) -> "Local fishing active in the backwaters; boating near the main dam wall is strictly prohibited."
                else -> "Strictly restricted due to dangerous depth."
            }
            list.add("⛵ Boating / Fishing" to boating)

            val season = "September to February (post-monsoon peak water level)."
            list.add("📅 Best Season" to season)

            val near = when {
                place.name.contains("Hidkal", ignoreCase = true) -> "Hukkeri town shrines and Gokak Falls."
                place.name.contains("Naviltirtha", ignoreCase = true) -> "Saundatti Yellamma Temple and Parasgad Fort."
                else -> "The legendary Rakshasa Cave on the nearby hillside and the scenic hanging bridge."
            }
            list.add("📍 Nearby Attractions" to near)
        }

        else -> {
            list.add("📅 Historical Timeline" to "• Ancient Era: Snaked by historic trade paths connecting the Deccan plateau with the Konkan coast.\n• 19th Century: Scientific surveys and wildlife mapping under British botanists.")
            list.add("🏛️ Architectural Style" to "Built using regional materials matching Chalukya and Kadamba traditions, designed for maximum functionality.")
            list.add("🌟 Cultural Importance" to "A valuable local historical landmark, representing the rich cultural diversity, historical struggles, and heritage of Belagavi region.")
            list.add("🔧 Restoration & Conservation" to "Maintained and protected by state historical societies and municipal departments to preserve it for future generations.")
        }
    }

    return list
}
