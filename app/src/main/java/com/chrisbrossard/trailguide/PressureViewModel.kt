package com.chrisbrossard.trailguide

import androidx.compose.runtime.mutableFloatStateOf
import androidx.lifecycle.ViewModel

class PressureViewModel : ViewModel() {
    val seaLevelPressure = mutableFloatStateOf(-1f)
    val startPressure = mutableFloatStateOf(-1f)
    val currentPressure = mutableFloatStateOf(0f)
}