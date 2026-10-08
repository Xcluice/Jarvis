package com.xcluice.jarvis
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import kotlin.math.sqrt

object VoskModel {
    private const val NAME = "vosk-model-small-en-in-0.4"
    @Volatile var model: Model? = null
    private fun root(c: Context) = File(c.filesDir, "vosk")
    fun installed(c: Context) = File(root(c), "ready").exists()
    fun load(c: Context): Model? {
        model?.let { return it }
        if (!installed(c)) return null
        return Model(File(root(c), NAME).absolutePath).also { model = it }
    }
    fun download(c: Context, progress: (Int) -> Unit): Boolean = try {
        val dir = root(c).apply { deleteRecursively(); mkdirs() }
        val zip = File(c.cacheDir, "m.zip")
        val con = URL("https://alphacephei.com/vosk/models/$NAME.zip").openConnection() as HttpURLConnection
        val total = con.contentLength.toLong().coerceAtLeast(1)
        var done = 0L; var last = -1
        con.inputStream.use { i -> zip.outputStream().use { o ->
            val b = ByteArray(65536)
            while (true) {
                val n = i.read(b); if (n < 0) break
                o.write(b, 0, n); done += n
                val p = (done * 100 / total).toInt(); if (p != last) { last = p; progress(p) }
            }
        } }
        ZipInputStream(zip.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val f = File(dir, e.name)
                if (!f.canonicalPath.startsWith(dir.canonicalPath)) continue
                if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) } }
            }
        }
        zip.delete(); File(dir, "ready").writeText("1"); true
    } catch (e: Exception) { false }
}

class VoskEar(private val m: Model, private val onPartial: (String) -> Unit, private val onLevel: (Float) -> Unit, private val onFinal: (String) -> Unit) {
    @Volatile private var run = false
    @Volatile var muted = false
    private val main = Handler(Looper.getMainLooper())

    fun start(continuous: Boolean, timeoutMs: Long = 10000) {
        run = true
        Thread {
            val rate = 16000
            val bs = maxOf(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 4096)
            val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bs * 2)
            val r = Recognizer(m, rate.toFloat())
            val buf = ByteArray(4096)
            val t0 = System.currentTimeMillis()
            var lastP = ""
            try {
                rec.startRecording()
                while (run) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0 || muted) continue
                    val sb = ByteBuffer.wrap(buf, 0, n).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    var sum = 0.0; val cnt = sb.remaining()
                    while (sb.hasRemaining()) { val v = sb.get().toDouble(); sum += v * v }
                    val lvl = (sqrt(sum / cnt.coerceAtLeast(1)) / 5000.0).toFloat().coerceIn(0f, 1f)
                    main.post { onLevel(lvl) }
                    if (r.acceptWaveForm(buf, n)) {
                        val t = JSONObject(r.result).optString("text")
                        if (t.isNotBlank()) { main.post { onFinal(t) }; if (!continuous) break }
                    } else {
                        val p = JSONObject(r.partialResult).optString("partial")
                        if (p != lastP) { lastP = p; if (p.isNotBlank()) main.post { onPartial(p) } }
                    }
                    if (!continuous && System.currentTimeMillis() - t0 > timeoutMs) {
                        val t = JSONObject(r.finalResult).optString("text"); main.post { onFinal(t) }; break
                    }
                }
            } catch (_: Exception) {
            } finally {
                run = false
                try { rec.stop() } catch (_: Exception) {}
                rec.release(); r.close()
            }
        }.start()
    }
    fun stop() { run = false }
}
