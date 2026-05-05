package com.mindtype.mobile.ml

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

enum class StressLevel { CALM, STRESSED }

/**
 * Wraps the ONNX Runtime model for on-device binary stress classification.
 * Input:  FloatArray[8]  — 8 behavioral features:
 *   mean_dwell, std_dwell, mean_flight, std_flight,
 *   typing_speed, backspace_rate, combined_behavior, dwell_flight_ratio
 * Output: 0 = CALM, 1 = STRESSED
 *
 * Feature engineering (must match train_and_export.py):
 *   combined_behavior  = (mean_dwell * typing_speed) / (backspace_rate + 1e-6)
 *   dwell_flight_ratio = mean_dwell / (mean_flight + 1e-6)
 */
class StressClassifier(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    init {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open("mindtype_model.onnx").readBytes()
            ortSession = ortEnv!!.createSession(modelBytes, OrtSession.SessionOptions())
        } catch (e: Exception) {
            ortSession = null
        }
    }

    /**
     * Classify stress from the 9 raw features produced by FeatureExtractor.
     * Internally computes the 2 engineered features and feeds 8 features to the model.
     */
    fun classify(features: FloatArray): StressLevel {
        // features[0..8] = mean_dwell, std_dwell, mean_flight, std_flight,
        //                   typing_speed, backspace_rate, pause_count,
        //                   mean_pressure, gyro_std

        val meanDwell     = features[0]
        val meanFlight    = features[2]
        val typingSpeed   = features[4]
        val backspaceRate = features[5]

        // Compute engineered features (same formula as train_and_export.py)
        val combinedBehavior  = (meanDwell * typingSpeed) / (backspaceRate + 1e-6f)
        val dwellFlightRatio  = meanDwell / (meanFlight + 1e-6f)

        // Build 8-feature input vector
        val input = floatArrayOf(
            meanDwell, features[1], meanFlight, features[3],
            typingSpeed, backspaceRate,
            combinedBehavior, dwellFlightRatio
        )

        ortSession?.let { session ->
            try {
                val env = ortEnv ?: return heuristic(features)
                val inputName = session.inputNames.iterator().next()
                val tensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(input),
                    longArrayOf(1, 8)
                )
                val result = session.run(mapOf(inputName to tensor))
                val label = (result[0].value as LongArray)[0]
                tensor.close()
                result.close()
                return if (label == 0L) StressLevel.CALM else StressLevel.STRESSED
            } catch (e: Exception) {
                // Fall through to heuristic
            }
        }

        return heuristic(features)
    }

    /** Fallback heuristic when ONNX model is unavailable */
    private fun heuristic(features: FloatArray): StressLevel {
        val backspaceRate = features[5]
        val gyroStd = features[8]
        val speedKPM = features[4]

        var score = 0
        if (backspaceRate > 0.08f) score += 3
        else if (backspaceRate > 0.04f) score += 1
        if (gyroStd > 1.2f) score += 2
        else if (gyroStd > 0.4f) score += 1
        if (speedKPM > 140f || (speedKPM < 40f && speedKPM > 5f)) score += 1

        return if (score >= 2) StressLevel.STRESSED else StressLevel.CALM
    }

    fun close() {
        ortSession?.close()
        ortEnv?.close()
    }
}
