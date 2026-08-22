package com.chrisbrossard.trailguide

import android.location.Location
import androidx.compose.ui.graphics.Path
import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.google.android.gms.location.Priority
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrisbrossard.trailguide.ui.theme.TrailGuideTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import dev.jamesyox.kastro.sol.SolarEvent
import dev.jamesyox.kastro.sol.SolarEventSequence
import java.time.LocalDate
import java.time.ZoneId
import kotlin.getValue
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import java.util.Locale
import kotlin.String
import kotlin.math.cos

class MainActivity : ComponentActivity(), SensorEventListener {
    @OptIn(ExperimentalMaterial3Api::class)
    val fusedLocationProviderClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    lateinit var singleLocationRequestPermissionLauncher: ActivityResultLauncher<Array<String>>
    lateinit var continuousLocationAndNotificationRequestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private var pressureSensor: Sensor? = null
    private lateinit var sensorManager: SensorManager

    private val pressureViewModel: PressureViewModel by viewModels()
    private val deadlineViewModel: DeadlineViewModel by viewModels()
    private val locationViewModel: LocationViewModel by viewModels()
    private val distanceViewModel: DistanceViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private lateinit var altitudeDistanceChartViewModel: AltitudeDistanceChartViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        //enableEdgeToEdge()

        altitudeDistanceChartViewModel = AltitudeDistanceChartViewModel(settingsViewModel)

        singleLocationRequestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            // permissions granted is not always correct.
            // Permissions must be checked manually
            var locationGranted = false
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                locationGranted = true
            } else if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                locationGranted = true
            }
            if (locationGranted) {
                if (!locationViewModel.hasLocation.value) {
                    fusedLocationProviderClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).addOnSuccessListener { location: Location ->
                        if (location.hasAltitude()) {
                            locationViewModel.location.value = location
                            val denominator =
                                1f - locationViewModel.location.value.altitude / 44330.77
                            pressureViewModel.seaLevelPressure.floatValue =
                                pressureViewModel.currentPressure.floatValue /
                                        denominator.pow(5.25588).toFloat()
                            locationViewModel.hasLocation.value = true
                        }
                    }
                }
            } else {
                if (shouldShowRequestPermissionRationale(
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) ||
                    shouldShowRequestPermissionRationale(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                ) {
                    val requiredPermissions = arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    val grantedPermissions = requiredPermissions.filter {
                        ContextCompat.checkSelfPermission(this, it) ==
                                PackageManager.PERMISSION_GRANTED
                    }
                    if (grantedPermissions.isEmpty()) {
                        val builder = AlertDialog.Builder(this)
                        builder.setTitle("Location permission")
                        builder.setMessage(
                            "Location permission required for calculating sunset time"
                        )
                        builder.setPositiveButton("OK") { _, _ ->
                            singleLocationRequestPermissionLauncher.launch(requiredPermissions)
                        }
                        builder.setNegativeButton("Settings") { _, _ ->
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                            startActivity(intent)
                        }
                        val dialog: AlertDialog = builder.create()
                        dialog.show()
                    }
                }
            }
        }

        continuousLocationAndNotificationRequestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            // permissions granted is not always correct.
            // Permissions must be checked manually
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                locationViewModel.locationGranted.value = true
            } else if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                locationViewModel.locationGranted.value = true
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                locationViewModel.notificationGranted.value = true
            }

            if (!locationViewModel.locationGranted.value || !locationViewModel.notificationGranted.value) {
                if (shouldShowRequestPermissionRationale(
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) ||
                    shouldShowRequestPermissionRationale(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) ||
                    shouldShowRequestPermissionRationale(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                ) {
                    var requiredPermissions = arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requiredPermissions = arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                    val missingPermissions = requiredPermissions.filter {
                        ContextCompat.checkSelfPermission(this, it) ==
                                PackageManager.PERMISSION_DENIED
                    }
                    if (missingPermissions.isNotEmpty()) {
                        val builder = AlertDialog.Builder(this)
                        builder.setTitle("Location and Notification permission")
                        builder.setMessage(
                            "Location and notification permissions required for calculating distance"
                        )
                        builder.setPositiveButton("OK") { _, _ ->
                            continuousLocationAndNotificationRequestPermissionLauncher.launch(
                                missingPermissions.toTypedArray()
                            )
                        }
                        builder.setNegativeButton("Settings") { _, _ ->
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                            startActivity(intent)
                            distanceViewModel.distanceState.value = OnOffState.OFF
                        }
                        val dialog: AlertDialog = builder.create()
                        dialog.show()
                    }
                }
            }

            if (locationViewModel.locationGranted.value && locationViewModel.notificationGranted.value) {
                distanceViewModel.distanceState.value = OnOffState.STARTING
            }
        }

        setContent {
            TrailGuideTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        innerPadding,
                        pressureViewModel,
                        deadlineViewModel,
                        locationViewModel,
                        distanceViewModel,
                        settingsViewModel,
                        altitudeDistanceChartViewModel,
                        fusedLocationProviderClient,
                        singleLocationRequestPermissionLauncher,
                        continuousLocationAndNotificationRequestPermissionLauncher
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()

        pressureSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

    }

    override fun onPause() {
        super.onPause()

        sensorManager.unregisterListener(this)
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PRESSURE) {
            if (pressureViewModel.startPressure.floatValue == -1f) {
                pressureViewModel.startPressure.floatValue = event.values[0]
            }
            pressureViewModel.currentPressure.floatValue = event.values[0]
        }
    }
}

@OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun Greeting(
    innerPadding: PaddingValues,
    myPressureViewModel: PressureViewModel,
    myDeadlineViewModel: DeadlineViewModel,
    myLocationViewModel: LocationViewModel,
    myDistanceViewModel: DistanceViewModel,
    mySettingsViewModel: SettingsViewModel,
    myAltitudeDistanceChartViewModel: AltitudeDistanceChartViewModel,
    myFusedLocationProviderClient: FusedLocationProviderClient,
    singleLocationRequestPermissionLauncher: ActivityResultLauncher<Array<String>>,
    continuousLocationAndNotificationRequestPermissionLauncher: ActivityResultLauncher<Array<String>>,
    myTimerViewModel: TimerViewModel = viewModel(),
    ) {
    val hikingTime by myTimerViewModel.time.collectAsStateWithLifecycle()
    //val seaLevelPressure = remember { mutableFloatStateOf(-1f) }
    //val alertShown = remember { mutableStateOf(false) }
    val pickerShown = remember { mutableStateOf(false) }
    val radioButtonsShown = remember { mutableStateOf(false) }
    val unitsShown = remember { mutableStateOf(false) }
    val options = listOf("Metric", "Imperial")
    var selectedOption by remember { mutableStateOf(options[0]) }
    val timeToSunset = remember { mutableIntStateOf(0) }
    val timeToDeadline = remember { mutableIntStateOf((0)) }
    val locations by myLocationViewModel.locationsState.collectAsStateWithLifecycle()
    val location by myLocationViewModel.locationState.collectAsStateWithLifecycle()
    val unitsMenuExpanded = remember { mutableStateOf(false) }

    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                HikingTime(hikingTime)
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.height(IntrinsicSize.Max)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        SunsetTime(
                            myLocationViewModel,
                            onTimeToSunsetChanged = { timeToSunset.intValue = it },
                            myPressureViewModel,
                            myFusedLocationProviderClient,
                            singleLocationRequestPermissionLauncher,
                            Clock.System.now().epochSeconds
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DeadlineTime(
                            myDeadlineViewModel,
                            onTimeToDeadlineChanged = { timeToDeadline.intValue = it },
                            onPickerShownChanged = { pickerShown.value = it },
                            Clock.System.now().epochSeconds
                        )
                    }
                }
                DeadlineTimePicker(
                    myDeadlineViewModel,
                    pickerShown.value,
                    onPickerShownChanged = { pickerShown.value = it }
                )
                Spacer(modifier = Modifier.height(32.dp))
                Distance(
                    myLocationViewModel,
                    myDistanceViewModel,
                    myPressureViewModel,
                    mySettingsViewModel,
                    continuousLocationAndNotificationRequestPermissionLauncher,
                    unitsMenuExpanded.value,
                    onUnitsShownChanged = { unitsMenuExpanded.value = it},
                )
                Spacer(modifier = Modifier.height(32.dp))
                TrackChart(
                    myLocationViewModel,
                    mySettingsViewModel,
                    myDistanceViewModel
                )
                Spacer(modifier = Modifier.height(32.dp))
                AltitudeDistanceChart(
                    myAltitudeDistanceChartViewModel,
                    mySettingsViewModel,
                )
            }
        }
    } else { // landscape
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row {
                Column( // left column
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.height(32.dp))
                    HikingTime(hikingTime)
                    Spacer(modifier = Modifier.height(32.dp))
                    SunsetTime(
                        myLocationViewModel,
                        onTimeToSunsetChanged = { timeToSunset.intValue = it },
                        myPressureViewModel,
                        myFusedLocationProviderClient,
                        singleLocationRequestPermissionLauncher,
                        Clock.System.now().epochSeconds
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    DeadlineTime(
                        myDeadlineViewModel,
                        onTimeToDeadlineChanged = { timeToDeadline.intValue = it },
                        onPickerShownChanged = { pickerShown.value = it },
                        Clock.System.now().epochSeconds
                    )
                }
                Column( // middle column
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Distance(
                        myLocationViewModel,
                        myDistanceViewModel,
                        myPressureViewModel,
                        mySettingsViewModel,
                        continuousLocationAndNotificationRequestPermissionLauncher,
                        unitsMenuExpanded.value,
                        onUnitsShownChanged = { unitsMenuExpanded.value = it }
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    TrackChart(
                        myLocationViewModel,
                        mySettingsViewModel,
                        myDistanceViewModel
                    )
                }
                DeadlineTimePicker(
                    myDeadlineViewModel,
                    pickerShown.value,
                    onPickerShownChanged = { pickerShown.value = it }
                )
                Column( // right column
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AltitudeDistanceChart(
                        myAltitudeDistanceChartViewModel,
                        mySettingsViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun HikingTime(hikingTime: Int) {
    Spacer(modifier = Modifier.height(32.dp))
    Row {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = secondsToHoursAndMinutes(hikingTime),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Hiking time"
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
fun SunsetTime(
    myLocationViewModel: LocationViewModel,
    onTimeToSunsetChanged: (Int) -> Unit,
    myPressureViewModel: PressureViewModel,
    myFusedLocationProviderClient: FusedLocationProviderClient,
    singleLocationRequestPermissionLauncher: ActivityResultLauncher<Array<String>>,
    nowEpochSeconds: Long
) {
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)
    val epochSecondsAtStartOfDay =
        today.atStartOfDay(zoneId).toEpochSecond()
    val instantOfStartOfDay = Instant.fromEpochMilliseconds(
        epochSecondsAtStartOfDay * 1000
    )

    val nextSunset = SolarEventSequence(
        start = instantOfStartOfDay,
        latitude = myLocationViewModel.location.value.latitude,
        longitude = myLocationViewModel.location.value.longitude,
        requestedSolarEvents = listOf(SolarEvent.Sunset),
        limit = 1.days
    )
    var timeString: String
    val nowEpochSeconds = nowEpochSeconds
    if (nextSunset.none() || !myLocationViewModel.hasLocation.value) {
        timeString = "-"
    } else {
        val sunsetEpochSeconds = nextSunset.first().time.epochSeconds
        //mySunsetViewModel.sunset.longValue = sunsetEpochSeconds
        if (nowEpochSeconds > sunsetEpochSeconds) {
            timeString = "-"
        } else {
            /*val sunsetSecondsFromStartOfDay = (sunsetEpochSeconds -
        epochSecondsAtStartOfDay).toInt()
timeString = secondsToTimeOfDay(sunsetSecondsFromStartOfDay)*/
            val timeToSunset = (sunsetEpochSeconds - nowEpochSeconds).toInt()
            onTimeToSunsetChanged(timeToSunset)
            timeString = secondsToHoursAndMinutes(timeToSunset)
        }
    }

    Text(
        modifier = Modifier.clickable {
            myLocationViewModel.locationRequest.value = true
        },
        text = timeString,
        style = MaterialTheme.typography.headlineSmall
    )
    Spacer(modifier = Modifier.width(8.dp))
    if (myLocationViewModel.locationRequest.value) {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(LocalContext.current, it) ==
                    PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            if (!myLocationViewModel.hasLocation.value) {
                myFusedLocationProviderClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).addOnSuccessListener { location: Location ->
                    if (location.hasAltitude()) {
                        myLocationViewModel.location.value = location
                        val denominator =
                            1f - myLocationViewModel.location.value.altitude / 44330.77
                        myPressureViewModel.seaLevelPressure.floatValue =
                            myPressureViewModel.currentPressure.floatValue /
                                    denominator.pow(5.25588).toFloat()
                        myLocationViewModel.hasLocation.value = true
                    }
                }
            }
        } else {
            val requiredPermissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            singleLocationRequestPermissionLauncher.launch(
                requiredPermissions
            )
        }
        myLocationViewModel.locationRequest.value = false
    }
    Text(
        text = "Time to sunset"
    )
}

@OptIn(ExperimentalTime::class)
@Composable
fun DeadlineTime(
    myDeadlineViewModel: DeadlineViewModel,
    onTimeToDeadlineChanged: (Int) -> Unit,
    onPickerShownChanged: (Boolean) -> Unit,
    nowEpochSeconds: Long
) {
    var timeString = "-"
    if (myDeadlineViewModel.deadline.longValue != -1L) {
        /*val deadlineSecondsFromStartOfDay = (myDeadlineViewModel.deadline.longValue -
                epochSecondsAtStartOfDay).toInt()
        timeString = secondsToTimeOfDay(deadlineSecondsFromStartOfDay*/
        val timeToDeadline = (myDeadlineViewModel.deadline.longValue -
                nowEpochSeconds).toInt()
        onTimeToDeadlineChanged(timeToDeadline)
        timeString = secondsToHoursAndMinutes(timeToDeadline)
    }

    Text(
        modifier = Modifier
            .clickable {
                onPickerShownChanged(true)
            },
        text = timeString,
        style = MaterialTheme.typography.headlineSmall
    )
    Text(
        text = "Time to deadline"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlineTimePicker(
    myDeadlineViewModel: DeadlineViewModel,
    pickerShown: Boolean,
    onPickerShownChanged: (Boolean) -> Unit
) {
    if (pickerShown) {
        val timePickerState = rememberTimePickerState(
            initialHour = 0,
            initialMinute = 0,
            is24Hour = false
        )
        TimePickerDialog(
            onDismissRequest = {
                onPickerShownChanged(false)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pickerTime = LocalDate.now().atTime(
                            timePickerState.hour,
                            timePickerState.minute
                        )
                        myDeadlineViewModel.deadline.longValue = pickerTime
                            .atZone(ZoneId.systemDefault())
                            .toEpochSecond()
                        onPickerShownChanged(false)
                    }
                ) {
                    Text("Confirm")
                }
            },
            title = {
                Text(
                    text = "Deadline"
                )
            }
        ) {
            TimePicker(timePickerState)
        }
    }
}

enum class OnOffState {
    OFF, PERMISSION, STARTING, ON, STOPPING
}

@OptIn(ExperimentalTime::class)
@Composable
fun Distance(
    myLocationViewModel: LocationViewModel,
    myDistanceViewModel: DistanceViewModel,
    myPressureViewModel: PressureViewModel,
    mySettingsViewModel: SettingsViewModel,
    continuousLocationAndNotificationRequestPermissionLauncher: ActivityResultLauncher<Array<String>>,
    unitsShown: Boolean,
    onUnitsShownChanged: (Boolean) -> Unit,
) {
    val distance by myLocationViewModel.distanceState.collectAsStateWithLifecycle()

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        var textString = "$distance m \u25bc"
        if (mySettingsViewModel.units.value == UnitSystem.IMPERIAL) {
            textString = String.format(Locale.US, "%d ft \u25bc", (distance * 3.28).toInt())
        }
        Text(
            text = textString,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.clickable {
                onUnitsShownChanged(true)
            }
        )
        DropdownMenu(
            expanded = unitsShown,
            onDismissRequest = { onUnitsShownChanged(false) },
        ) {
            DropdownMenuItem(
                text = { Text("Metric") },
                onClick = {
                    mySettingsViewModel.units.value = UnitSystem.METRIC
                    onUnitsShownChanged(false)
                }
            )
            DropdownMenuItem(
                text = { Text("Imperial") },
                onClick = {
                    mySettingsViewModel.units.value = UnitSystem.IMPERIAL
                    onUnitsShownChanged(false)
                }
            )
        }
        Spacer(modifier = Modifier.padding(6.dp))
        OutlinedButton(
            onClick = {
                when (myDistanceViewModel.distanceState.value) {
                    OnOffState.OFF -> {
                        myDistanceViewModel.distanceState.value = OnOffState.PERMISSION
                    }

                    OnOffState.PERMISSION -> {

                    }

                    OnOffState.STARTING -> {

                    }

                    OnOffState.ON -> {
                        myDistanceViewModel.distanceState.value = OnOffState.STOPPING
                    }

                    OnOffState.STOPPING -> {

                    }
                }
            }
        ) {
            Text(
                text = myDistanceViewModel.buttonString.value
            )
        }
    }
    Text("Distance")
    when (myDistanceViewModel.distanceState.value) {
        OnOffState.OFF -> {
        }
        OnOffState.PERMISSION -> {
            var requiredPermissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requiredPermissions = arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
            val missingPermissions = requiredPermissions.filter {
                ContextCompat.checkSelfPermission(LocalContext.current, it) ==
                        PackageManager.PERMISSION_DENIED
            }
            if (missingPermissions.isEmpty() && myLocationViewModel.notificationGranted.value) {
                myDistanceViewModel.distanceState.value = OnOffState.STARTING
            } else {
                continuousLocationAndNotificationRequestPermissionLauncher.launch(missingPermissions.toTypedArray())
            }
        }
        OnOffState.STARTING -> {
            val intent = Intent(
                LocalContext.current,
                LocationService::class.java).apply {
                action = "START"
            }
            if (myLocationViewModel.hasLocation.value) {
                intent.putExtra("sea_level_pressure", myPressureViewModel.seaLevelPressure.floatValue)
            } else {
                intent.putExtra("sea_level_pressure", SensorManager.PRESSURE_STANDARD_ATMOSPHERE)
            }
                //myPressureViewModel.seaLevelPressure.floatValue)
            intent.putExtra("current_pressure",
                myPressureViewModel.currentPressure.floatValue)
            LocalContext.current.startForegroundService(intent)
            myDistanceViewModel.buttonString.value = "Stop"
            myDistanceViewModel.distanceState.value = OnOffState.ON
        }
        OnOffState.ON -> {
        }
        OnOffState.STOPPING -> {
            val intent = Intent(
                LocalContext.current,
                LocationService::class.java).apply {
                action = "STOP"
            }
            LocalContext.current.stopService(intent)
            myDistanceViewModel.buttonString.value = "Start"
            myDistanceViewModel.distanceState.value = OnOffState.OFF
        }
    }
}

@Composable
fun TrackChart(
    locationViewModel: LocationViewModel,
    settingsViewModel: SettingsViewModel,
    distanceViewModel: DistanceViewModel
) {
    val textMeasurer = rememberTextMeasurer()

    //if (distanceViewModel.distanceState.value == OnOffState.ON) {
        Canvas(
            modifier = Modifier
                //.fillMaxWidth()
                //.weight(1f)
                .size(200.dp)
                //.aspectRatio(1f)
                .background(Color.Transparent)
        ) {
            var path = Path()
            path.moveTo(0f, 0f)
            path.lineTo(size.width, 0f)
            path.lineTo(size.width, size.height)
            path.lineTo(0f, size.height)
            path.close()
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(width = 1.dp.toPx())
            )
            path = Path()
            //var dx = 0f
            //var dy = 0f
            //path.moveTo(size.width / 2f, size.height / 2f
            if (locationViewModel.locationHistory.isNotEmpty() && locationViewModel.locationHistory.size > 1) {

                val minimumLatitude = locationViewModel.locationHistory.minOf { it.latitude }
                val maximumLatitude = locationViewModel.locationHistory.maxOf { it.latitude }
                val minimumLongitude = locationViewModel.locationHistory.minOf { it.longitude }
                val maximumLongitude = locationViewModel.locationHistory.maxOf { it.longitude }

                val latitudeRange = maximumLatitude - minimumLatitude
                val longitudeRange = maximumLongitude - minimumLongitude

                val meanLatitude = (minimumLatitude + maximumLatitude) / 2

                val latitudeRangeMeters = latitudeRange * 111320.0
                val longitudeRangeMeters =
                    longitudeRange * 111320.0 * cos(Math.toRadians(meanLatitude))

                //if (latitudeRange != 0.0 && longitudeRange != 0.0) {
                for ((index, location) in locationViewModel.locationHistory.withIndex()) {
                    if (location.latitude != 0.0) {
                        val range = maxOf(latitudeRange, longitudeRange)

                        var rangeMeters = latitudeRangeMeters
                        if (longitudeRangeMeters > latitudeRangeMeters) {
                            rangeMeters = longitudeRangeMeters
                        }
                        val legendPath = Path()
                        legendPath.moveTo(15f, size.height - 30f)
                        legendPath.lineTo(15f + size.width / 4f, size.height - 30f)
                        drawPath(
                            path = legendPath,
                            color = Color.Black,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        //path = Path()
                        var textToDraw =
                            String.format(Locale.US, "%d m", (rangeMeters / 4f).toInt())
                        if (settingsViewModel.units.value == UnitSystem.IMPERIAL) {
                            textToDraw =
                                String.format(Locale.US, "%d ft", (rangeMeters * 3.28 / 4f).toInt())
                        }
                        val textLayoutResult =
                            textMeasurer.measure(textToDraw, style = TextStyle(fontSize = 12.sp))
                        drawText(
                            textMeasurer = textMeasurer,
                            text = textToDraw,
                            topLeft = Offset(
                                15f + size.width / (4f * 2f) - textLayoutResult.size.width / 2f,
                                size.height - 25f
                            ),
                            style = TextStyle(fontSize = 12.sp)
                        )

                        //val range = if (longitudeRange > latitudeRange) longitudeRange else latitudeRange
                        val latitudeFraction: Double = if (range != 0.0) {
                            (location.latitude - minimumLatitude) / range
                        } else {
                            0.5
                        }
                        val longitudeFraction: Double = if (range != 0.0) {
                            (location.longitude - minimumLongitude) / range
                        } else {
                            0.5
                        }
                        var x = longitudeFraction * size.width
                        val deltaX = longitudeRange / range * size.width
                        val offsetX = size.width / 2 - deltaX / 2
                        x += offsetX
                        var y = (1.0 - latitudeFraction) * size.height
                        val deltaY = latitudeRange / range * size.height
                        val offsetY = size.height / 2 - deltaY / 2
                        y -= offsetY
                        if (index == 0) {
                            path.moveTo(x.toFloat(), y.toFloat())
                            /*drawCircle(
                            center = Offset(x.toFloat(), y = y.toFloat()),
                            radius = 8f,
                            color = Color.Red,
                            style = Stroke(width = 2f))*/
                        } else {
                            path.lineTo(x.toFloat(), y.toFloat())
                            /*drawCircle(
                            center = Offset(x.toFloat(), y = y.toFloat()),
                            radius = 8f,
                            color = Color.Red,
                            style = Stroke(width = 2f))*/
                        }
                    }
                }
                //path.close()
                drawPath(
                    path = path,
                    color = Color.Red,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    //}
}

@Composable
fun AltitudeDistanceChart(
    altitudeDistanceChartViewModel: AltitudeDistanceChartViewModel,
    settingsViewModel: SettingsViewModel,
    //viewModel: AltitudeDistanceChartViewModel = viewModel()
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val axisTitleComponent = rememberTextComponent()

    Box(
        //modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CartesianChartHost(
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    /*lineSpecs = listOf(
                        rememberLineSpec(
                            shader = verticalGradient
                        )
                    )*/
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(Color.Red)),
                            areaFill = LineCartesianLayer.AreaFill.single(
                                fill = Fill(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary
                                                .copy(alpha = 0.25f),
                                            Color.Transparent
                                        )
                                    )
                                )
                            ),
                            interpolator = LineCartesianLayer.Interpolator
                                .cubic(curvature = 0.38f),
                            /*fixpointProvider = LineCartesianLayer.PointProvider.single(
                                point = LineCartesianLayer.Point(
                                    component = rememberShapeComponent(
                                        fill = Fill(Color.Red),
                                        shape = CircleShape
                                    ),
                                    size = 10.dp
                                )
                            )*/
                        )
                    )
                ),
                startAxis = VerticalAxis.rememberStart(
                    title = { "Altitude " +
                            if (settingsViewModel.units.value == UnitSystem.METRIC) "(m)" else "(ft)" },
                    titleComponent = axisTitleComponent,
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    title = { "Distance "  +
                            if (settingsViewModel.units.value == UnitSystem.METRIC) "(m)" else "(ft)" },
                    titleComponent = axisTitleComponent,
                    valueFormatter = { _, value, _ -> "${value.toInt()}" }
                ),
            ),
            modelProducer = altitudeDistanceChartViewModel.modelProducer //viewModel.modelProducer
        )
    }
}

