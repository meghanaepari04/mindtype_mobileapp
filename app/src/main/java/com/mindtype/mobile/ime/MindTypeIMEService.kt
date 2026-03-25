package com.mindtype.mobile.ime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.app.NotificationCompat
import com.mindtype.mobile.R
import com.mindtype.mobile.data.AppDatabase
import com.mindtype.mobile.data.entity.KeystrokeEventEntity
import com.mindtype.mobile.features.FeatureExtractor
import com.mindtype.mobile.ml.StressClassifier
import com.mindtype.mobile.ml.StressLevel
import com.mindtype.mobile.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MindTypeIMEService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "mindtype_stress_channel"
        var currentStressLevel: StressLevel = StressLevel.CALM
    }

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard
    private lateinit var gyroscopeManager: GyroscopeManager
    private lateinit var featureExtractor: FeatureExtractor
    private lateinit var stressClassifier: StressClassifier
    private lateinit var db: AppDatabase

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var lastEventTime: Long = 0L
    private var sessionId: String = ""
    private var userId: String = ""

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getInstance(applicationContext)
        featureExtractor = FeatureExtractor()
        stressClassifier = StressClassifier(applicationContext)
        gyroscopeManager = GyroscopeManager(
            getSystemService(Context.SENSOR_SERVICE) as SensorManager
        )
        gyroscopeManager.start()

        val prefs = getSharedPreferences("mindtype_prefs", Context.MODE_PRIVATE)
        userId = prefs.getString("user_id", "U00") ?: "U00"
        sessionId = prefs.getString("current_session_id", "") ?: ""

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 34) { // Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            startForeground(
                NOTIFICATION_ID, 
                buildNotification(StressLevel.CALM), 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(StressLevel.CALM))
        }
    }

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
        keyboard = Keyboard(this, R.xml.keys_layout)
        keyboardView.keyboard = keyboard
        keyboardView.isPreviewEnabled = false
        keyboardView.setOnKeyboardActionListener(this)
        return keyboardView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        featureExtractor.resetWindow()
        lastEventTime = 0L
    }

    // ─── Key events ──────────────────────────────────────────────────────────────

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val downTime = System.currentTimeMillis()
        processKeyEvent(
            keyCode = primaryCode,
            downTime = downTime,
            eventTime = System.currentTimeMillis(),
            pressure = 0.5f,   // KeyboardView doesn't expose pressure — use MotionEvent if overriding onTouchEvent
            touchSize = 0.5f,
            isBackspace = (primaryCode == Keyboard.KEYCODE_DELETE)
        )
        // Forward key to the currently connected app
        val ic = currentInputConnection ?: return
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            ic.deleteSurroundingText(1, 0)
        } else if (primaryCode == Keyboard.KEYCODE_DONE) {
            ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
        } else {
            ic.commitText(primaryCode.toChar().toString(), 1)
        }
    }

    fun processKeyEvent(
        keyCode: Int,
        downTime: Long,
        eventTime: Long,
        pressure: Float,
        touchSize: Float,
        isBackspace: Boolean
    ) {
        // NEVER store the actual character — only codes and timestamps
        val dwellTime = (eventTime - downTime).toFloat()
        val flightTime = if (lastEventTime > 0L) (downTime - lastEventTime).toFloat() else 0f
        lastEventTime = eventTime

        val event = KeystrokeEventEntity(
            sessionId = sessionId,
            userId = userId,
            timestamp = System.currentTimeMillis(),
            keyCode = keyCode,
            downTime = downTime,
            eventTime = eventTime,
            dwellTime = dwellTime,
            flightTime = flightTime,
            touchPressure = pressure,
            touchSize = touchSize,
            isBackspace = if (isBackspace) 1 else 0
        )

        serviceScope.launch {
            db.keystrokeEventDao().insert(event)
        }

        val gyroReadings = gyroscopeManager.getRecentReadings()
        featureExtractor.addEvent(event, gyroReadings)

        // Trigger inference every 60 seconds
        featureExtractor.getWindowIfReady()?.let { window ->
            serviceScope.launch {
                val windowEntity = window.copy(sessionId = sessionId)
                val stressLevel = stressClassifier.classify(window.toFeatureArray())
                val updatedWindow = windowEntity.copy(predictedClass = stressLevel.name)
                db.featureWindowDao().insert(updatedWindow)
                currentStressLevel = stressLevel
                updateNotification(stressLevel)
            }
        }
    }

    // ─── Notification ────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MindType Stress Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows your current stress indicator" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(level: StressLevel): Notification {
        val (emoji, label) = when (level) {
            StressLevel.CALM -> Pair("🟢", "Calm")
            StressLevel.MILD_STRESS -> Pair("🟡", "Mild Stress")
            StressLevel.HIGH_STRESS -> Pair("🔴", "High Stress")
        }
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("MindType $emoji")
            .setContentText("Current Stress Level: $label")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
    }

    private fun updateNotification(level: StressLevel) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(level))
    }

    // ─── KeyboardView.OnKeyboardActionListener stubs ─────────────────────────────

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}

    override fun onDestroy() {
        super.onDestroy()
        gyroscopeManager.stop()
        stressClassifier.close()
        serviceJob.cancel()
    }
}
