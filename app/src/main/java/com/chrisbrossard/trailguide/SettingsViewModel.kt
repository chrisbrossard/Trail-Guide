package com.chrisbrossard.trailguide

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class UnitSystem {
    METRIC, IMPERIAL
}

class SettingsViewModel() : ViewModel() {
    val units = mutableStateOf(UnitSystem.METRIC)
}