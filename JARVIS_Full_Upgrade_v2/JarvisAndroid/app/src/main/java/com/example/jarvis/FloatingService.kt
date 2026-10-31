package com.example.jarvis

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class FloatingService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Settings.canDrawOverlays(this) && bubble == null) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            bubble = TextView(this).apply {
                text = "J"
                textSize = 23f
                setTextColor(0xFF65E0FF.toInt())
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xEE092033.toInt())
                    setStroke(3, 0xFF43CFFF.toInt())
                }
                elevation = 12f
                contentDescription = "Open JARVIS"
                setOnClickListener {
                    val launch = packageManager.getLaunchIntentForPackage(packageName)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    if (launch != null) startActivity(launch)
                }
            }
            val type = if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                58, 58, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 22
                y = 210
            }
            runCatching { windowManager?.addView(bubble, params) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        bubble?.let { runCatching { windowManager?.removeView(it) } }
        bubble = null
        super.onDestroy()
    }
}
