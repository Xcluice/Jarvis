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
import android.os.PowerManager

class WakeService : Service() {
    private lateinit var brain: Brain
    private var vear: VoskEar? = null
    private var wl: PowerManager.WakeLock? = null
    private val h = Handler(Looper.getMainLooper())
    private var awaitingUntil = 0L
    private val wake = Regex("\\b(jarvis|jarvish|jervis|jarves|jarvi|travis|service|jar vis)\\b")

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "jarvis").setContentTitle("Jarvis is listening")
            .setContentText("Say \"Hey Jarvis\" followed by a command").setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(1, n)
        brain = Brain(this)
        val m = VoskModel.load(this)
        if (m == null) { stopSelf(); return }
        wl = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:wake").apply { acquire() }
        vear = VoskEar(m, {}, {}) { onHeard(it) }
        vear?.start(true)
    }

    private fun onHeard(text: String) {
        val t = text.lowercase()
        if (System.currentTimeMillis() < awaitingUntil) { awaitingUntil = 0; run(t); return }
        val m = wake.find(t) ?: return
        val cmd = t.substring(m.range.last + 1).trim(' ', ',', '.')
        if (cmd.isEmpty()) say("Yes?") { awaitingUntil = System.currentTimeMillis() + 8000 } else run(cmd)
    }

    private fun say(s: String, done: () -> Unit = {}) {
        vear?.muted = true
        brain.speak(s) { h.postDelayed({ vear?.muted = false }, 400); done() }
    }

    private fun run(cmd: String) { brain.handle(cmd) { say(it) } }

    override fun onDestroy() {
        vear?.stop(); brain.shutdown(); wl?.takeIf { it.isHeld }?.release(); super.onDestroy()
    }
}
