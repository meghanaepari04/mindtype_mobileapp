package com.mindtype.mobile.ui

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.mindtype.mobile.R
import com.mindtype.mobile.data.AppDatabase
import com.mindtype.mobile.databinding.ActivityMainBinding
import com.mindtype.mobile.ime.MindTypeIMEService
import com.mindtype.mobile.ml.StressLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(applicationContext)

        binding.btnSettings.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }

        // Refresh dashboard every 30 seconds
        lifecycleScope.launch {
            while (isActive) {
                loadDashboard()
                delay(30_000)
            }
        }
    }

    private suspend fun loadDashboard() {
        val prefs = getSharedPreferences("mindtype_prefs", Context.MODE_PRIVATE)
        val sessionId = prefs.getString("current_session_id", "") ?: ""
        val userId = prefs.getString("user_id", "—") ?: "—"

        val since24h = System.currentTimeMillis() - 24 * 60 * 60 * 1000L

        val keystrokeCount = withContext(Dispatchers.IO) {
            db.keystrokeEventDao().countSince(since24h)
        }
        val windows = withContext(Dispatchers.IO) {
            db.featureWindowDao().getWindowsSince(since24h)
        }
        val sessionEntity = withContext(Dispatchers.IO) {
            db.sessionDao().getSessionsForUser(userId).firstOrNull()
        }

        val sessionDurationMin = sessionEntity?.let {
            val endMs = it.endTime ?: System.currentTimeMillis()
            (endMs - it.startTime) / 60_000L
        } ?: 0L

        val stressValues = windows.mapNotNull { w ->
            when (w.predictedClass) {
                "CALM" -> 1f
                "STRESSED" -> 2f
                else -> null
            }
        }
        val avgStress = stressValues.average().let { if (it.isNaN()) 0.0 else it }

        val currentLevel = MindTypeIMEService.currentStressLevel

        withContext(Dispatchers.Main) {
            binding.tvUserId.text = "PARTICIPANT: $userId"
            binding.tvKeystrokeCount.text = "$keystrokeCount"
            binding.tvSessionDuration.text = "${sessionDurationMin}m"
            binding.tvAvgStress.text = "%.1f".format(avgStress)

            // Current stress dot
            val (color, label) = when (currentLevel) {
                StressLevel.CALM -> Pair(getColor(R.color.stress_calm), "🟢 Calm")
                StressLevel.STRESSED -> Pair(getColor(R.color.stress_high), "🔴 Stressed")
            }
            binding.tvCurrentStress.text = label
            binding.tvCurrentStress.setTextColor(color)

            // Avg score badge on chart card
            binding.tvChartAvgScore.text = "AVG: %.2f".format(avgStress)

            // Build interactive stress trend chart
            buildStressChart(stressValues, avgStress.toFloat())
        }
    }

    private fun buildStressChart(stressValues: List<Float>, avgScore: Float) {
        val cyanColor = getColor(R.color.chart_cyan)
        val cyanFill      = getColor(R.color.chart_cyan_fill)
        val avgColor      = getColor(R.color.chart_avg_line)
        val axisTextColor = getColor(R.color.text_medium_emphasis)

        if (stressValues.isEmpty()) {
            binding.stressChart.clear()
            binding.stressChart.setNoDataText("No stress data yet — start typing!")
            binding.stressChart.setNoDataTextColor(axisTextColor)
            binding.stressChart.invalidate()
            return
        }

        // Main stress line entries
        val entries = stressValues.mapIndexed { i, v -> Entry(i.toFloat(), v) }

        val dataSet = LineDataSet(entries, "Stress Level").apply {
            setColor(cyanColor)
            setCircleColor(cyanColor)
            lineWidth = 2.5f
            circleRadius = 3.5f
            setDrawCircles(true)
            setDrawCircleHole(true)
            circleHoleColor = getColor(R.color.primary_surface)
            circleHoleRadius = 2f
            setDrawFilled(true)
            fillColor = cyanColor
            fillAlpha = 50
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f

            // Highlight on touch
            highLightColor = cyanColor
            highlightLineWidth = 1.5f
            setDrawHorizontalHighlightIndicator(false)
            enableDashedHighlightLine(10f, 5f, 0f)
        }

        binding.stressChart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false

            // Interactive
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            // Background
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)

            // Extra padding for axis labels
            extraBottomOffset = 12f
            extraLeftOffset = 8f

            // ─── X Axis (bottom) — "Window #" ───────────────
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                gridColor = Color.parseColor("#1AFFFFFF")  // subtle grid
                gridLineWidth = 0.5f
                setTextColor(axisTextColor)
                textSize = 11f
                granularity = 1f
                labelRotationAngle = 0f
                setDrawAxisLine(true)
                axisLineColor = axisTextColor
                axisLineWidth = 1f

                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "W${value.toInt() + 1}"
                    }
                }
            }

            // ─── Y Axis (left) — "Stress Level" ─────────────
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#1AFFFFFF")
                gridLineWidth = 0.5f
                setTextColor(axisTextColor)
                textSize = 11f
                axisMinimum = 0.5f
                axisMaximum = 2.5f
                granularity = 1f
                setDrawAxisLine(true)
                axisLineColor = axisTextColor
                axisLineWidth = 1f
                setLabelCount(2, true)

                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return when (value.toInt()) {
                            1 -> "Calm"
                            2 -> "Stressed"
                            else -> ""
                        }
                    }
                }

                // Avg score horizontal limit line
                removeAllLimitLines()
                val avgLine = LimitLine(avgScore, "Avg: %.2f".format(avgScore)).apply {
                    lineColor = avgColor
                    lineWidth = 1.5f
                    setTextColor(avgColor)
                    textSize = 10f
                    labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                    enableDashedLine(15f, 8f, 0f)
                }
                addLimitLine(avgLine)
            }

            axisRight.isEnabled = false

            // Animate
            animateX(800)
            invalidate()
        }
    }
}
