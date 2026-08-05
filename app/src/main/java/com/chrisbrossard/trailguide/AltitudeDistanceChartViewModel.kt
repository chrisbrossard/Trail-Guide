package com.chrisbrossard.trailguide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import kotlinx.coroutines.launch

class AltitudeDistanceChartViewModel: ViewModel() {
    val modelProducer = CartesianChartModelProducer()

    init {
        viewModelScope.launch {
            LocationService.trackingData.collect { points ->
                if (points.isNotEmpty()) {
                    modelProducer.runTransaction {
                        lineModel {
                            series(
                                x = points.map { it.distance },
                                y = points.map { it.altitude }
                            )
                        }
                    }
                }
            }
        }
    }
}

