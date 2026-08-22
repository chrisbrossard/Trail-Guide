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
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime


class LocationService: Service(), SensorEventListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var locationClient: FusedLocationProviderClient

    private var lastLocation = Location("")
    var serviceJob: Job? = null
    val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    //private var distance = 0f
    //private var accuracy = 0
    private var startTime = 0L
    data class ChartPoint(
        //val distance: Int,
        val distance: Float, //time: Float,
        val altitude: Float
    )
    companion object {
        private val _trackingData = MutableStateFlow<List<ChartPoint>>(emptyList())
        val trackingData: StateFlow<List<ChartPoint>> = _trackingData.asStateFlow()
    }
    private var seaLevelPressure = 0f
    private var currentPressure = 0f

    private var seaLevelPressureUpdated = false
    private var pressureSensor: Sensor? = null
    private lateinit var sensorManager: SensorManager
    @OptIn(ExperimentalTime::class)
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            serviceScope.launch {
                val locations = result.locations.sortedBy { it.time }
                var deltaDistance = 0
                //var distanceString = "* "
                //locations.forEachIndexed { index, location ->
                //if (seaLevelPressure == -1f && currentPressure != -1f && index == 0) {
                for (location in locations) {
                    if (pressureSensor != null && !seaLevelPressureUpdated) {
                        val denominator = 1f - location.altitude / 44330.77
                        seaLevelPressure = (currentPressure / denominator.pow(5.25588)).toFloat()
                        seaLevelPressureUpdated = true
                    }
                    //LocationRepository.emitLocation(location)
                    if (lastLocation.latitude != 0.0) {
                        var subDeltaDistance = lastLocation.distanceTo(location).toInt()
                        deltaDistance += subDeltaDistance
                        //distanceString += "$subDeltaDistance "
                    }
                    lastLocation = location
                }
                LocationRepository.addDistance(deltaDistance)
                LocationRepository.emitLocations(locations)
                LocationRepository.emitDistance(LocationRepository.getDistance())
                //LocationRepository.emitDeltaDistance(deltaDistance)
                //LocationRepository.emitSubDeltaDistances(distanceString)
                //LocationRepository.emitUpdateCount(locations.size)

                var a = locations.last().altitude.toFloat()
                if (pressureSensor != null) {
                    a = SensorManager.getAltitude(seaLevelPressure, currentPressure)
                }
                val newPoint = ChartPoint(
                    LocationRepository.getDistance().toFloat(), //(Clock.System.now().epochSeconds - startTime).toFloat(),
                    a
                )
                _trackingData.update { currentList -> currentList + newPoint }
            }
        }
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    @OptIn(ExperimentalTime::class)
    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {
            "START" -> {
                sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
                pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
                pressureSensor?.also { sensor ->
                    sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                }

                seaLevelPressure = intent.getFloatExtra("sea_level_pressure",
                    0f)
                currentPressure = intent.getFloatExtra("current_pressure",
                    0f)
                if (pressureSensor != null) {
                    val a = SensorManager.getAltitude(seaLevelPressure, currentPressure)
                    if (_trackingData.value.isEmpty()) {
                        val zeroPoint = ChartPoint(
                            0f,
                            a
                        )
                        _trackingData.update { currentList -> currentList + zeroPoint }
                    }
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

                startTime = Clock.System.now().epochSeconds

                /*serviceJob = CoroutineScope(Dispatchers.IO).launch {
                    while (isActive) {
                        val location: Location = suspendCancellableCoroutine { continuation ->
                            client.getCurrentLocation(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                CancellationTokenSource().token
                            ).addOnSuccessListener { location ->
                                continuation.resume(
                                    value = location
                                ) { cause, _, _ ->
                                    (cause)
                                }
                            }
                        }
                        LocationRepository.emitLocation(location)
                        if (lastLocation.latitude != 0.0) {
                            distance += lastLocation.distanceTo(location).toInt()
                        }
                        lastLocation = location
                        LocationRepository.emitDistance(distance)

                        val a = SensorManager.getAltitude(seaLevelPressure, currentPressure)
                        val newPoint = ChartPoint(
                            (Clock.System.now().epochSeconds - startTime).toFloat() / 60f, //distance,
                            a
                        )
                        _trackingData.update { currentList -> currentList + newPoint }

                        delay((60 * 1000).milliseconds)
                    }
                }*/

                val locationRequest = LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    10000).apply {
                        setMaxUpdateDelayMillis(60 * 1000)
                }.build()

                locationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )

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