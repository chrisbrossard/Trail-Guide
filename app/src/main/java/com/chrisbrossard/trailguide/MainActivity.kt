package com.chrisbrossard.trailguide

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.PRESSURE_STANDARD_ATMOSPHERE
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.google.android.gms.location.Priority
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import kotlin.String
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

class MainActivity : ComponentActivity(), SensorEventListener {
    @OptIn(ExperimentalMaterial3Api::class)
    val fusedLocationProviderClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    val requestPermissionLauncher = registerForActivityResult(
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
        var postGranted = false
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            postGranted = true
        }
        if (locationGranted && postGranted) {
            setContent {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        innerPadding,
                        pressureViewModel,
                        deadlineViewModel,
                        locationViewModel,
                        distanceViewModel,
                        fusedLocationProviderClient
                    )
                }
            }
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
        }
    }
    private var pressureSensor: Sensor? = null
    private lateinit var sensorManager: SensorManager
    private val pressureViewModel: PressureViewModel by viewModels()
    private val deadlineViewModel: DeadlineViewModel by viewModels()
    private val locationViewModel: LocationViewModel by viewModels()
    private val distanceViewModel: DistanceViewModel by viewModels()
    private val sunsetViewModel: SunsetViewModel by viewModels()

    val altitudeDistanceViewModel: AltitudeDistanceChartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        //enableEdgeToEdge()

        var permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        var locationGranted = false
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            locationGranted = true
        } else if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            locationGranted = true
        }
        var postGranted = false
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            postGranted = true
        }

        if (locationGranted && postGranted) {
            setContent {
                TrailGuideTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Greeting(
                            innerPadding,
                            pressureViewModel,
                            deadlineViewModel,
                            locationViewModel,
                            distanceViewModel,
                            fusedLocationProviderClient
                        )
                    }
                }
            }
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()

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
                    PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            pressureSensor?.also { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
            if (!locationViewModel.hasLocation.value) {
                fusedLocationProviderClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).addOnSuccessListener { location ->
                    if (location != null && location.hasAltitude()) {
                        locationViewModel.location.value = location
                        val denominator = 1f - locationViewModel.location.value.altitude / 44330.77
                        pressureViewModel.seaLevelPressure.floatValue =
                            pressureViewModel.currentPressure.floatValue /
                                    denominator.pow(5.25588).toFloat()
                    }
                }
                locationViewModel.hasLocation.value = true
            }
        }
        if (shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        ) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Location permission")
            builder.setMessage("Location permission required for calculating sunset time" +
                " and distance")
            builder.setPositiveButton("OK") { _, _ ->
                requestPermissionLauncher.launch(requiredPermissions)
            }
            builder.setNegativeButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
        if (shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Location permission")
            builder.setMessage("Location permission required for calculating sunset time")
            builder.setPositiveButton("OK") { _, _ ->
                requestPermissionLauncher.launch(requiredPermissions)
            }
            builder.setNegativeButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
        if (shouldShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS
            )
        ) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Post notification permission")
            builder.setMessage("Post notification permission required to calculate distance")
            builder.setPositiveButton("OK") { _, _ ->
                requestPermissionLauncher.launch(requiredPermissions)
            }
            builder.setNegativeButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
    }

    override fun onPause() {
        super.onPause()
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                    PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            sensorManager.unregisterListener(this)
        }
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
    myFusedLocationProviderClient: FusedLocationProviderClient,
    myTimerViewModel: TimerViewModel = viewModel(),
    ) {
    val hikingTime by myTimerViewModel.time.collectAsStateWithLifecycle()
    //val seaLevelPressure = remember { mutableFloatStateOf(-1f) }
    val alertShown = remember { mutableStateOf(false) }
    val pickerShown = remember { mutableStateOf(false) }
    val options = listOf("Sunset", "Deadline")
    var selectedOption by remember { mutableStateOf(options[0]) }
    val timeToSunset = remember { mutableIntStateOf(0) }
    val timeToDeadline = remember { mutableIntStateOf((0)) }

    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HikingTime(
                    hikingTime
                )
                Sunset(
                    myLocationViewModel,
                    onTimeToSunsetChanged = { timeToSunset.intValue = it },
                    Clock.System.now().epochSeconds
                )
                Deadline(
                    myDeadlineViewModel,
                    onPickerShownChanged = { pickerShown.value = it },
                    onTimeToDeadlineChanged = { timeToDeadline.intValue = it },
                    Clock.System.now().epochSeconds
                )
                DeadlineTimePicker(
                    myDeadlineViewModel,
                    pickerShown.value,
                    onPickerShownChanged = { pickerShown.value = it },
                )
                TurnaroundTimeRadioButtonGroup(
                    options,
                    selectedOption,
                    onTurnaroundTypeChanged = { selectedOption = it }
                )
                TurnaroundTime(
                    myTimerViewModel,
                    myDeadlineViewModel,
                    selectedOption,
                    hikingTime,
                    timeToSunset.intValue,
                    timeToDeadline.intValue
                )
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
                Distance(
                    myLocationViewModel,
                    myDistanceViewModel,
                    myPressureViewModel,
                    myFusedLocationProviderClient
                )
                AltitudeDistanceChart()
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
                    HikingTime(
                        hikingTime
                    )
                    Sunset(
                        myLocationViewModel,
                        onTimeToSunsetChanged = { timeToSunset.intValue = it },
                        Clock.System.now().epochSeconds
                    )
                    Deadline(
                        myDeadlineViewModel,
                        onPickerShownChanged = { pickerShown.value = it },
                        onTimeToDeadlineChanged = { timeToDeadline.intValue = it },
                        Clock.System.now().epochSeconds

                    )
                    DeadlineTimePicker(
                        myDeadlineViewModel,
                        pickerShown.value,
                        onPickerShownChanged = { pickerShown.value = it}
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
                    TurnaroundTimeRadioButtonGroup(
                        options,
                        selectedOption,
                        onTurnaroundTypeChanged = { selectedOption = it }
                    )
                    TurnaroundTime(
                        myTimerViewModel,
                        myDeadlineViewModel,
                        selectedOption,
                        hikingTime,
                        timeToSunset.intValue,
                        timeToDeadline.intValue
                    )
                }
                Column( // right column
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    /*AltitudeChange(
                    myPressureViewModel
                    )
                    Altitude(
                        myPressureViewModel,
                        myLocationViewModel,
                        onAlertShownChanged = { alertShown.value = it}
                    )
                    AltitudeAlertDialog(
                        myPressureViewModel,
                        alertShown.value,
                        onAlertShownChanged = { alertShown.value = it}
                    )*/
                    Distance(
                        myLocationViewModel,
                        myDistanceViewModel,
                        myPressureViewModel,
                        myFusedLocationProviderClient
                    )
                    AltitudeDistanceChart()
                }
            }
        }
    }
}

