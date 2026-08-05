package com.chrisbrossard.trailguide

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.google.android.gms.location.LocationCallback


class DistanceViewModel: ViewModel() {
    val distance = mutableIntStateOf(0)
    val tracking = mutableStateOf(false)
    lateinit var callback: LocationCallback
    val lastFixTime = mutableLongStateOf(0L)
    val timeToLastFix = mutableIntStateOf(0)
    val buttonLabel = mutableStateOf("Start")
    val distanceState = mutableStateOf(OnOffState.OFF)
    val buttonString = mutableStateOf("Start")
    val startChartActivity = mutableStateOf(false)

}