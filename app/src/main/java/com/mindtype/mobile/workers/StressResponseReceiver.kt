package com.mindtype.mobile.workers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.mindtype.mobile.data.AppDatabase
import com.mindtype.mobile.data.entity.StressLabelEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Receives the user's stress score selection from the notification buttons.
 * Maps raw score (1–5) to class label and persists to Room DB.
 */
class StressResponseReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION = "com.mindtype.mobile.STRESS_RESPONSE"
        const val EXTRA_SCORE = "score"
        const val EXTRA_SESSION_ID = "session_id"

        fun buildIntent(context: Context, score: Int, sessionId: String): Intent =
            Intent(context, StressResponseReceiver::class.java).apply {
                action = ACTION
                putExtra(EXTRA_SCORE, score)
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val score = intent.getIntExtra(EXTRA_SCORE, -1)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        if (score == -1) return

        val mappedClass = StressLabelEntity.mapScoreToClass(score)
        val label = StressLabelEntity(
            sessionId = sessionId,
            rawScore = score,
            mappedClass = mappedClass
        )

        // Cancel the notification manually since Action buttons don't auto-dismiss it
        context.getSystemService(NotificationManager::class.java).cancel(2001)
        
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getInstance(context).stressLabelDao().insert(label)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Stress level recorded", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
