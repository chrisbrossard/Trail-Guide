package com.chrisbrossard.trailguide

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class DeadlineViewModel: ViewModel() {
    var deadline = mutableLongStateOf(-1L)
}