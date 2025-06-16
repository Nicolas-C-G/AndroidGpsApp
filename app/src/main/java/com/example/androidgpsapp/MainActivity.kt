package com.example.androidgpsapp

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.androidgpsapp.ui.theme.AndroidGpsAppTheme
import com.google.android.gms.location.*
import kotlin.math.sin
import kotlin.math.cos
import androidx.compose.ui.graphics.nativeCanvas

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private lateinit var sensorManager: SensorManager
    private lateinit var sensorEventListener: SensorEventListener

    private var latitude by mutableStateOf("Loading...")
    private var longitude by mutableStateOf("Loading...")
    private val gpsTrail = mutableStateListOf<Pair<Double, Double>>()

    private var zoomLevel by mutableFloatStateOf(1.0f)
    private var azimuth by mutableFloatStateOf(0f)

    private var kalmanLat: KalmanFilter? = null
    private var kalmanLon: KalmanFilter? = null

    private var isLoading by mutableStateOf(true)
    private var hasResetTrail = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val rawLat = location.latitude.toFloat()
                    val rawLon = location.longitude.toFloat()

                    if (kalmanLat == null || kalmanLon == null) {
                        kalmanLat = KalmanFilter(0.0001f, 0.0005f, rawLat)
                        kalmanLon = KalmanFilter(0.0001f, 0.0005f, rawLon)
                    }

                    val filteredLat = kalmanLat!!.update(rawLat)
                    val filteredLon = kalmanLon!!.update(rawLon)

                    latitude = filteredLat.toString()
                    longitude = filteredLon.toString()

                    // Add to trail
                    gpsTrail.add(Pair(filteredLat.toDouble(), filteredLon.toDouble()))

                    // Update loading state
                    isLoading = gpsTrail.size < 30

                    if (hasResetTrail && gpsTrail.size >= 25){
                        gpsTrail.clear()
                        hasResetTrail = false
                    }
                }
            }
        }

        checkLocationPermission()
        setupCompass()

        setContent {
            AndroidGpsAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (isLoading) {
                        // Show loading spinner
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                            Text("Waiting for GPS initialization...",
                                modifier = Modifier.padding(top = 80.dp))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            LocationCanvas(
                                gpsTrail = gpsTrail,
                                zoomLevel = zoomLevel,
                                azimuth = azimuth
                            )

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                            ) {
                                Button(onClick = {
                                    zoomLevel = (zoomLevel * 1.2f).coerceIn(0.5f, 10f)
                                }) {
                                    Text("+")
                                }

                                Button(onClick = {
                                    zoomLevel = (zoomLevel / 1.2f).coerceIn(0.5f, 10f)
                                }) {
                                    Text("-")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        ).setMinUpdateIntervalMillis(500L).build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun setupCompass() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, event.values.size)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, event.values.size)
                    }
                }

                if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(sensorEventListener, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onStop() {
        super.onStop()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(sensorEventListener)
    }
}

@Composable
fun LocationCanvas(
    gpsTrail: List<Pair<Double, Double>>,
    zoomLevel: Float,
    azimuth: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (gpsTrail.size < 2) return@Canvas

        val minLat = gpsTrail.minOf { it.first }
        val maxLat = gpsTrail.maxOf { it.first }
        val minLon = gpsTrail.minOf { it.second }
        val maxLon = gpsTrail.maxOf { it.second }

        val baseScaleX = size.width / (maxLon - minLon)
        val baseScaleY = size.height / (maxLat - minLat)

        val scaleX = baseScaleX * zoomLevel
        val scaleY = baseScaleY * zoomLevel

        val scaleWidth = (maxLon - minLon) * scaleX
        val scaleHeight = (maxLat - minLat) * scaleY

        val offsetX = (size.width - scaleWidth) / 2
        val offsetY = (size.height - scaleHeight) / 2

        val path = Path().apply {
            val first = gpsTrail.first()
            moveTo(
                (offsetX + ((first.second - minLon) * scaleX)).toFloat(),
                size.height - (offsetY + ((first.first - minLat) * scaleY)).toFloat()
            )

            for (point in gpsTrail.drop(1)) {
                lineTo(
                    (offsetX + ((point.second - minLon) * scaleX)).toFloat(),
                    size.height - (offsetY + ((point.first - minLat) * scaleY)).toFloat()
                )
            }
        }

        drawPath(
            path = path,
            color = Color.Black,
            style = Stroke(width = 4f)
        )

        // Draw big circle on last point
        val last = gpsTrail.last()

        val lastX = (offsetX + ((last.second - minLon) * scaleX)).toFloat()
        val lastY = size.height - (offsetY + ((last.first - minLat) * scaleY)).toFloat()

        drawCircle(
            color = Color.Green,
            radius = 20f,
            center = Offset(lastX, lastY)
        )

        // Compass parameters
        val compassRadius = 40f
        val compassCenterX = size.width - compassRadius - 16f  // 16f = padding from the edge
        val compassCenterY = compassRadius + 16f

        // Draw compass circle
        drawCircle(
            color = Color.Gray,
            radius = compassRadius,
            center = Offset(compassCenterX, compassCenterY),
            style = Stroke(width = 3f)
        )

        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 24f
            textAlign = android.graphics.Paint.Align.CENTER
        }

        // N
        drawContext.canvas.nativeCanvas.drawText(
            "N",
            compassCenterX,
            compassCenterY - compassRadius + 20f,
            textPaint
        )

        // S
        drawContext.canvas.nativeCanvas.drawText(
            "S",
            compassCenterX,
            compassCenterY + compassRadius - 8f,
            textPaint
        )

        // E
        drawContext.canvas.nativeCanvas.drawText(
            "E",
            compassCenterX + compassRadius - 8f,
            compassCenterY + 8f,
            textPaint
        )

        // W
        drawContext.canvas.nativeCanvas.drawText(
            "W",
            compassCenterX - compassRadius + 8f,
            compassCenterY + 8f,
            textPaint
        )

        // Draw compass arrow (North indicator)
        val compassAngleRad = Math.toRadians(azimuth.toDouble())
        val compassTipX = compassCenterX + (compassRadius * 0.8f * sin(compassAngleRad)).toFloat()
        val compassTipY = compassCenterY - (compassRadius * 0.8f * cos(compassAngleRad)).toFloat()

        drawLine(
            color = Color.Red,
            start = Offset(compassCenterX, compassCenterY),
            end = Offset(compassTipX, compassTipY),
            strokeWidth = 3f
        )

    }
}
