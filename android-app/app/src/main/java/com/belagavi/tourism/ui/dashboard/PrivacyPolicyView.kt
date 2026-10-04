package com.belagavi.tourism.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyView(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Privacy Policy",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Belagavi Smart Tourism",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Effective Date: January 1, 2025",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            PolicySection(
                icon = Icons.Default.GppGood,
                title = "Our Commitment",
                body = "Belagavi Smart Tourism is committed to protecting your privacy. We collect only the minimum data necessary to provide our services and never sell your personal information to third parties."
            )

            PolicySection(
                icon = Icons.Default.LocationOn,
                title = "Location (GPS) Usage",
                body = "We request your location to provide:\n• Real-time distance and travel time to tourist places\n• Dynamic route suggestions based on your current city\n• Google Maps navigation with your location as origin\n\nLocation is used only while the app is active and is never stored or shared. You can deny location access at any time — the app will fall back to Belagavi as the default origin."
            )

            PolicySection(
                icon = Icons.Default.Person,
                title = "Authentication",
                body = "We use Firebase Authentication for account management. You can sign in with:\n• Email and Password\n• Google Sign-In\n\nYour authentication credentials are managed securely by Firebase and Google. We store only your display name and email for personalisation (e.g., greeting on the Home screen)."
            )

            PolicySection(
                icon = Icons.Default.Lock,
                title = "Firebase & Data Storage",
                body = "We use Google Firebase services:\n• Firebase Authentication — secure sign-in\n• Firebase Firestore — store wishlists and expense records\n• Firebase Analytics — anonymous usage statistics\n\nAll data is stored securely on Google Cloud. We do not sell, rent, or share your personal data with any third party."
            )

            PolicySection(
                icon = Icons.Default.Info,
                title = "Local Storage & Preferences",
                body = "The app stores your wishlist and expense data linked to your Firebase account. No sensitive information is stored locally on-device beyond what Firebase SDK requires for offline support."
            )

            PolicySection(
                icon = Icons.Default.GppGood,
                title = "Your Rights",
                body = "You can:\n• Delete your account at any time from the Profile screen\n• Revoke location permission in Android Settings → App Permissions\n• Revoke Google Sign-In access in your Google Account settings\n\nFor questions or data deletion requests, contact us through the app."
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Text(
                    text = "By using Belagavi Smart Tourism, you agree to this Privacy Policy. We may update this policy — you will be notified of significant changes.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PolicySection(icon: ImageVector, title: String, body: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 20.sp
            )
        }
    }
}
