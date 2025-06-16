package com.example.androidgpsapp

class KalmanFilter (
    private val q: Float, // process noise covariance
    private val r: Float, // measurement noise covariance
    initialX: Float, // estimated value
    initialP: Float = 1f // estimation error covariance
) {

    private var x: Float = initialX
    private var p: Float = initialP

    fun update(measurement: Float): Float {
        // Prediction update
        p += q

        // Measurement update
        val k = p / (p + r) //Kalman gain
        x += k * (measurement - x)
        p *= (1 - k)

        return x
    }
}