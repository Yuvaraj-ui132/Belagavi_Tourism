package com.belagavi.tourism.data.model

import com.google.firebase.firestore.PropertyName

data class Place(
    val id: Int = 0,
    val name: String = "",
    val category: String = "",
    val description: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    @get:PropertyName("best_time") @set:PropertyName("best_time") var best_time: String = "",
    @get:PropertyName("entry_fee") @set:PropertyName("entry_fee") var entry_fee: String = "",
    @get:PropertyName("visit_duration") @set:PropertyName("visit_duration") var visit_duration: String = "",
    val city: String = "",
    @get:PropertyName("how_to_reach") @set:PropertyName("how_to_reach") var how_to_reach: String = "",
    @get:PropertyName("local_tips") @set:PropertyName("local_tips") var local_tips: String = "",
    @get:PropertyName("detailed_history") @set:PropertyName("detailed_history") var detailed_history: String = "",
    @get:PropertyName("folder_name") @set:PropertyName("folder_name") var folder_name: String = "",
    val history: String = "",
    val architecture: String = "",
    @get:PropertyName("famous_features") @set:PropertyName("famous_features") var famous_features: String = "",
    val transport: Transport = Transport()
)

data class Transport(
    @get:PropertyName("distance_from_city") @set:PropertyName("distance_from_city") var distance_from_city: String = "",
    @get:PropertyName("auto_taxi") @set:PropertyName("auto_taxi") var auto_taxi: String = "",
    val drive: String = "",
    val bus: List<BusRoute> = emptyList()
)

data class BusRoute(
    val route: String = "",
    val frequency: String = "",
    val duration: String = "",
    val fare: String = ""
)
