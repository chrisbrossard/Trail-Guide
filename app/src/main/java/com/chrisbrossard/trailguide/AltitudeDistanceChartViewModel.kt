package com.chrisbrossard.trailguide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.round

class AltitudeDistanceChartViewModel(settingsViewModel: SettingsViewModel): ViewModel() {
    val modelProducer = CartesianChartModelProducer()
    var multiplier = 1f


    init {
        viewModelScope.launch {
            LocationService.trackingData.collect { points ->
                if (settingsViewModel.units.value == UnitSystem.IMPERIAL) {
                    multiplier = 3.28f
                }
                if (points.isNotEmpty()) {
                    modelProducer.runTransaction {
                        lineModel {
                            series(
                                x = points.map { round(it.distance * multiplier) },
                                y = points.map { String.format(Locale.US, "%.2f",
                                    it.altitude * multiplier).toFloat() }
                            )
                        }
                    }
                }
            }
        }
    }
}

