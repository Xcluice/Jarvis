package com.xcluice.jarvis
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class OrbView(c: Context) : View(c) {
    var state = 0   // 0 idle, 1 listening, 2 thinking, 3 speaking
    var level = 0f
    private var t = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val an = ValueAnimator.ofFloat(0f, 6.2832f).apply {
        duration = 2600; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); an.start() }
    override fun onDetachedFromWindow() { an.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val base = minOf(width, height) * 0.2f
        val amp = when (state) { 1 -> 0.08f + level * 0.3f; 2 -> 0.1f; 3 -> 0.14f; else -> 0.04f }
        for (i in 3 downTo 1) {
            val r = base * (1f + 0.45f * i) * (1f + amp * sin(t * i + i))
            p.shader = RadialGradient(cx, cy, r, intArrayOf(0xAA9B5CFF.toInt(), 0x553FA9FF, 0x003FA9FF), floatArrayOf(0.3f, 0.7f, 1f), Shader.TileMode.CLAMP)
            cv.drawCircle(cx, cy, r, p)
        }
        val r0 = base * (1f + amp * sin(t * 2))
        p.shader = LinearGradient(cx - r0, cy - r0, cx + r0, cy + r0, intArrayOf(0xFFFF6AD5.toInt(), 0xFF8B5CFF.toInt(), 0xFF22D3EE.toInt()), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r0, p)
    }
}