/*fun secondsToTimeOfDay(seconds: Int): String {
    var timeString = String.format(Locale.US, "%02d", (seconds / 3600)) + ":"
    timeString += String.format(Locale.US, "%02d", ((seconds % 3600) / 60))
    return timeString
}*/

fun secondsToHoursAndMinutes(seconds: Int): String {
    var timeString = (seconds / 3600).toString() + " h "
    timeString += ((seconds % 3600) / 60).toString() + " m"
    return timeString
}


/*@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Units(
    currentUnit: String,
    unitsShown: Boolean,
    onUnitsShownChanged: (Boolean) -> Unit,
    onUnitsChanged: (String) -> Unit
) {
    if (unitsShown) {
        val options = listOf("METRIC", "IMPERIAL")

        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    onClick = { onUnitsChanged(label) },
                    selected = currentUnit == label
                ) {
                    Text(label.lowercase().replaceFirstChar { it.uppercase() }) // "Metric" or "Imperial"
                }
            }
        }


        val builder = AlertDialog.Builder(LocalContext.current)
        builder.setTitle("Units")
        builder.setMessage(
            "Please select units"
        )
        builder.setPositiveButton("Metric") { _, _ ->
            onUnitsChanged("METRIC")
            onUnitsShownChanged(false)
        }
        builder.setNegativeButton("Imperial") { _, _ ->
            onUnitsChanged("IMPERIAL")
            onUnitsShownChanged(false)
        }
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }
}*/

