package com.chrisbrossard.trailguide

import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.ViewModel

class DeadlineViewModel: ViewModel() {
    var deadline = mutableLongStateOf(-1L)
}