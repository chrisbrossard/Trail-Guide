package com.chrisbrossard.trailguide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import kotlinx.coroutines.launch
import java.util.Locale

class AltitudeDistanceChartViewModel: ViewModel() {
    val modelProducer = CartesianChartModelProducer()

    init {
        viewModelScope.launch {
            LocationService.trackingData.collect { points ->
                if (points.isNotEmpty()) {
                    modelProducer.runTransaction {
                        lineModel {
                            series(
                                x = points.map { String.format(Locale.US, "%.2f", it.time).toFloat() }, //distance },
                                y = points.map { String.format(Locale.US, "%.2f", it.altitude).toFloat() }
                            )
                        }
                    }
                }
            }
        }
    }
}

