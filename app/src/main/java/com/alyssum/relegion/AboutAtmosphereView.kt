package com.alyssum.relegion

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.PowerManager
import android.view.Choreographer
import android.view.View

/** Drives the single scene clock; the brand material consumes the same shader and coordinates. */
internal class AboutAtmosphereView(context: Context, private val scene: AboutScene) : View(context), Choreographer.FrameCallback {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shader get() = scene.shader
    private val repaint: () -> Unit = { if (shader == null) updateMaterials(); invalidate() }
    private val power = context.getSystemService(PowerManager::class.java)
    private var attached = false
    private var allowed = false
    private var registered = false
    private var scheduled = false
    private var disposed = false
    private var darkMode = false
    private var background = Color.WHITE
    private var configured = false
    private var lastFrame = 0L
    private val powerChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reconcile()
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
    }

    fun configure(color: Int, dark: Boolean) {
        if (configured && background == color && darkMode == dark) return
        configured = true
        background = color
        darkMode = dark
        scene.configure(color, dark)
        updateMaterials()
        invalidate()
    }

    fun setMotionAllowed(value: Boolean) {
        allowed = value
        reconcile()
    }

    private fun shouldAnimate() = !disposed && attached && allowed && isShown && windowVisibility == VISIBLE
        && shader != null && !power.isPowerSaveMode && ValueAnimator.areAnimatorsEnabled()

    private fun reconcile() {
        if (shouldAnimate()) {
            if (!scheduled) {
                lastFrame = 0L
                scheduled = true
                Choreographer.getInstance().postFrameCallback(this)
            }
        } else {
            Choreographer.getInstance().removeFrameCallback(this)
            scheduled = false
            lastFrame = 0L
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!shouldAnimate()) { lastFrame = 0L; return }
        if (lastFrame != 0L) scene.advance(((frameTimeNanos - lastFrame) * 1e-9f).coerceIn(0f, 0.08f))
        lastFrame = frameTimeNanos
        invalidate()
        scheduled = true
        Choreographer.getInstance().postFrameCallbackDelayed(this, 33L)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scene.resize(w, h)
        updateMaterials()
    }

    private fun updateMaterials() {
        if (shader != null) {
            paint.shader = shader
        } else {
            // Retain a usable about page if a GPU driver rejects the shader.
            paint.shader = LinearGradient(0f, 0f, 0f, height.coerceAtLeast(1).toFloat(),
                if (darkMode) intArrayOf(0xff24223c.toInt(), background) else intArrayOf(0xffdedcf6.toInt(), background), null, Shader.TileMode.CLAMP)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        getLocationInWindow(scene.origin)
        if (canvas.isHardwareAccelerated || shader == null) canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        else canvas.drawColor(background)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        scene.addListener(repaint)
        if (!disposed) {
            context.registerReceiver(powerChanges, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        reconcile()
    }

    override fun onDetachedFromWindow() {
        attached = false
        scene.removeListener(repaint)
        reconcile()
        if (registered) { context.unregisterReceiver(powerChanges); registered = false }
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (configured) reconcile()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (configured) reconcile()
    }

    fun dispose() {
        disposed = true
        scene.removeListener(repaint)
        reconcile()
        if (registered) { context.unregisterReceiver(powerChanges); registered = false }
        setRenderEffect(null)
    }
}
