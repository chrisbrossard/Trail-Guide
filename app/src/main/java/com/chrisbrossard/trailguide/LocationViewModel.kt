package com.chrisbrossard.trailguide

import android.location.Location
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class LocationViewModel: ViewModel() {
    val location = mutableStateOf(Location(""))
    val locationHistory = mutableStateListOf<Location>()
    val hasLocation = mutableStateOf(false)
    val locationState = LocationRepository.locationFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10000),
            initialValue = Location("")
        )
    val locationsState = LocationRepository.locationsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10000),
            initialValue = emptyList()
        )
    val distanceState = LocationRepository.distanceFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10000),
            initialValue = 0
        )
    /*val accuracyState = LocationRepository.accuracyFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10000),
            initialValue = 0
        )*/
    /*val speedState = LocationRepository.speedFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10000),
            initialValue = 0
        )*/
    /*val deltaDistanceState = LocationRepository.deltaDistanceFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10000),
            initialValue = 0
        )*/
    /*val updateCountState = LocationRepository.updateCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10000),
            initialValue = 0
        )*/
    /*val subDeltaDistances = LocationRepository.subDeltaDistances
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10000),
            initialValue = ""
        )*/
    //val startService = mutableStateOf(false)
    //val serviceStarted = mutableStateOf(false)
}