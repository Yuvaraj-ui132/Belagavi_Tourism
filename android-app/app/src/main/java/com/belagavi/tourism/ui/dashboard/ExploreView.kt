package com.belagavi.tourism.ui.dashboard

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.belagavi.tourism.data.model.Place
import com.belagavi.tourism.data.repository.PlacesRepository
import com.belagavi.tourism.ui.theme.BorderLight
import com.belagavi.tourism.ui.theme.CreamBackground
import com.belagavi.tourism.ui.theme.CreamWarm
import com.belagavi.tourism.ui.theme.ForestPale
import com.belagavi.tourism.ui.theme.ForestPrimary
import com.belagavi.tourism.ui.theme.InkFaint
import com.belagavi.tourism.ui.theme.InkMuted
import com.belagavi.tourism.ui.theme.InkPrimary
import com.belagavi.tourism.ui.theme.InkSecondary
import com.belagavi.tourism.ui.theme.WhiteSurface

@Composable
fun ExploreView(
    placesRepository: PlacesRepository,
    selectedCategory: String = "All",
    onCategorySelected: (String) -> Unit = {},
    onNavigateToPlaceDetail: (Int) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val categories = listOf("All", "Fort", "Waterfall", "Wildlife", "Nature", "Reservoir", "Temple", "Forest", "Adventure")
    val places = remember { placesRepository.getPlaces() }

    val filteredPlaces = remember(selectedCategory, searchQuery) {
        places.filter { place ->
            val matchesCategory = if (selectedCategory.equals("All", ignoreCase = true)) {
                true
            } else if (selectedCategory.equals("Heritage", ignoreCase = true) || selectedCategory.equals("Fort", ignoreCase = true)) {
                place.category.equals("Fort", ignoreCase = true) || place.category.equals("Heritage", ignoreCase = true)
            } else if (selectedCategory.equals("Dam", ignoreCase = true) || selectedCategory.equals("Reservoir", ignoreCase = true)) {
                place.category.equals("Dam", ignoreCase = true) || place.category.equals("Reservoir", ignoreCase = true)
            } else {
                place.category.equals(selectedCategory, ignoreCase = true)
            }
            val matchesSearch = searchQuery.isBlank() ||
                    place.name.contains(searchQuery, ignoreCase = true) ||
                    place.category.contains(searchQuery, ignoreCase = true) ||
                    place.city.contains(searchQuery, ignoreCase = true) ||
                    place.description.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
    ) {
        // Web Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = "Explore Places",
                fontFamily = FontFamily.Serif,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = InkPrimary
            )
            Text(
                text = "Discover all ${places.size} destinations across Belagavi district",
                fontSize = 13.sp,
                color = InkMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Search Bar
        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search fort, waterfalls, temples...", color = InkMuted, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Icon",
                        tint = ForestPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = WhiteSurface,
                    unfocusedContainerColor = WhiteSurface,
                    focusedBorderColor = ForestPrimary,
                    unfocusedBorderColor = BorderLight,
                    focusedTextColor = InkPrimary,
                    unfocusedTextColor = InkPrimary
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Horizontal Category Filter Pills
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                val isSelected = selectedCategory.equals(category, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(40.dp),
                    color = if (isSelected) ForestPrimary else WhiteSurface,
                    border = BorderStroke(1.dp, if (isSelected) ForestPrimary else BorderLight),
                    modifier = Modifier.clickable { onCategorySelected(category) }
                ) {
                    Text(
                        text = category,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) Color.White else InkPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Locations Count
        Text(
            text = "${filteredPlaces.size} DESTINATIONS FOUND",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = InkMuted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        // Places List
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredPlaces) { place ->
                ExplorePlaceListItem(
                    place = place,
                    onPlaceClick = onNavigateToPlaceDetail
                )
            }
        }
    }
}

@Composable
fun ExplorePlaceListItem(
    place: Place,
    onPlaceClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlaceClick(place.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteSurface),
        border = BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(CreamWarm)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("https://belagavi-tourism-planner.web.app/static/images/${place.folder_name}/1.jpg")
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
                        .padding(10.dp)
                ) {
                    Text(
                        text = place.category.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = CreamBackground,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = place.name,
                        fontFamily = FontFamily.Serif,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = ForestPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = place.city,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = InkMuted
                        )
                    }
                }

                Text(
                    text = place.description,
                    fontSize = 12.sp,
                    color = InkSecondary,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (place.entry_fee.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CreamWarm
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Icon(Icons.Default.AttachMoney, contentDescription = null, tint = InkMuted, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(place.entry_fee, fontSize = 11.sp, color = InkMuted)
                            }
                        }
                    }
                    if (place.visit_duration.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CreamWarm
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = InkMuted, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(place.visit_duration, fontSize = 11.sp, color = InkMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
