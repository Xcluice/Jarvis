package com.xcluice.jarvis
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var orb: OrbView
    private lateinit var status: TextView
    private lateinit var reply: TextView
    private lateinit var wakeBtn: TextView
    private lateinit var updBtn: TextView
    private lateinit var brain: Brain
    private var ear: Ear? = null
    private var listening = false
    private val idle = "Tap the orb and talk"

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun label(t: String, sp: Float, c: Int) = TextView(this).apply { text = t; textSize = sp; setTextColor(c); gravity = Gravity.CENTER }
    private fun pill(t: String, f: () -> Unit) = TextView(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
        setPadding(dp(22), dp(12), dp(22), dp(12))
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF7B2FF7.toInt(), 0xFF00C6FF.toInt())).apply { cornerRadius = dp(30).toFloat() }
        setOnClickListener { f() }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        brain = Brain(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(24), dp(28), dp(24), dp(24)) }
        root.addView(label("J A R V I S", 20f, Color.WHITE))
        orb = OrbView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f); setOnClickListener { toggle() } }
        root.addView(orb)
        status = label(idle, 16f, 0xCCFFFFFF.toInt()); root.addView(status)
        reply = label("", 18f, Color.WHITE).apply { minLines = 3; setPadding(0, dp(12), 0, dp(12)) }; root.addView(reply)
        wakeBtn = pill("") { toggleWake() }; root.addView(wakeBtn)
        root.addView(pill("Set as default assistant") { assistantSettings() })
        updBtn = pill("") {}; updBtn.visibility = View.GONE; root.addView(updBtn)
        setContentView(root)
        refreshWake()
        Brain.checkUpdate { url ->
            updBtn.text = "Update available - tap to download"; updBtn.visibility = View.VISIBLE
            updBtn.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
        handleLaunch(intent)
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); handleLaunch(i) }
    private fun handleLaunch(i: Intent?) { if (i?.action == Intent.ACTION_ASSIST || i?.action == Intent.ACTION_VOICE_COMMAND || i?.getBooleanExtra("listen", false) == true) listen() }

    private fun toggle() {
        if (listening) { ear?.stop(); listening = false; orb.state = 0; status.text = idle } else { brain.stopSpeaking(); listen() }
    }

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1); return
        }
        listening = true; orb.state = 1; status.text = "Listening..."
        ear = Ear(this, { orb.level = ((it + 2f) / 12f).coerceIn(0f, 1f) }) { r -> onHeard(r) }
        ear?.start()
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, g: IntArray) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
    }

    private fun onHeard(r: String?) {
        listening = false
        if (r == null) { orb.state = 0; status.text = "Didn't catch that. Tap to retry"; return }
        status.text = "\u201C$r\u201D"; orb.state = 2
        brain.handle(r) { out ->
            reply.text = out; orb.state = 3
            brain.speak(out) { orb.state = 0; status.text = idle }
        }
    }

    private fun wakeOn() = getSharedPreferences("j", 0).getBoolean("wake", false)
    private fun refreshWake() { wakeBtn.text = if (wakeOn()) "Hey Jarvis: ON" else "Hey Jarvis: OFF" }

    private fun toggleWake() {
        val p = getSharedPreferences("j", 0)
        if (wakeOn()) { stopService(Intent(this, WakeService::class.java)); p.edit().putBoolean("wake", false).apply() }
        else {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { listen(); return }
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Allow 'Display over other apps' so Jarvis can open apps while you're elsewhere", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return
            }
            startForegroundService(Intent(this, WakeService::class.java)); p.edit().putBoolean("wake", true).apply()
        }
        refreshWake()
    }

    private fun assistantSettings() {
        try { startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
        catch (e: Exception) { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
    }

    override fun onDestroy() { ear?.stop(); brain.shutdown(); super.onDestroy() }
}
