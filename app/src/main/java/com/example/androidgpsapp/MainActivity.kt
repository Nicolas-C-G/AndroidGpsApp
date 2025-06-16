package com.example.androidgpsapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.androidgpsapp.ui.theme.AndroidGpsAppTheme
import com.google.android.gms.location.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp



class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var latitude by mutableStateOf("Loading...")
    private var longitude by mutableStateOf("Loading...")
    private val gpsTrail = mutableStateListOf<Pair<Double, Double>>()

    private var zoomLevel by mutableFloatStateOf(1.0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val lat = location.latitude
                    val lon = location.longitude
                    latitude  = lat.toString()
                    longitude = lon.toString()
                    gpsTrail.add(Pair(lat, lon))
                }
            }
        }

        checkLocationPermission()

        setContent {
            AndroidGpsAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    androidx.compose.foundation.layout.Box(modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)) {

                        LocationCanvas(
                            gpsTrail = gpsTrail,
                            zoomLevel = zoomLevel
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ){
                            Button(onClick = {
                                zoomLevel = (zoomLevel * 1.2f).coerceIn(0.5f, 10f)
                            }) {
                                Text("+")
                            }

                            Button(onClick = {
                                zoomLevel = (zoomLevel / 1.2f ).coerceIn(0.5f, 10f)
                            }) {
                                Text("-")
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
            3000L // 3 seconds between updates
        ).setMinUpdateIntervalMillis(1500L).build()

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

    override fun onStop() {
        super.onStop()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

@Composable
fun LocationCanvas(
    gpsTrail: List<Pair<Double, Double>>,
    zoomLevel: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (gpsTrail.size < 2) return@Canvas

        // Get bounds
        val minLat = gpsTrail.minOf { it.first }
        val maxLat = gpsTrail.maxOf { it.first }
        val minLon = gpsTrail.minOf { it.second }
        val maxLon = gpsTrail.maxOf { it.second }

        val baseScaleX = size.width / (maxLon - minLon)
        val baseScaleY = size.height / (maxLat - minLat)

        val scaleX = baseScaleX * zoomLevel
        val scaleY = baseScaleY * zoomLevel

        // Build the path
        val path = Path().apply {
            val first = gpsTrail.first()
            moveTo(
                ((first.second - minLon) * scaleX).toFloat(),
                size.height - ((first.first - minLat) * scaleY).toFloat()
            )

            for (point in gpsTrail.drop(1)) {
                lineTo(
                    ((point.second - minLon) * scaleX).toFloat(),
                    size.height - ((point.first - minLat) * scaleY).toFloat()
                )
            }
        }

        drawPath(
            path = path,
            color = Color.Black,
            style = Stroke(width = 4f)
        )
    }
}
