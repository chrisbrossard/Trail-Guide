package com.chrisbrossard.trailguide

import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.ViewModel

class SunsetViewModel: ViewModel() {
    val sunset = mutableLongStateOf(-1L)
}