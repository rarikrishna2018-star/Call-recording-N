package com.example.callrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val prefs = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", true)) return

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call answered (incoming) or connected/dialing (outgoing)
                if (!RecorderService.running) {
                    try {
                        context.startForegroundService(
                            Intent(context, RecorderService::class.java)
                                .setAction(RecorderService.ACTION_START)
                        )
                    } catch (e: Exception) {
                        // Android blocked background start: grant "Display over other apps"
                    }
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (RecorderService.running) {
                    context.startService(
                        Intent(context, RecorderService::class.java)
                            .setAction(RecorderService.ACTION_STOP)
                    )
                }
            }
        }
    }
}
