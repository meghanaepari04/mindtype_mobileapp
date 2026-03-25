package com.mindtype.mobile.export

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.mindtype.mobile.data.AppDatabase
import java.io.File
import java.io.FileWriter

/**
 * Queries all Room tables and exports a labeled CSV dataset to:
 * Downloads/mindtype_mobile_dataset.csv
 *
 * CSV columns: user_id, session_id, window_start, window_end,
 *              mean_dwell, std_dwell, mean_flight, std_flight,
 *              typing_speed, backspace_rate, pause_count,
 *              mean_pressure, gyro_std, raw_score, mapped_class, predicted_class
 */
class DataExporter(private val context: Context) {

    suspend fun exportCsv(): Boolean {
        val db = AppDatabase.getInstance(context)
        val windows = db.featureWindowDao().getAllWindows()
        val labels = db.stressLabelDao().getAllLabels()
        val prefs = context.getSharedPreferences("mindtype_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", "UNKNOWN") ?: "UNKNOWN"
        val sessionId = prefs.getString("current_session_id", "") ?: ""

        if (windows.isEmpty()) return false

        // Map window_id → stress label (match by closest timestamp)
        val labelMap = labels.associateBy { it.timestamp }

        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "mindtype_mobile_dataset.csv"
        )

        FileWriter(file).use { writer ->
            // Header
            writer.appendLine(
                "user_id,session_id,window_start,window_end," +
                "mean_dwell,std_dwell,mean_flight,std_flight," +
                "typing_speed,backspace_rate,pause_count," +
                "mean_pressure,gyro_std," +
                "raw_score,mapped_class,predicted_class"
            )

            for (w in windows) {
                // Find closest label within 10 minutes of window end
                val matchedLabel = labels.minByOrNull {
                    kotlin.math.abs(it.timestamp - w.windowEnd)
                }?.takeIf { kotlin.math.abs(it.timestamp - w.windowEnd) < 600_000L }

                writer.appendLine(
                    "${userId},${w.sessionId},${w.windowStart},${w.windowEnd}," +
                    "${w.meanDwell},${w.stdDwell},${w.meanFlight},${w.stdFlight}," +
                    "${w.typingSpeed},${w.backspaceRate},${w.pauseCount}," +
                    "${w.meanPressure},${w.gyroStd}," +
                    "${matchedLabel?.rawScore ?: ""},${matchedLabel?.mappedClass ?: ""},${w.predictedClass}"
                )
            }
        }

        // Trigger Android ShareSheet
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MindType Dataset — $userId")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share MindType Dataset").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return true
    }
}
