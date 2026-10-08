package com.xcluice.jarvis
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class WakeService : Service() {
    private lateinit var brain: Brain
    private lateinit var ear: Ear
    private val h = Handler(Looper.getMainLooper())
    private var awaiting = false
    private var alive = true

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "jarvis").setContentTitle("Jarvis is listening")
            .setContentText("Say \"Hey Jarvis\" followed by a command").setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(1, n)
        brain = Brain(this)
        ear = Ear(this, {}) { onHeard(it) }
        h.postDelayed({ if (alive) ear.start() }, 1000)
    }

    private fun again() { h.postDelayed({ if (alive) ear.start() }, 400) }

    private fun run(cmd: String) { brain.handle(cmd) { out -> brain.speak(out) { again() } } }

    private fun onHeard(r: String?) {
        if (!alive) return
        val t = r?.lowercase()
        if (t != null) {
            if (awaiting) { awaiting = false; run(t); return }
            val i = t.indexOf("jarvis")
            if (i >= 0) {
                val cmd = t.substring(i + 6).trim(' ', ',', '.')
                if (cmd.isEmpty()) { awaiting = true; brain.speak("Yes?") { again() } } else run(cmd)
                return
            }
        }
        again()
    }

    override fun onDestroy() { alive = false; ear.stop(); brain.shutdown(); super.onDestroy() }
}