@Composable
fun HikingTime(
    hikingTime: Int,
) {
    val timeString = secondsToHoursAndMinutes(hikingTime)
    Text(
        text = timeString,
        style = MaterialTheme.typography.headlineSmall
    )
    Text(
        text = "Hiking time"
    )
    Spacer(modifier = Modifier.height(32.dp))
}

@OptIn(ExperimentalTime::class)
@Composable
fun Sunset(
    myLocationViewModel: LocationViewModel,
    //mySunsetViewModel: SunsetViewModel,
    onTimeToSunsetChanged: (Int) -> Unit,
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
    var timeString = ""
    //val nowEpochSeconds = Clock.System.now().epochSeconds
    if (nextSunset.none()) {
        timeString = "-"
    } else {
        val sunsetEpochSeconds = nextSunset.first().time.epochSeconds
        //mySunsetViewModel.sunset.longValue = sunsetEpochSeconds
        if (nowEpochSeconds > sunsetEpochSeconds) {
            timeString = "-"
        } else {
            val sunsetSecondsFromStartOfDay = (sunsetEpochSeconds -
                    epochSecondsAtStartOfDay).toInt()
            timeString = secondsToTimeOfDay(sunsetSecondsFromStartOfDay)
            val timeToSunset = (sunsetEpochSeconds - nowEpochSeconds).toInt()
            onTimeToSunsetChanged(timeToSunset)
            timeString += " (" + secondsToHoursAndMinutes(timeToSunset) + ")"
        }
    }
    Text(
        text = timeString,
        style = MaterialTheme.typography.headlineSmall
    )
    Text(
        text = "Sunset time"
    )
    Spacer(modifier = Modifier.height(24.dp))
}

@OptIn(ExperimentalTime::class)
@Composable
fun Deadline(
    myDeadlineViewModel: DeadlineViewModel,
    onPickerShownChanged: (Boolean) -> Unit,
    onTimeToDeadlineChanged: (Int) -> Unit,
    nowEpochSeconds: Long
) {

    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)
    val epochSecondsAtStartOfDay =
        today.atStartOfDay(zoneId).toEpochSecond()

    var timeString = "-"
    if (myDeadlineViewModel.deadline.longValue != -1L) {
        val deadlineSecondsFromStartOfDay = (myDeadlineViewModel.deadline.longValue -
                epochSecondsAtStartOfDay).toInt()
        timeString = secondsToTimeOfDay(deadlineSecondsFromStartOfDay)
        val timeToDeadline = (myDeadlineViewModel.deadline.longValue -
                nowEpochSeconds).toInt()
        onTimeToDeadlineChanged(timeToDeadline)
        timeString += " (" + secondsToHoursAndMinutes(timeToDeadline) + ")"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = timeString,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = {
                onPickerShownChanged(true)
                //pickerShown.value = true
            },
        ) {
            Text(
                text = "Set"
            )
        }
    }
    Text(
        text = "Deadline"
    )
    Spacer(modifier = Modifier.height(32.dp))
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

@Composable
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
}

