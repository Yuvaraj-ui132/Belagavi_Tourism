package com.belagavi.tourism.ui.navigation

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.belagavi.tourism.data.repository.PlacesRepository
import com.belagavi.tourism.ui.auth.AuthViewModel
import com.belagavi.tourism.ui.dashboard.ExpenseView
import com.belagavi.tourism.ui.dashboard.ExploreView
import com.belagavi.tourism.ui.dashboard.HomeView
import com.belagavi.tourism.ui.dashboard.ProfileView
import com.belagavi.tourism.ui.theme.BorderLight
import com.belagavi.tourism.ui.theme.CreamBackground
import com.belagavi.tourism.ui.theme.CreamWarm
import com.belagavi.tourism.ui.theme.ForestPale
import com.belagavi.tourism.ui.theme.ForestPrimary
import com.belagavi.tourism.ui.theme.InkMuted
import com.belagavi.tourism.ui.theme.InkPrimary
import com.belagavi.tourism.ui.theme.InkSecondary
import com.belagavi.tourism.ui.theme.WhiteSurface

import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home

sealed class DashboardTab(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : DashboardTab("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Explore : DashboardTab("explore", "Explore", Icons.Filled.Explore, Icons.Outlined.Explore)
    object AiAssistant : DashboardTab("ai_chat", "AI Guide", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
    object Expense : DashboardTab("expense", "Budget", Icons.Filled.Wallet, Icons.Outlined.Wallet)
    object Profile : DashboardTab("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardHostView(
    navController: NavController,
    authViewModel: AuthViewModel,
    placesRepository: PlacesRepository
) {
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    var currentTab by remember { mutableStateOf<DashboardTab>(DashboardTab.Home) }
    var exploreCategory by remember { mutableStateOf("All") }

    val userName = currentUser?.displayName ?: currentUser?.email?.split('@')?.firstOrNull() ?: "Explorer"
    val avatarInitial = userName.firstOrNull()?.uppercase() ?: "B"

    LaunchedEffect(key1 = isLoggedIn) {
        if (!isLoggedIn) {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.DashboardHost.route) { inclusive = true }
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = WhiteSurface,
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 0.5.dp
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { currentTab = DashboardTab.Home }
                            ) {
                                // Web mark: Premium Saffron rounded square with italic B
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ForestPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "B",
                                        color = CreamBackground,
                                        fontSize = 17.sp,
                                        fontFamily = FontFamily.Serif,
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Belagavi",
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = InkPrimary,
                                        lineHeight = 18.sp
                                    )
                                    Text(
                                        text = "KARNATAKA",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp,
                                        color = InkMuted
                                    )
                                }
                            }
                        },
                        actions = {
                            // AI Guide quick access pill
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = ForestPale,
                                border = androidx.compose.foundation.BorderStroke(1.dp, ForestPrimary.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .clickable { navController.navigate("ai_chat") }
                                    .padding(end = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI Guide",
                                        tint = ForestPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "AI Guide",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = InkPrimary
                                    )
                                }
                            }

                            // Profile avatar button (navigates to Profile tab)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = CreamWarm,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                                modifier = Modifier
                                    .clickable { currentTab = DashboardTab.Profile }
                                    .padding(end = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(ForestPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = avatarInitial,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CreamBackground
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = userName.take(8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = InkPrimary
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = WhiteSurface
                        )
                    )
                    HorizontalDivider(color = BorderLight, thickness = 0.75.dp)
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WhiteSurface)
            ) {
                HorizontalDivider(color = BorderLight, thickness = 0.75.dp)
                NavigationBar(
                    containerColor = WhiteSurface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.height(64.dp)
                ) {
                    val tabs = listOf(
                        DashboardTab.Home,
                        DashboardTab.Explore,
                        DashboardTab.AiAssistant,
                        DashboardTab.Expense,
                        DashboardTab.Profile
                    )

                    tabs.forEach { tab ->
                        val selected = currentTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (tab == DashboardTab.AiAssistant) {
                                    navController.navigate("ai_chat")
                                } else {
                                    currentTab = tab
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    letterSpacing = 0.3.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = InkPrimary,
                                indicatorColor = ForestPrimary,
                                unselectedIconColor = InkSecondary,
                                unselectedTextColor = InkSecondary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CreamBackground)
        ) {
            Crossfade(targetState = currentTab, label = "TabTransition") { tab ->
                when (tab) {
                    is DashboardTab.Home -> {
                        HomeView(
                            authViewModel = authViewModel,
                            placesRepository = placesRepository,
                            onNavigateToExplore = { currentTab = DashboardTab.Explore },
                            onNavigateToExploreCategory = { cat ->
                                exploreCategory = cat
                                currentTab = DashboardTab.Explore
                            },
                            onNavigateToPlaceDetail = { placeId ->
                                navController.navigate("place/$placeId")
                            }
                        )
                    }
                    is DashboardTab.Explore -> {
                        ExploreView(
                            placesRepository = placesRepository,
                            selectedCategory = exploreCategory,
                            onCategorySelected = { exploreCategory = it },
                            onNavigateToPlaceDetail = { placeId ->
                                navController.navigate("place/$placeId")
                            }
                        )
                    }
                    is DashboardTab.AiAssistant -> {
                        // Handled via onClick navigation to existing AI Assistant screen
                    }
                    is DashboardTab.Expense -> {
                        ExpenseView(
                            authViewModel = authViewModel,
                            placesRepository = placesRepository
                        )
                    }
                    is DashboardTab.Profile -> {
                        ProfileView(
                            authViewModel = authViewModel,
                            placesRepository = placesRepository,
                            onNavigateToPlaceDetail = { placeId ->
                                navController.navigate("place/$placeId")
                            },
                            onNavigateToPrivacyPolicy = {
                                navController.navigate(Screen.PrivacyPolicy.route)
                            },
                            onNavigateToDiscover = {
                                currentTab = DashboardTab.Home
                            },
                            onNavigateToExpense = {
                                currentTab = DashboardTab.Expense
                            },
                            onNavigateToExplore = {
                                currentTab = DashboardTab.Explore
                            }
                        )
                    }
                }
            }
        }
    }
}
