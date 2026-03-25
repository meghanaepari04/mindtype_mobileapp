package com.mindtype.mobile.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reschedules WorkManager stress prompts after device reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            StressLabelWorker.schedule(context)
        }
    }
}
