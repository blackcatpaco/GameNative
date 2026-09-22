package app.gamenative.externaldisplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.winlator.xserver.Pointer
import com.winlator.xserver.XServer
import kotlin.math.abs

/**
 * A self-centering "spring" scroll-wheel control for in-game knobs/sliders that read a PC
 * mouse wheel (VOTV's radio and radar equipment). Drag away from centre to spin the wheel;
 * the tick rate accelerates the further you drag, and releasing snaps the thumb back to
 * centre instantly, so there's no limit on how much you can scroll without re-grabbing from
 * a track edge.
 */
internal class ScrollWheelView(
    context: Context,
    private val xServer: XServer,
) : View(context) {

    private companion object {
        const val DEADZONE_FRACTION = 0.08f
        const val MAX_TICK_INTERVAL_MS = 220L
        const val MIN_TICK_INTERVAL_MS = 25L
        const val SNAP_BACK_DURATION_MS = 150L
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val colorGreen = Color.parseColor("#33FF33")
    private val colorGreenDim = Color.parseColor("#124312")

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = colorGreen
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = colorGreenDim
    }
    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = colorGreenDim
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorGreen
    }

    private val trackInset = dp(10f)
    private val thumbHeight = dp(20f)

    /** -trackHalfHeight() .. +trackHalfHeight(); negative is "up". */
    private var offsetY = 0f
    private var isDragging = false
    private var touchStartY = 0f
    private var touchStartOffset = 0f

    private var snapAnimStartOffset = 0f
    private var snapAnimStartTime = 0L

    private val tickRunnable = Runnable { runTick() }
    private val snapRunnable = Runnable { runSnap() }

    private fun trackHalfHeight(): Float {
        val usable = (height - paddingTop - paddingBottom) / 2f - trackInset - thumbHeight / 2f
        return usable.coerceAtLeast(1f)
    }

    private fun runTick() {
        if (!isDragging) return
        val half = trackHalfHeight()
        val deadzone = DEADZONE_FRACTION * half
        val magnitude = abs(offsetY)
        if (magnitude <= deadzone) {
            postDelayed(tickRunnable, MAX_TICK_INTERVAL_MS)
            return
        }
        val f = ((magnitude - deadzone) / (half - deadzone)).coerceIn(0f, 1f)
        val interval = (MAX_TICK_INTERVAL_MS - (MAX_TICK_INTERVAL_MS - MIN_TICK_INTERVAL_MS) * f).toLong()
        // Screen Y grows downward, so dragging UP (negative offset) scrolls up.
        val button = if (offsetY < 0) Pointer.Button.BUTTON_SCROLL_UP else Pointer.Button.BUTTON_SCROLL_DOWN
        xServer.injectPointerButtonPress(button)
        xServer.injectPointerButtonRelease(button)
        postDelayed(tickRunnable, interval)
    }

    private fun runSnap() {
        val elapsed = System.currentTimeMillis() - snapAnimStartTime
        val t = (elapsed.toFloat() / SNAP_BACK_DURATION_MS).coerceIn(0f, 1f)
        val eased = 1f - (1f - t) * (1f - t)
        offsetY = snapAnimStartOffset * (1f - eased)
        invalidate()
        if (t < 1f) {
            postOnAnimation(snapRunnable)
        } else {
            offsetY = 0f
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(snapRunnable)
                isDragging = true
                touchStartY = event.y
                touchStartOffset = offsetY
                removeCallbacks(tickRunnable)
                post(tickRunnable)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return true
                val half = trackHalfHeight()
                offsetY = (touchStartOffset + (event.y - touchStartY)).coerceIn(-half, half)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                removeCallbacks(tickRunnable)
                snapAnimStartOffset = offsetY
                snapAnimStartTime = System.currentTimeMillis()
                removeCallbacks(snapRunnable)
                postOnAnimation(snapRunnable)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val trackRect = RectF(trackInset, trackInset, w - trackInset, h - trackInset)
        canvas.drawRect(trackRect, trackPaint)

        val centerY = h / 2f
        val half = trackHalfHeight()
        listOf(0.25f, 0.5f, 0.75f, 1.0f).forEach { frac ->
            val tyUp = centerY - half * frac
            val tyDown = centerY + half * frac
            canvas.drawLine(trackRect.left + dp(4f), tyUp, trackRect.left + dp(10f), tyUp, tickPaint)
            canvas.drawLine(trackRect.right - dp(10f), tyUp, trackRect.right - dp(4f), tyUp, tickPaint)
            canvas.drawLine(trackRect.left + dp(4f), tyDown, trackRect.left + dp(10f), tyDown, tickPaint)
            canvas.drawLine(trackRect.right - dp(10f), tyDown, trackRect.right - dp(4f), tyDown, tickPaint)
        }
        canvas.drawLine(trackRect.left, centerY, trackRect.right, centerY, centerLinePaint)

        val thumbCenterY = centerY + offsetY
        val thumbRect = RectF(
            trackRect.left + dp(3f),
            thumbCenterY - thumbHeight / 2f,
            trackRect.right - dp(3f),
            thumbCenterY + thumbHeight / 2f,
        )
        canvas.drawRect(thumbRect, thumbPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(tickRunnable)
        removeCallbacks(snapRunnable)
    }
}
