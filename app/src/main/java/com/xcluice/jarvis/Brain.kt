package com.xcluice.jarvis
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Ear(private val ctx: Context, private val rms: (Float) -> Unit, private val result: (String?) -> Unit) {
    private var sr: SpeechRecognizer? = null
    fun start() {
        stop()
        val s = SpeechRecognizer.createSpeechRecognizer(ctx)
        s.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) { rms(v) }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(e: Int) { result(null) }
            override fun onResults(r: Bundle?) { result(r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()) }
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        s.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
        sr = s
    }
    fun stop() { sr?.destroy(); sr = null }
}

class Brain(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private val hist = ArrayList<Pair<String, String>>()

    init {
        tts = TextToSpeech(ctx) { st ->
            if (st == TextToSpeech.SUCCESS) tts?.let { t ->
                val r = t.setLanguage(Locale.UK)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) t.language = Locale.getDefault()
                else t.voices?.firstOrNull { v -> !v.isNetworkConnectionRequired && listOf("gbd", "rjs", "gbb").any { it in v.name } }?.let { t.voice = it }
                t.setPitch(0.55f); t.setSpeechRate(0.9f)
                ready = true
            }
        }
    }

    fun speak(t: String, done: (() -> Unit)? = null) {
        val s = tts
        if (!ready || s == null) { done?.invoke(); return }
        s.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { main.post { done?.invoke() } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { main.post { done?.invoke() } }
        })
        s.speak(t, TextToSpeech.QUEUE_FLUSH, null, "j")
    }
    fun stopSpeaking() { tts?.stop() }
    fun shutdown() { tts?.shutdown() }

    fun handle(raw: String, reply: (String) -> Unit) {
        val t = raw.lowercase().trim().trimEnd('.', '?', '!')
        local(t)?.let { reply(it); return }
        Thread {
            val r = try { ask(raw) } catch (e: Exception) { "I can't reach my brain right now. Check your internet." }
            main.post { reply(r) }
        }.start()
    }

    private fun local(t: String): String? {
        Regex("^(?:open )?youtube(?: and)? (?:search for |search |play )(.+)$").find(t)?.let { return yt(it.groupValues[1]) }
        Regex("^(?:search for |search |play )(.+) on youtube$").find(t)?.let { return yt(it.groupValues[1]) }
        Regex("^(?:open|launch|start) (.+)$").find(t)?.let { return openApp(it.groupValues[1]) }
        Regex("^(?:google|search for|search|look up) (.+)$").find(t)?.let { return web(it.groupValues[1]) }
        if (t.length < 30 && "time" in t) return "It's " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        if (t.length < 30 && "date" in t) return "Today is " + SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
        return null
    }

    private fun go(i: Intent) = ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun yt(q0: String): String {
        val q = q0.replace(Regex("\\b(juani|wani|wonie|wuhani|one e|waney|wanny|wunny|juwani|wuani|wahani)\\b"), "wuanii")
        try { go(Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", q)) }
        catch (e: Exception) { go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8")))) }
        return "Searching YouTube for $q"
    }

    private fun web(q: String): String {
        go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8"))))
        return "Searching for $q"
    }

    private fun openApp(name: String): String {
        val pm = ctx.packageManager
        val n = name.trim()
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        fun l(a: android.content.pm.ResolveInfo) = a.loadLabel(pm).toString().lowercase()
        val m = apps.firstOrNull { l(it) == n } ?: apps.firstOrNull { l(it).startsWith(n) } ?: apps.firstOrNull { n in l(it) }
            ?: return "I couldn't find $n on your phone."
        val li = pm.getLaunchIntentForPackage(m.activityInfo.packageName) ?: return "I can't open $n."
        go(li)
        return "Opening " + m.loadLabel(pm)
    }

    private fun ask(q: String): String {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content",
            "You are Jarvis, a smart, witty voice assistant on the user's Android phone. Answer in one or two short plain sentences. No markdown, no emojis. Reply in the user's language."))
        hist.takeLast(6).forEach {
            msgs.put(JSONObject().put("role", "user").put("content", it.first))
            msgs.put(JSONObject().put("role", "assistant").put("content", it.second))
        }
        msgs.put(JSONObject().put("role", "user").put("content", q))
        val c = URL("https://text.pollinations.ai/openai").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(JSONObject().put("model", "openai").put("messages", msgs).toString().toByteArray()) }
        val out = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").replace(Regex("[*_#`]"), "").trim()
        hist.add(q to out)
        return out
    }

    companion object {
        fun checkUpdate(cb: (String) -> Unit) = Thread {
            try {
                val j = JSONObject(URL("https://api.github.com/repos/Xcluice/Jarvis/releases/latest").readText())
                val n = j.getString("tag_name").removePrefix("v").toInt()
                if (n > BuildConfig.VERSION_CODE) {
                    val u = j.getJSONArray("assets").getJSONObject(0).getString("browser_download_url")
                    Handler(Looper.getMainLooper()).post { cb(u) }
                }
            } catch (_: Exception) {}
        }.start()
    }
}
