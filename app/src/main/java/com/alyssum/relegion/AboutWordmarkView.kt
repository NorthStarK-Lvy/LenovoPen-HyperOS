package com.alyssum.relegion

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/** Draw a cached glyph outline with the scene's native gradient, including software canvases. */
internal class AboutWordmarkView(context: Context, private val scene: AboutScene) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.BOLD) }
    private val glyphs = Path()
    private val bounds = RectF()
    private val position = IntArray(2)
    private val colors = IntArray(9)
    private val repaint: () -> Unit = { invalidate() }
    private var revision = -1L
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE

    init { contentDescription = "RELEGION"; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    fun configure(ink: Int) { paint.color = ink; invalidate() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val wanted = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 38f, resources.displayMetrics)
        paint.textSize = wanted
        paint.textSize = minOf(wanted, h * 0.9f,
            wanted * (w - paddingLeft - paddingRight).coerceAtLeast(1) / paint.measureText("RELEGION"))
        glyphs.reset()
        paint.getTextPath("RELEGION", 0, 8, 0f, 0f, glyphs)
        glyphs.computeBounds(bounds, true)
        glyphs.offset((w - bounds.width()) * .5f - bounds.left, (h - bounds.height()) * .5f - bounds.top)
        glyphs.computeBounds(bounds, true)
        revision = -1
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bounds.isEmpty) return
        getLocationInWindow(position)
        val x = position[0] - scene.origin[0]
        val y = position[1] - scene.origin[1]
        if (revision != scene.revision || lastX != x || lastY != y) {
            val radius = 16f * resources.displayMetrics.density
            for (i in colors.indices) colors[i] = scene.field.inkAt(x + bounds.left + bounds.width() * i / (colors.size - 1), y + bounds.centerY(), radius)
            paint.shader = LinearGradient(bounds.left, 0f, bounds.right, 0f, colors, null, Shader.TileMode.CLAMP)
            revision = scene.revision; lastX = x; lastY = y
        }
        canvas.drawPath(glyphs, paint)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.TextView"
        info.text = "RELEGION"
        info.isHeading = true
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); scene.addListener(repaint) }
    override fun onDetachedFromWindow() { scene.removeListener(repaint); super.onDetachedFromWindow() }
    fun dispose() { scene.removeListener(repaint); paint.shader = null }
}