/*fun secondsToMinutesAndSeconds(seconds: Int): String {
    var timeString = ((seconds % 3600) / 60).toString() + "m "
    timeString += (seconds % 60).toString() + "s"
    return timeString
}*/

/*@Composable
fun HikingTime(
    hikingTime: Int,
) {
    val timeString = secondsToHoursAndMinutes(hikingTime)

    Column {

        Text(
            text = timeString,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Hiking time"
        )
    }
}*/

/*Row(
    modifier = Modifier.height(IntrinsicSize.Max)
) {
    /*TurnaroundTimeRadioButtonGroup(
        options,
        selectedOption,
        onTurnaroundTypeChanged = { selectedOption = it }
    )*/
    Column(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .selectableGroup()
                .padding(4.dp)
        ) {
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .selectable(
                            selected = (option == selectedOption),
                            onClick = {
                                selectedOption = option
                            },
                            role = Role.RadioButton
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (option == selectedOption),
                        onClick = null
                    )
                    Text(option)
                }
            }
        }
        Text("Turnaround based on")
    }
    Column(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val timeString = TurnaroundTime(
            myTimerViewModel,
            myDeadlineViewModel,
            selectedOption,
            hikingTime,
            timeToSunset.intValue,
            timeToDeadline.intValue
        )
        Text(
            text = timeString,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Turnaround time"
        )
    }
}
Spacer(modifier = Modifier.height(32.dp))*/

