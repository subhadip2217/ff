package com.subhadip.dynamicisland

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Rect
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.*
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*

class IslandOverlayService : Service() {
    companion object {
        const val ACTION_NOTIFICATION = "com.subhadip.dynamicisland.NOTIFICATION"
        const val ACTION_LISTENER_READY = "com.subhadip.dynamicisland.LISTENER_READY"
        private const val CHANNEL = "dynamic_island_service"
        private const val NOTIFICATION_ID = 1001
    }
    private lateinit var wm: WindowManager
    private lateinit var audio: AudioManager
    private var root: LinearLayout? = null
    private var expanded = false
    private var latestTitle = "Dynamic Island"
    private var latestText = "Tap to expand"
    private var battery = 100
    private var charging = false
    private var callState = "No active call"
    private var mediaController: MediaController? = null
    private var titleView: TextView? = null
    private var detailView: TextView? = null
    private var detailPanel: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var mediaArtworkView: ImageView? = null
    private var mediaTitleView: TextView? = null
    private var mediaPlayView: TextView? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_NOTIFICATION -> {
                    latestTitle = intent.getStringExtra("title") ?: "Notification"
                    latestText = intent.getStringExtra("text") ?: ""
                    refresh()
                    pulse()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    battery = intent.getIntExtra("level", battery)
                    charging = intent.getIntExtra("status", 0) == 2 || intent.getIntExtra("status", 0) == 5
                    refresh()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Dynamic Island service", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Dynamic Island is active")
            .setContentText("Tap the floating pill to expand controls.")
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, notification)

        val filter = IntentFilter().apply {
            addAction(ACTION_NOTIFICATION)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
        setupCalls()
        setupMedia()
        if (Settings.canDrawOverlays(this)) showIsland()
    }

    private fun setupCalls() {
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        try {
            val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            tm.listen(object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    callState = when (state) {
                        TelephonyManager.CALL_STATE_RINGING -> "Incoming call"
                        TelephonyManager.CALL_STATE_OFFHOOK -> "Call in progress"
                        else -> "No active call"
                    }
                    refresh()
                }
            }, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (_: SecurityException) { }
    }

    private fun setupMedia() {
        try {
            val msm = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            mediaController = msm.getActiveSessions(ComponentName(this, IslandNotificationListener::class.java)).firstOrNull()
        } catch (_: Exception) { mediaController = null }
    }

    private fun showIsland() {
        if (root != null) return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(15), dp(9), dp(15), dp(9))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dp(30).toFloat()
                setStroke(dp(1), Color.rgb(37, 68, 69))
            }
            elevation = dp(12).toFloat()
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val dot = TextView(this).apply { text = "●"; textSize = 9f; setTextColor(Color.rgb(89, 230, 210)); setPadding(0, 0, dp(8), 0) }
        titleView = TextView(this).apply { text = "DYNAMIC ISLAND"; textSize = 12f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; maxLines = 1 }
        val arrow = TextView(this).apply { text = "⌄"; textSize = 19f; setTextColor(Color.rgb(89, 230, 210)); setPadding(dp(12), 0, 0, 0) }
        top.addView(dot)
        top.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(arrow)
        container.addView(top)
        detailPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(0, dp(12), 0, 0) }
        detailView = TextView(this).apply { textSize = 12f; setTextColor(Color.LTGRAY); maxLines = 3 }
        detailPanel!!.addView(detailView)
        detailPanel!!.addView(makeMediaRow())
        detailPanel!!.addView(makeVolumeRow())
        container.addView(detailPanel)
        container.setOnClickListener { toggleExpanded() }
        container.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE && expanded) { collapse(); true } else false
        }
        params = WindowManager.LayoutParams(dp(210), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = cameraCutoutOffset() }
        try {
            wm.addView(container, params)
            root = container
            refresh()
        } catch (_: Exception) { }
    }

    private fun makeMediaRow(): LinearLayout {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val artwork = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(35,35,40)); cornerRadius = dp(9).toFloat() }
        }
        mediaArtworkView = artwork
        row.addView(artwork)
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(9), 0, dp(4), 0) }
        mediaTitleView = TextView(this).apply { text = "Nothing playing"; textSize = 11f; setTextColor(Color.WHITE); maxLines = 1 }
        val artist = TextView(this).apply { text = "Start music on your phone"; textSize = 10f; setTextColor(Color.LTGRAY); maxLines = 1 }
        labels.addView(mediaTitleView); labels.addView(artist)
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(controlButton("‹") { mediaController?.transportControls?.skipToPrevious() })
        mediaPlayView = controlButton("▶") {
            val state = mediaController?.playbackState?.state
            if (state == android.media.session.PlaybackState.STATE_PLAYING) mediaController?.transportControls?.pause()
            else mediaController?.transportControls?.play()
            setupMedia()
        }
        row.addView(mediaPlayView)
        row.addView(controlButton("›") { mediaController?.transportControls?.skipToNext() })
        return row
    }

    private fun makeVolumeRow(): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply { text = "VOL"; textSize = 10f; setTextColor(Color.GRAY) })
        val slider = SeekBar(this).apply {
            max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) audio.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        row.addView(slider, LinearLayout.LayoutParams(0, dp(32), 1f))
        return row
    }

    private fun controlButton(label: String, click: () -> Unit) = TextView(this).apply {
        text = label; textSize = 16f; gravity = Gravity.CENTER
        setTextColor(Color.rgb(89, 230, 210))
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener { click() }
    }

    private fun toggleExpanded() { if (expanded) collapse() else expand() }
    private fun expand() {
        expanded = true
        detailPanel?.visibility = View.VISIBLE
        params?.let { it.width = dp(310); try { root?.let { v -> wm.updateViewLayout(v, it) } } catch (_: Exception) {} }
        refresh()
        root?.animate()?.scaleX(1.02f)?.scaleY(1.02f)?.setDuration(220)?.setInterpolator(DecelerateInterpolator())?.start()
    }
    private fun collapse() {
        expanded = false
        detailPanel?.visibility = View.GONE
        params?.let { it.width = dp(194); try { root?.let { v -> wm.updateViewLayout(v, it) } } catch (_: Exception) {} }
        root?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(200)?.setInterpolator(DecelerateInterpolator())?.start()
    }
    private fun cameraCutoutOffset(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= 30) {
                val cutout = wm.currentWindowMetrics.windowInsets.displayCutout
                val rect = cutout?.boundingRects?.firstOrNull()
                if (rect != null) (rect.centerY() - dp(22)).coerceAtLeast(0) else dp(4)
            } else dp(4)
        } catch (_: Exception) { dp(4) }
    }

    private fun refresh() {
        titleView?.text = when {
            callState != "No active call" -> "☎  $callState"
            charging -> "⚡  Charging · $battery%"
            else -> "◈  DYNAMIC ISLAND"
        }
        detailView?.text = latestTitle + "\n" + latestText + "\nBattery " + battery + "% · " + (if (charging) "Charging" else "On battery")
        try {
            val md = mediaController?.metadata
            mediaTitleView?.text = md?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Nothing playing"
            mediaArtworkView?.setImageBitmap(md?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART) ?: md?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART))
            mediaPlayView?.text = if (mediaController?.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) "Ⅱ" else "▶"
        } catch (_: Exception) {}
    }
    private fun pulse() {
        root?.animate()?.scaleX(1.07f)?.scaleY(1.07f)?.setDuration(130)?.withEndAction {
            root?.animate()?.scaleX(if (expanded) 1.025f else 1f)?.scaleY(if (expanded) 1.025f else 1f)?.setDuration(180)?.start()
        }?.start()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (root == null && Settings.canDrawOverlays(this)) showIsland()
        return START_STICKY
    }
    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}
