package com.subhadip.dynamicisland

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val bg = Color.rgb(7, 8, 12)
    private val accent = Color.rgb(89, 230, 210)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 36, 28, 36)
            setBackgroundColor(bg)
        }
        val title = TextView(this).apply {
            text = "DYNAMIC ISLAND"
            textSize = 25f
            setTextColor(Color.WHITE)
            letterSpacing = .14f
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Your phone. Your island.\nA smarter AMOLED experience."
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 28)
        }
        val status = TextView(this).apply {
            text = permissionSummary()
            textSize = 13f
            setTextColor(accent)
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 24)
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(actionButton("1 · Allow display over other apps") {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(actionButton("2 · Enable notification access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        root.addView(actionButton("3 · Allow phone permission") {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 41)
        })
        if (Build.VERSION.SDK_INT >= 33) root.addView(actionButton("4 · Allow notifications") {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        })
        root.addView(actionButton("START DYNAMIC ISLAND", true) {
            if (!Settings.canDrawOverlays(this)) status.text = "Please allow display over other apps first."
            else {
                val i = Intent(this, IslandOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                status.text = "Island is running. Return here to stop it."
            }
        })
        root.addView(actionButton("STOP DYNAMIC ISLAND") { stopService(Intent(this, IslandOverlayService::class.java)) })
        root.addView(TextView(this).apply {
            text = "Notification and media details stay on this device.\nA persistent Android service notification is normal."
            textSize = 11f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 26, 0, 0)
        })
        setContentView(root)
    }

    private fun permissionSummary(): String {
        val overlay = if (Settings.canDrawOverlays(this)) "Overlay ✓" else "Overlay needed"
        val listener = if (Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true) "Notifications ✓" else "Notification access needed"
        return "$overlay     •     $listener"
    }

    private fun actionButton(label: String, primary: Boolean = false, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTextColor(if (primary) Color.BLACK else Color.WHITE)
            setBackgroundColor(if (primary) accent else Color.rgb(29, 32, 41))
            setOnClickListener { action() }
            val p = LinearLayout.LayoutParams(-1, -2)
            p.setMargins(0, 7, 0, 7)
            layoutParams = p
        }
}
