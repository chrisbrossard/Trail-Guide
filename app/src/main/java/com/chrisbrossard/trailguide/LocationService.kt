package com.chrisbrossard.trailguide

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class LocationService: Service(), SensorEventListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var locationClient: FusedLocationProviderClient

    private var lastLocation = Location("")
    private var distance = 0
    //private var accuracy = 0
    data class ChartPoint(
        val distance: Int,
        val altitude: Int
    )
    companion object {
        private val _trackingData = MutableStateFlow<List<ChartPoint>>(emptyList())
        val trackingData: StateFlow<List<ChartPoint>> = _trackingData.asStateFlow()
    }
    private var seaLevelPressure = -1f
    private var currentPressure = -1f
    private var pressureSensor: Sensor? = null
    private lateinit var sensorManager: SensorManager
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            serviceScope.launch {
                val locations = result.locations.sortedBy { it.time }
                var deltaDistance = 0
                var distanceString = "* "
                for (location in locations) {
                    //LocationRepository.emitLocation(location)
                    if (lastLocation.latitude != 0.0) {
                        var subDeltaDistance = lastLocation.distanceTo(location).toInt()
                        deltaDistance += subDeltaDistance
                        distanceString += "$subDeltaDistance "
                    }
                    lastLocation = location
                }
                distance += deltaDistance
                LocationRepository.emitLocations(locations)
                LocationRepository.emitDistance(distance)
                //LocationRepository.emitDeltaDistance(deltaDistance)
                //LocationRepository.emitSubDeltaDistances(distanceString)
                //LocationRepository.emitUpdateCount(locations.size)

                val a = SensorManager.getAltitude(seaLevelPressure, currentPressure)
                val newPoint = ChartPoint(
                        distance, //+ (Math.random() * 10).toInt(),
                        a.toInt() //+ (Math.random() * 1000).toInt()
                    )
                _trackingData.update { currentList -> currentList + newPoint}
            }
        }
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {
            "START" -> {
                seaLevelPressure = intent.getFloatExtra("sea_level_pressure",
                    -1f)
                currentPressure = intent.getFloatExtra("current_pressure",
                    -1f)
                val a = SensorManager.getAltitude(seaLevelPressure, currentPressure)
                if (_trackingData.value.isEmpty()) {
                    val zeroPoint = ChartPoint(
                        0,
                        a.toInt()
                    )
                    _trackingData.update { currentList -> currentList + zeroPoint}
                }

                val channel = NotificationChannel(
                    "altitude_steps_channel",
                    "Location Tracking",
                    NotificationManager.IMPORTANCE_LOW
                )
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
                val notification = NotificationCompat.Builder(this, "altitude_steps_channel")
                    .setContentTitle("Service Active")
                    .build()
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)

                val locationRequest = LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    10000).apply {
                        setMaxUpdateDelayMillis(30 * 1000)
                }.build()

                locationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )

                sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
                pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
                pressureSensor?.also { sensor ->
                    sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                }

                return START_STICKY
            }
            "STOP" -> {
                //serviceScope.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        locationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PRESSURE) {
            currentPressure = event.values[0]
        }
    }
}