/*AltitudeChange(
    myPressureViewModel
)
Altitude(
    myPressureViewModel,
    myLocationViewModel,
    seaLevelPressure.floatValue,
    onAlertShownChanged = { alertShown.value = it} ,
    onSeaLevelPressureChanged = { seaLevelPressure.floatValue = it }
)
AltitudeAlertDialog(
    myPressureViewModel,
    alertShown.value,
    onSeaLevelPressureChanged = { seaLevelPressure.floatValue = it},
    onAlertShownChanged = { alertShown.value = it}
)*/
/*Canvas(
    modifier = Modifier
        .size(200.dp)
        .background(Color.Blue)
){

}*/

/*val fusedLocationProviderClient: FusedLocationProviderClient by lazy {
    LocationServices.getFusedLocationProviderClient(this)
}
@SuppressLint("MissingPermission")
fusedLocationProviderClient.getCurrentLocation(
    Priority.PRIORITY_HIGH_ACCURACY,
    CancellationTokenSource().token
).addOnSuccessListener { location ->
    if (location != null) {
        locationViewModel.location.value = location
        val denominator = 1f - locationViewModel.location.value.altitude / 44330.77
        pressureViewModel.seaLevelPressure.floatValue =
            pressureViewModel.currentPressure.floatValue /
                    denominator.pow(5.25588).toFloat()
    }
}*/

