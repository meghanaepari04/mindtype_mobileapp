package com.mindtype.mobile.ime

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.LinkedList

/**
 * Captures gyroscope Z-axis readings at ~50Hz during active typing.
 * Used for hand tremor analysis as part of the 9-feature vector.
 */
class GyroscopeManager(private val sensorManager: SensorManager) : SensorEventListener {

    private val readings = LinkedList<Float>()
    private val maxReadings = 3000  // ~60s at 50Hz

    fun start() {
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        gyro?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun getRecentReadings(): List<Float> {
        return readings.toList()
    }

    fun clearReadings() {
        readings.clear()
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Z-axis rotation (index 2)
        readings.addLast(event.values[2])
        if (readings.size > maxReadings) readings.removeFirst()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
