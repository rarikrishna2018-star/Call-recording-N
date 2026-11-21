package com.example.callrecorder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)
        val pad = (20 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.TOP
        }

        root.addView(TextView(this).apply {
            text = "Call Recorder"
            textSize = 26f
        })

        status = TextView(this).apply { textSize = 15f; setPadding(0, pad / 2, 0, pad / 2) }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "1. Grant permissions"
            setOnClickListener {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.POST_NOTIFICATIONS
                    ), 1
                )
            }
        })

        root.addView(Button(this).apply {
            text = "2. Allow display over other apps"
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        })

        root.addView(Button(this).apply {
            text = "3. Disable battery optimization"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        })

        root.addView(Switch(this).apply {
            text = "Auto-record calls   "
            textSize = 18f
            isChecked = prefs.getBoolean("enabled", true)
            setPadding(0, pad, 0, pad)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean("enabled", on).apply()
            }
        })

        root.addView(TextView(this).apply {
            text = "Recordings are saved in:\nInternal storage > Recordings > CallRecords\n" +
                "File name: IN_number_date.m4a or OUT_number_date.m4a"
            textSize = 14f
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val ok = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        ).all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        status.text = "Permissions: " + (if (ok) "OK" else "MISSING") +
            "\nOverlay: " + (if (Settings.canDrawOverlays(this)) "OK" else "MISSING")
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(c, p, r)
        onResume()
    }
}