// this is to catch when the user goes to settings to enable location permission and then backs out.
/*val singleLocationRequestSettingsLauncher = registerForActivityResult(
ActivityResultContracts.StartActivityForResult()
) {
    val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    val missingPermissions = requiredPermissions.filter {
        ContextCompat.checkSelfPermission(this, it) ==
                PackageManager.PERMISSION_GRANTED
    }
    if (missingPermissions.isNotEmpty()) {
        if (!locationViewModel.hasLocation.value) {
            @SuppressLint("MissingPermission")
            fusedLocationProviderClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location: Location ->
                if (location.hasAltitude()) {
                    locationViewModel.location.value = location
                    val denominator =
                        1f - locationViewModel.location.value.altitude / 44330.77
                    pressureViewModel.seaLevelPressure.floatValue =
                        pressureViewModel.currentPressure.floatValue /
                                denominator.pow(5.25588).toFloat()
                    locationViewModel.hasLocation.value = true
                }
            }
        }
    }
}*/
// this is to catch when the user goes to settings to enable location or notifications and backs out
/*val continuousLocationAndNotificationRequestSettingsLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) {
    var locationGranted = false
    if (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        locationGranted = true
    } else  if (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        locationGranted = true
    }
    var postGranted = false
    if (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        postGranted = true
    }
    if (locationGranted && postGranted) {
        distanceViewModel.distanceState.value = OnOffState.STARTING
    } else {
        distanceViewModel.distanceState.value = OnOffState.OFF
    }
}*/

