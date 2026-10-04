package com.belagavi.tourism.ui.dashboard

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.belagavi.tourism.data.model.Place
import com.belagavi.tourism.data.repository.PlacesRepository
import com.belagavi.tourism.ui.auth.AuthViewModel
import com.belagavi.tourism.ui.theme.BorderLight
import com.belagavi.tourism.ui.theme.CreamBackground
import com.belagavi.tourism.ui.theme.CreamWarm
import com.belagavi.tourism.ui.theme.ForestPale
import com.belagavi.tourism.ui.theme.ForestPrimary
import com.belagavi.tourism.ui.theme.InkMuted
import com.belagavi.tourism.ui.theme.InkPrimary
import com.belagavi.tourism.ui.theme.InkSecondary
import com.belagavi.tourism.ui.theme.WhiteSurface
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.Calendar

@Composable
fun HomeView(
    authViewModel: AuthViewModel,
    placesRepository: PlacesRepository,
    onNavigateToExplore: () -> Unit,
    onNavigateToExploreCategory: (String) -> Unit = {},
    onNavigateToPlaceDetail: (Int) -> Unit
) {
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()
    val uid = currentUser?.uid
    val db = remember { FirebaseFirestore.getInstance() }

    val allPlaces = remember { placesRepository.getPlaces() }
    val userName = currentUser?.displayName ?: currentUser?.email?.split('@')?.firstOrNull() ?: "Explorer"

    // 10 Belagavi facts for "Did You Know?"
    val belagaviFacts = remember {
        listOf(
            "Belagavi Fort was constructed in 1204 AD by Bichiraja of the Ratta dynasty, guarded by massive stone ramparts and a deep protective moat.",
            "Gokak Falls is renowned as the 'Niagara of Karnataka', plunging 52 meters across a horseshoe-shaped sandstone cliff on the Ghataprabha River.",
            "Belagavi hosted the historic 39th session of the Indian National Congress in 1924 — the only Congress session ever presided over by Mahatma Gandhi.",
            "Inside Belagavi Fort, Kamal Basti features a magnificent domed ceiling carved into an exquisite 72-petaled stone lotus symbolizing 24 Jain Tirthankaras.",
            "The iconic sweet 'Belgaum Kunda' originated in 1951 when Jakku Maruthi Savant accidentally slow-cooked whole milk with khowa and sugar into caramelized gold.",
            "Bhimgad Wildlife Sanctuary is the world's only known natural breeding habitat for the critically endangered Wroughton’s Free-tailed Bat.",
            "The 201-meter historic pedestrian wire-rope suspension bridge over Gokak Falls was built by British engineers in 1907 and is still operational today.",
            "Sada Falls cascades near the border of Karnataka, Goa, and Maharashtra through prehistoric volcanic caves and dramatic deep gorges.",
            "Rajhansgad Fort (Yellur Fort) sits perched at 2,500 feet atop Yellur Hill, offering an unparalleled 360-degree vantage point across the Belagavi valley.",
            "Godchinamalaki Falls cascades in a rare and scenic two-tiered staircase drop of 25 meters and 20 meters along the emerald green Markandeya River."
        )
    }

    var currentFactIndex by remember { mutableStateOf((0..9).random()) }

    // Live wishlist tracking
    var wishlistedPlaceIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    LaunchedEffect(key1 = uid) {
        if (uid != null) {
            db.collection("wishlist")
                .whereEqualTo("user_id", uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        wishlistedPlaceIds = snapshot.documents.mapNotNull { doc ->
                            doc.getLong("placeId")?.toInt() ?: doc.id.split("_").lastOrNull()?.toIntOrNull()
                        }.toSet()
                    }
                }
        } else {
            wishlistedPlaceIds = emptySet()
        }
    }

    // Live Weather State for Belagavi (lat: 15.8497, lon: 74.4977)
    var liveTemp by remember { mutableStateOf<Double?>(null) }
    var weatherAlert by remember { mutableStateOf("Conditions are optimal for outdoor sightseeing today!") }
    var weatherBadge by remember { mutableStateOf("Perfect weather for outdoors") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=15.8497&longitude=74.4977&current_weather=true")
                val jsonStr = url.readText()
                val json = JSONObject(jsonStr)
                if (json.has("current_weather")) {
                    val cw = json.getJSONObject("current_weather")
                    val temp = cw.optDouble("temperature", 24.0)
                    val code = cw.optInt("weathercode", 0)

                    val (alert, badge) = when {
                        code in listOf(51, 53, 55, 61, 63, 65, 80, 81) ->
                            "Light rain detected in ghat areas. Carry rain gear for waterfall trails." to "Light rain alert"
                        code in listOf(65, 67, 82, 95, 96, 99) ->
                            "Heavy rain active. Access roads to waterfalls may be slippery. Postpone steep hikes." to "Rain alert"
                        temp >= 33.0 ->
                            "High afternoon temperatures. Carry hydration and plan outdoor visits before noon or after 4 PM." to "Warm sunny day"
                        else ->
                            "Pleasant and clear conditions across Belagavi district today." to "Great for sightseeing"
                    }

                    withContext(Dispatchers.Main) {
                        liveTemp = temp
                        weatherAlert = alert
                        weatherBadge = badge
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    liveTemp = 24.0
                    weatherBadge = "Pleasant weather"
                    weatherAlert = "Pleasant conditions for sightseeing in Belagavi."
                }
            }
        }
    }

    // Smart Day Suggestion (Morning / Afternoon / Evening)
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val (dayPlanTitle, dayPlanDesc, dayPlanPlaces) = remember(currentHour) {
        when {
            currentHour < 12 -> Triple(
                "Morning Heritage & Fort Walk",
                "Cool morning breeze is perfect to explore Belagavi Fort ramparts and the intricate 72-petal lotus ceiling of Kamal Basti.",
                listOf("Belagavi Fort", "Kamal Basti", "Kote Kere Lake")
            )
            currentHour < 17 -> Triple(
                "Afternoon Gastronomy & Culture",
                "Escape peak midday sun with authentic hot Belgaum Kunda at Khade Bazar, then visit the magnificent Suvarna Vidhana Soudha.",
                listOf("Khade Bazar", "Camp Area", "Vidhana Soudha")
            )
            else -> Triple(
                "Golden Hour & Sunset Vistas",
                "Catch the breathtaking twilight panorama from Rajhansgad (Yellur Fort) or relax along the landscaped promenade of Fort Lake.",
                listOf("Rajhansgad Fort", "Fort Lake", "Military Mahadeva")
            )
        }
    }

    // Featured destination spotlights
    val spotlightPlaces = remember(allPlaces) {
        allPlaces.filter { it.id in listOf(1, 4) }.ifEmpty { allPlaces.take(2) }
    }

    // 6 Curated hero images in exact specified order
    val heroImages = remember {
        listOf(
            "https://belagavi-tourism-planner.web.app/static/images/jalavane_falls/1.jpg",
            "https://belagavi-tourism-planner.web.app/static/images/godachimalaki_falls/1.jpg",
            "https://belagavi-tourism-planner.web.app/static/images/khanapur_forest/1.jpg",
            "https://belagavi-tourism-planner.web.app/static/images/chorla_ghat/1.jpg",
            "https://belagavi-tourism-planner.web.app/static/images/gokak/1.jpg",
            "https://belagavi-tourism-planner.web.app/static/images/kamalbasadi_belagavi/1.jpg"
        )
    }
    var currentHeroIndex by remember { mutableIntStateOf(0) }

    // Subtle automatic slideshow timer cycling every 30 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000)
            currentHeroIndex = (currentHeroIndex + 1) % heroImages.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
            .verticalScroll(rememberScrollState())
    ) {
        // ── 0. HERO IMAGE BANNER (SUBSTANTIALLY TALLER + 30S CROSSFADE SLIDESHOW) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
        ) {
            Crossfade(
                targetState = currentHeroIndex,
                animationSpec = tween(durationMillis = 1000),
                modifier = Modifier.fillMaxSize()
            ) { index ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(heroImages[index])
                        .crossfade(true)
                        .build(),
                    contentDescription = "Belagavi Highlights",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // gradient overlay for text legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.60f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    text = "KARNATAKA'S NORTHERN CROWN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Text(
                    text = "Explore Belagavi",
                    fontFamily = FontFamily.Serif,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 32.sp
                )
            }
        }

        // ── 1. COMPACT PERSONALIZED HERO HEADER ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(CreamWarm, CreamBackground)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NAMASKARA,",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            color = InkMuted
                        )
                        Text(
                            text = userName,
                            fontFamily = FontFamily.Serif,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkPrimary
                        )
                    }

                    // Direct Explore CTA
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = ForestPrimary,
                        modifier = Modifier.clickable { onNavigateToExplore() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Explore",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Discover historic forts, cascading falls, and ancient sacred sanctuaries across Belagavi.",
                    fontSize = 13.sp,
                    color = InkSecondary,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // ── 2. "DID YOU KNOW?" BELAGAVI FACTS CARD ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { currentFactIndex = (currentFactIndex + 1) % belagaviFacts.size },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                border = BorderStroke(1.dp, BorderLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(WhiteSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = "Did You Know",
                                    tint = ForestPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DID YOU KNOW?",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = InkPrimary
                            )
                        }
                        // Tap card to reveal next fact
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = belagaviFacts[currentFactIndex],
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Normal,
                        color = InkPrimary
                    )
                }
            }
        }

        // ── 3. LIVE WEATHER & REGIONAL ADVISORY ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                border = BorderStroke(1.dp, BorderLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ForestPale),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Weather",
                            tint = ForestPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Belagavi Forecast",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                color = InkMuted
                            )
                            if (liveTemp != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• ${"%.0f".format(liveTemp)}°C",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InkPrimary
                                )
                            }
                        }
                        Text(
                            text = weatherBadge,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkPrimary,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                        Text(
                            text = weatherAlert,
                            fontSize = 11.sp,
                            color = InkSecondary,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // ── 4. "PLAN YOUR DAY" SMART SUGGESTION ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                border = BorderStroke(1.dp, BorderLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = ForestPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PLAN YOUR DAY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = InkMuted
                            )
                        }

                        Text(
                            text = if (currentHour < 12) "Morning" else if (currentHour < 17) "Afternoon" else "Evening",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = InkMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = dayPlanTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary
                    )

                    Text(
                        text = dayPlanDesc,
                        fontSize = 12.sp,
                        color = InkSecondary,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dayPlanPlaces.forEach { placeName ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = CreamWarm,
                                border = BorderStroke(0.5.dp, BorderLight)
                            ) {
                                Text(
                                    text = "📍 $placeName",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = InkPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 5. "QUICK EXPLORE" CATEGORY SHORTCUTS ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
        ) {
            Text(
                text = "QUICK EXPLORE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = InkMuted,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickCategoryCard(
                    modifier = Modifier.weight(1f),
                    icon = "💧",
                    title = "Waterfalls",
                    subtitle = "Gokak, Sada",
                    onClick = { onNavigateToExploreCategory("Waterfall") }
                )
                QuickCategoryCard(
                    modifier = Modifier.weight(1f),
                    icon = "🏰",
                    title = "Heritage",
                    subtitle = "Forts & Bastions",
                    onClick = { onNavigateToExploreCategory("Fort") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickCategoryCard(
                    modifier = Modifier.weight(1f),
                    icon = "🦁",
                    title = "Wildlife",
                    subtitle = "Bhimgad forest",
                    onClick = { onNavigateToExploreCategory("Wildlife") }
                )
                QuickCategoryCard(
                    modifier = Modifier.weight(1f),
                    icon = "🏞️",
                    title = "Scenic & Dams",
                    subtitle = "Lakes & reserves",
                    onClick = { onNavigateToExploreCategory("Reservoir") }
                )
            }
        }

        // ── 6. SPOTLIGHT DESTINATIONS ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FEATURED DESTINATIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = InkMuted
                    )
                    Text(
                        text = "Handpicked highlights for your visit",
                        fontSize = 12.sp,
                        color = InkMuted,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ForestPale,
                    border = BorderStroke(1.dp, ForestPrimary.copy(alpha = 0.25f)),
                    modifier = Modifier.clickable { onNavigateToExplore() }
                ) {
                    Text(
                        text = "View All →",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            spotlightPlaces.forEach { place ->
                SpotlightCard(
                    place = place,
                    onNavigateToPlaceDetail = { onNavigateToPlaceDetail(place.id) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // ── 7. BOTTOM EXPLORE ALL BUTTON ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = { onNavigateToExplore() },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForestPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Explore All Belagavi Destinations",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun QuickCategoryCard(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteSurface),
        border = BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 22.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InkPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = InkMuted
                )
            }
        }
    }
}

@Composable
private fun SpotlightCard(
    place: Place,
    onNavigateToPlaceDetail: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToPlaceDetail() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteSurface),
        border = BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://belagavi-tourism-planner.web.app/static/images/${place.folder_name}/1.jpg")
                    .crossfade(true)
                    .build(),
                contentDescription = place.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ForestPale,
                    border = BorderStroke(0.5.dp, ForestPrimary.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = place.category.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = place.name,
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = place.description,
                    fontSize = 11.sp,
                    color = InkSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Details",
                tint = ForestPrimary,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(16.dp)
            )
        }
    }
}
