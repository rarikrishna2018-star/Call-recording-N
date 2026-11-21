package com.example.callrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.CallLog
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecorderService : Service() {

    companion object {
        @Volatile var running = false
        const val ACTION_START = "com.example.callrecorder.START"
        const val ACTION_STOP = "com.example.callrecorder.STOP"
        private const val CHANNEL = "rec"
    }

    private var recorder: MediaRecorder? = null
    private var uri: Uri? = null
    private var pfd: ParcelFileDescriptor? = null
    private var startTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopRecording() else startRecording()
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (running) return

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Call recording", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Recording call")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        try {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            stopSelf(); return
        }

        try {
            startTime = System.currentTimeMillis()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(startTime))
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "CALL_$stamp.m4a")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_RECORDINGS + "/CallRecords"
                )
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            pfd = contentResolver.openFileDescriptor(uri!!, "w")

            recorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(pfd!!.fileDescriptor)
                prepare()
                start()
            }
            running = true
        } catch (e: Exception) {
            cleanupFailed()
        }
    }

    private fun cleanupFailed() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { pfd?.close() } catch (_: Exception) {}
        uri?.let { try { contentResolver.delete(it, null, null) } catch (_: Exception) {} }
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopRecording() {
        if (!running) { stopSelf(); return }
        running = false
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { pfd?.close() } catch (_: Exception) {}
        pfd = null

        val u = uri
        val st = startTime
        Thread {
            if (u != null) renameWithNumber(u, st)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }.start()
    }

    /** Looks up the number in the call log and renames the file: IN_/OUT_ + number + date. */
    private fun renameWithNumber(u: Uri, since: Long) {
        var number = "Unknown"
        var dir = "CALL"
        // Call log entry is written shortly after the call ends, so retry a few times.
        for (i in 0 until 4) {
            Thread.sleep(1500)
            val r = lastCall(since)
            if (r != null) {
                val clean = r.first.filter { it.isDigit() || it == '+' }
                if (clean.isNotEmpty()) number = clean
                dir = when (r.second) {
                    CallLog.Calls.INCOMING_TYPE -> "IN"
                    CallLog.Calls.OUTGOING_TYPE -> "OUT"
                    else -> "CALL"
                }
                break
            }
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(since))
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "${dir}_${number}_$stamp.m4a")
            put(MediaStore.Audio.Media.IS_PENDING, 0)
        }
        try { contentResolver.update(u, values, null, null) } catch (_: Exception) {}
    }

    private fun lastCall(since: Long): Pair<String, Int>? {
        try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf((since - 180_000).toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                if (c.moveToFirst()) return Pair(c.getString(0) ?: "", c.getInt(1))
            }
        } catch (_: SecurityException) {
        }
        return null
    }
}
