package com.chrisbrossard.trailguide

import android.os.SystemClock
import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

class TimerViewModel : ViewModel() {
    val startFromBoot = mutableLongStateOf(-1L)
    val startFromEpoch = mutableLongStateOf(0L)
    private val _time = MutableStateFlow(0)
    val time: StateFlow<Int> = _time.asStateFlow()

    init {
        startTimer()
    }

    @OptIn(ExperimentalTime::class)
    fun startTimer() {
        viewModelScope.launch {
            while(true) {
                delay(1000.milliseconds)
                if (startFromBoot.longValue == -1L) {
                    startFromBoot.longValue = SystemClock.elapsedRealtime()
                    startFromEpoch.longValue = Clock.System.now().epochSeconds
                }
                _time.value = ((SystemClock.elapsedRealtime() - startFromBoot.longValue) / 1000).toInt()
            }
        }
    }
}