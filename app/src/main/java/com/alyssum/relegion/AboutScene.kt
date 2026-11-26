package com.alyssum.relegion

import android.content.Context
import android.graphics.Color
import android.graphics.RuntimeShader
import android.util.Log

/** One viewport, material and clock shared by the backdrop and brand lettering. */
internal class AboutScene(context: Context) {
    var shader = runCatching {
        RuntimeShader(context.resources.openRawResource(R.raw.about_atmosphere).bufferedReader().use { it.readText() })
    }.onFailure { Log.w("RELEGION", "About shader unavailable", it) }.getOrNull()
        private set
    private val listeners = linkedSetOf<() -> Unit>()
    val field = AboutColorField()
    var revision = 0L; private set
    var dark = false; private set
    var background = Color.WHITE; private set
    private var elapsed = 0f
    val origin = IntArray(2)
    private var width = 1f
    private var height = 1f
    init { updateUniforms() }

    fun configure(color: Int, isDark: Boolean) {
        if (background == color && dark == isDark) return
        background = color
        dark = isDark
        updateUniforms()
        redraw()
    }

    fun resize(w: Int, h: Int) {
        width = w.coerceAtLeast(1).toFloat()
        height = h.coerceAtLeast(1).toFloat()
        updateUniforms()
        redraw()
    }

    private fun phase(): Float = (elapsed % 240f).let { if (it <= 120f) it else 240f - it }
    fun advance(seconds: Float) {
        elapsed += seconds
        updateUniforms()
        redraw()
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun redraw() { listeners.forEach { it() } }

    private fun updateUniforms() {
        field.configure(width, height, phase(), dark, background)
        revision++
        try {
            shader?.apply {
                setFloatUniform("resolution", width, height)
                setFloatUniform("base", Color.red(background) / 255f, Color.green(background) / 255f, Color.blue(background) / 255f)
                setFloatUniform("time", phase())
                setFloatUniform("centers", field.centers)
                setFloatUniform("palette", field.colors)
            }
        } catch (error: IllegalArgumentException) {
            Log.w("RELEGION", "About viewport material unavailable; using gradient", error)
            shader = null
        }
    }
}