/*@Composable
fun TurnaroundTimeRadioButtonGroup(
    options: List<String>,
    selectedOption: String,
    onTurnaroundTypeChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .selectableGroup()
            .padding(4.dp)
    ) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .selectable(
                        selected = (option == selectedOption),
                        onClick = {
                            onTurnaroundTypeChanged(option)
                        },
                        role = Role.RadioButton
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (option == selectedOption),
                    onClick = null
                )
                Text(option)
            }
        }
    }
    Text("Turnaround based on")
    Spacer(modifier = Modifier.height(32.dp))
}*/

/*@Composable
fun TurnaroundTime(
    myTimerViewModel: TimerViewModel,
    myDeadlineViewModel: DeadlineViewModel,
    //mySunsetViewModel: SunsetViewModel,
    selectedOption: String,
    hikingTime: Int,
    timeToSunset: Int,
    timeToDeadline: Int
): String {
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)
    val epochSecondsAtStartOfDay =
        today.atStartOfDay(zoneId).toEpochSecond()

    var turnaroundSecondsFromStartOfDay: Int
    var turnaroundSecondsFromNow: Int
    var timeString = "-"
    if (selectedOption == "Sunset") {
        turnaroundSecondsFromStartOfDay = (myTimerViewModel.startFromEpoch.longValue -
                epochSecondsAtStartOfDay).toInt()
        turnaroundSecondsFromStartOfDay += (hikingTime + timeToSunset) / 2
        timeString = secondsToTimeOfDay(turnaroundSecondsFromStartOfDay)
        turnaroundSecondsFromNow =
            ((hikingTime + timeToSunset) / 2) - hikingTime
        timeString += if (turnaroundSecondsFromNow < 0) {
            " (now)"
        } else {
            " (" + secondsToHoursAndMinutes(turnaroundSecondsFromNow) + ")"
        }
    } else if (myDeadlineViewModel.deadline.longValue != -1L) {
        turnaroundSecondsFromStartOfDay = (myTimerViewModel.startFromEpoch.longValue -
                epochSecondsAtStartOfDay).toInt()
        turnaroundSecondsFromStartOfDay += (hikingTime + timeToDeadline) / 2
        timeString = secondsToTimeOfDay(turnaroundSecondsFromStartOfDay)
        turnaroundSecondsFromNow = (hikingTime + timeToDeadline) / 2 - hikingTime
        timeString += if (turnaroundSecondsFromNow < 0) {
            " (now)"
        } else {
            " (" + secondsToHoursAndMinutes(turnaroundSecondsFromNow) + ")"
        }
    }
    return timeString
}*/

