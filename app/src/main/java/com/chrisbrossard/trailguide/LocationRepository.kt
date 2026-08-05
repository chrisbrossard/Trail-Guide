package com.chrisbrossard.trailguide

import android.location.Location
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object LocationRepository {
    private val _locationFlow = MutableSharedFlow<Location>(replay = 1)
    val locationFlow = _locationFlow.asSharedFlow()

    private val _distanceFlow = MutableSharedFlow<Int>(replay = 1)
    val distanceFlow = _distanceFlow.asSharedFlow()
    private val _deltaDistanceFlow = MutableSharedFlow<Int>(replay = 1)
    val deltaDistanceFlow = _deltaDistanceFlow.asSharedFlow()

    private val _accuracyFlow = MutableSharedFlow<Int>(replay = 1)
    val accuracyFlow = _accuracyFlow.asSharedFlow()

    private val _updateCount = MutableSharedFlow<Int>(replay = 1)
    val updateCount = _updateCount.asSharedFlow()

    private val _subDeltaDistances = MutableSharedFlow<String>(replay = 1)
    val subDeltaDistances = _subDeltaDistances.asSharedFlow()

    suspend fun emitLocation(location: Location) {
        _locationFlow.emit(location)
    }
    private val _speedFlow = MutableSharedFlow<Float>(replay = 1)
    val speedFlow = _speedFlow.asSharedFlow()

    suspend fun emitDistance(distance: Int) {
        _distanceFlow.emit(distance)
    }
    suspend fun emitAccuracy(accuracy: Int) {
        _accuracyFlow.emit(accuracy)
    }

    suspend fun emitSpeed(speed: Float) {
        _speedFlow.emit(speed)
    }
    suspend fun emitDeltaDistance(deltaDistance: Int) {
        _deltaDistanceFlow.emit(deltaDistance)
    }
    suspend fun emitUpdateCount(updateCount: Int) {
        _updateCount.emit(updateCount)
    }

    suspend fun emitSubDeltaDistances(subDeltaDistance: String) {
        _subDeltaDistances.emit(subDeltaDistance)
    }
}