package com.chrisbrossard.trailguide

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class SunsetViewModel: ViewModel() {
    val sunset = mutableLongStateOf(-1L)
}