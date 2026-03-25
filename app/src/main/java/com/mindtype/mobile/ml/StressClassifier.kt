package com.mindtype.mobile.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

enum class StressLevel { CALM, MILD_STRESS, HIGH_STRESS }

/**
 * Wraps the TensorFlow Lite model for on-device stress classification.
 * Input:  FloatArray[9]  — the 9 behavioral features (in order from FeatureExtractor)
 * Output: FloatArray[3]  — softmax probabilities [Calm, Mild_Stress, High_Stress]
 */
class StressClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null

    init {
        try {
            val model = loadModelFile()
            interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(2) })
        } catch (e: Exception) {
            // Model placeholder may not be a real TFLite model yet — fail gracefully
            interpreter = null
        }
    }

    fun classify(features: FloatArray): StressLevel {
        val interp = interpreter ?: return StressLevel.CALM   // fallback if no real model yet

        val input = arrayOf(features)
        val output = Array(1) { FloatArray(3) }
        interp.run(input, output)

        val probs = output[0]
        return when (probs.indices.maxByOrNull { probs[it] } ?: 0) {
            0 -> StressLevel.CALM
            1 -> StressLevel.MILD_STRESS
            else -> StressLevel.HIGH_STRESS
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd("mindtype_model.tflite")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun close() {
        interpreter?.close()
    }
}
