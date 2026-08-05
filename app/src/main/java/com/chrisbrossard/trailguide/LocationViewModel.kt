package com.chrisbrossard.trailguide

import android.location.Location
import android.location.LocationListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LocationViewModel: ViewModel() {
    val location = mutableStateOf(Location(""))
    val hasLocation = mutableStateOf(false)
    val locationState = LocationRepository.locationFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10000),
            initialValue = Location("")
        )
    val distanceState = LocationRepository.distanceFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10000),
            initialValue = 0
        )
    val accuracyState = LocationRepository.accuracyFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10000),
            initialValue = 0
        )
    val speedState = LocationRepository.speedFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10000),
            initialValue = 0
        )
    val deltaDistanceState = LocationRepository.deltaDistanceFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10000),
            initialValue = 0
        )
    val updateCountState = LocationRepository.updateCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10000),
            initialValue = 0
        )
    val subDeltaDistances = LocationRepository.subDeltaDistances
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10000),
            initialValue = ""
        )
    val startService = mutableStateOf(false)
    val serviceStarted = mutableStateOf(false)

    init {
    }
}