/*@Composable
fun AltitudeChange(myPressureViewModel: PressureViewModel) {
    val startAltitude = SensorManager.getAltitude(
        PRESSURE_STANDARD_ATMOSPHERE,
        myPressureViewModel.startPressure.floatValue
    )
    val currentAltitude = SensorManager.getAltitude(
        PRESSURE_STANDARD_ATMOSPHERE,
        myPressureViewModel.currentPressure.floatValue
    )
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = (currentAltitude - startAltitude).toInt()
                .toString() + " m",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = {
                myPressureViewModel.startPressure.floatValue =
                    myPressureViewModel.currentPressure.floatValue
            }
        ) {
            Text("Reset")
        }
    }
    Text(
        text = "Altitude change"
    )
    Spacer(modifier = Modifier.height(32.dp))
}*/

/*@Composable
fun Altitude(
    myPressureViewModel: PressureViewModel,
    myLocationViewModel: LocationViewModel,
    seaLevelPressure: Float,
    onAlertShownChanged: (Boolean) -> Unit,
    onSeaLevelPressureChanged: (Float) -> Unit
) {
    if (seaLevelPressure == -1f &&
        myPressureViewModel.currentPressure.floatValue != 0f &&
        myLocationViewModel.location.value.latitude != 0.0
    ) {
        val denominator = 1f - myLocationViewModel.location.value.altitude / 44330.77
        myPressureViewModel.seaLevelPressure.floatValue =
            myPressureViewModel.currentPressure.floatValue /
                denominator.pow(5.25588).toFloat()
    }
    val a = SensorManager.getAltitude(
        seaLevelPressure,
        myPressureViewModel.currentPressure.floatValue
    )
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = a.toInt().toString() + " m",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = {
                onAlertShownChanged(true)
            }
        ) {
            Text("Set")
        }
    }
    Text(
        text = "Altitude"
    )
    Spacer(modifier = Modifier.height(32.dp))
}*/

/*@Composable
fun AltitudeAlertDialog(
    myPressureViewModel: PressureViewModel,
    alertShown: Boolean,
    onSeaLevelPressureChanged: (Float) -> Unit,
    onAlertShownChanged: (Boolean) -> Unit
) {
    var altitudeInput = ""

    if (alertShown) {
        AlertDialog(
            onDismissRequest = {
                onAlertShownChanged(false)
            },
            title = {
                Text("Enter an altitude")
            },
            text = {
                OutlinedTextField(
                    value = altitudeInput,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            altitudeInput = newValue
                        }
                    },
                    label = {},
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        altitudeInput.toIntOrNull()?.let {
                            val denominator =
                                1f - altitudeInput.toFloat() / 44330.77
                            onSeaLevelPressureChanged(
                                myPressureViewModel.currentPressure.floatValue /
                                        denominator.pow(5.25588).toFloat()
                            )
                            onAlertShownChanged(false)
                        }
                    },
                    enabled = altitudeInput.isNotEmpty()
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onAlertShownChanged(false)
                    },
                    content = {
                        Text("Dismiss")
                    }
                )
            }
        )
    }
}*/