@Composable
fun TurnaroundTime(
    myTimerViewModel: TimerViewModel,
    myDeadlineViewModel: DeadlineViewModel,
    //mySunsetViewModel: SunsetViewModel,
    selectedOption: String,
    hikingTime: Int,
    timeToSunset: Int,
    timeToDeadline: Int
) {
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
        if (turnaroundSecondsFromNow < 0) {
            timeString += " (now)"
        } else {
            timeString += " (" + secondsToHoursAndMinutes(turnaroundSecondsFromNow) + ")"
        }
    } else if (myDeadlineViewModel.deadline.longValue != -1L) {
        turnaroundSecondsFromStartOfDay = (myTimerViewModel.startFromEpoch.longValue -
                epochSecondsAtStartOfDay).toInt()
        turnaroundSecondsFromStartOfDay += (hikingTime + timeToDeadline) / 2
        timeString = secondsToTimeOfDay(turnaroundSecondsFromStartOfDay)
        turnaroundSecondsFromNow = (hikingTime + timeToDeadline) / 2 - hikingTime
        if (turnaroundSecondsFromNow < 0) {
            timeString += " (now)"
        } else {
            timeString += " (" + secondsToHoursAndMinutes(turnaroundSecondsFromNow) + ")"
        }
    }
    Text(
        text = timeString,
        style = MaterialTheme.typography.headlineSmall
    )
    Text(
        text = "Turnaround time"
    )
    Spacer(modifier = Modifier.height(32.dp))
}

@Composable
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
}

@Composable
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
}

@Composable
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
}

enum class OnOffState {
    OFF, STARTING, ON, STOPPING
}

@OptIn(ExperimentalTime::class)
@Composable
fun Distance(
    myLocationViewModel: LocationViewModel,
    myDistanceViewModel: DistanceViewModel,
    myPressureViewModel: PressureViewModel,
    myFusedLocationProviderClient: FusedLocationProviderClient
) {
    val location by myLocationViewModel.locationState.collectAsStateWithLifecycle()
    val distance by myLocationViewModel.distanceState.collectAsStateWithLifecycle()
    val accuracy by myLocationViewModel.accuracyState.collectAsStateWithLifecycle()
    val speed by myLocationViewModel.speedState.collectAsStateWithLifecycle()
    val updateCount by myLocationViewModel.updateCountState.collectAsStateWithLifecycle()
    val deltaDistance by myLocationViewModel.deltaDistanceState.collectAsStateWithLifecycle()
    val subDeltaDistances by myLocationViewModel.subDeltaDistances.collectAsStateWithLifecycle()
    //val lifeCycleOwner = LocalLifecycleOwner.current

    // detect onStop call
    /*DisposableEffect(lifeCycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                myDistanceViewModel.distanceState.value = OnOffState.STOPPING
            }
        }
        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }*/

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = distance.toString() + " m",
                    //" " + subDeltaDistances +
                    //" " + deltaDistance.toString() +
                    //" " + accuracy.toString(),
                    //" " + speed.toString(),
                    //" " + updateCount.toString(),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.clickable {
                myDistanceViewModel.startChartActivity.value = true
            }
        )
        /*if (myDistanceViewModel.startChartActivity.value) {
            val intent = Intent(
                LocalContext.current,
                AltitudeChartActivity::class.java)
                .apply {
                }
            LocalContext.current.startActivity(intent)
            myDistanceViewModel.startChartActivity.value = false
        }*/
        Spacer(modifier = Modifier.padding(6.dp))
        Button(
            onClick = {
                when (myDistanceViewModel.distanceState.value) {
                    OnOffState.OFF -> {
                        myDistanceViewModel.distanceState.value = OnOffState.STARTING
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
        OnOffState.STARTING -> {
            val intent = Intent(
                LocalContext.current,
                LocationService::class.java).apply {
                action = "START"
            }
            intent.putExtra("sea_level_pressure", myPressureViewModel.seaLevelPressure.value)
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
fun AltitudeDistanceChart(
    viewModel: AltitudeDistanceChartViewModel = viewModel()
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val axisTitleComponent = rememberTextComponent()

    LaunchedEffect(Unit) {
        modelProducer.runTransaction {
            lineModel{ series(13, 8, 7, 12, 0, 1, 15, 14, 0, 11, 6, 12, 0, 11, 12, 11) }
        }
    }

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
                                .cubic(curvature = 0.38f)
                        )
                    )
                ),
                startAxis = VerticalAxis.rememberStart(
                    title = { "Altitude (m)" },
                    titleComponent = axisTitleComponent
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    title = { "Distance (m)" },
                    titleComponent = axisTitleComponent,
                    valueFormatter = { _, value, _ -> "${value.toInt()}" }
                ),
            ),
            modelProducer = viewModel.modelProducer,
        )
    }
}

fun secondsToTimeOfDay(seconds: Int): String {
    var timeString = String.format(Locale.US, "%02d", (seconds / 3600)) + ":"
    timeString += String.format(Locale.US, "%02d", ((seconds % 3600) / 60))
    return timeString
}

fun secondsToHoursAndMinutes(seconds: Int): String {
    var timeString = (seconds / 3600).toString() + " h "
    timeString += ((seconds % 3600) / 60).toString() + " m"
    return timeString
}

/*fun secondsToMinutesAndSeconds(seconds: Int): String {
    var timeString = ((seconds % 3600) / 60).toString() + "m "
    timeString += (seconds % 60).toString() + "s"
    return timeString
}*/
