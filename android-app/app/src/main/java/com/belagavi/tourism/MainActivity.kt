package com.belagavi.tourism

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.belagavi.tourism.data.repository.PlacesRepository
import com.belagavi.tourism.ui.navigation.NavGraph
import com.belagavi.tourism.ui.theme.BelagaviSmartTourismTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var placesRepository: PlacesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen BEFORE super.onCreate for correct behavior
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            BelagaviSmartTourismTheme {
                NavGraph(placesRepository = placesRepository)
            }
        }
    }
}
