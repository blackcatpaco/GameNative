package app.gamenative.externaldisplay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

/**
 * Renders a VOTV overworld map image with a live marker for the player's position.
 *
 * The coordinate math mirrors the (independently re-derived, not copied) relationship used by
 * the community map at github.com/Questwalker/votv-map: it stores points in "game units" equal
 * to Unreal world units / 100, square map bounds of [0, MAP_SIZE] in each axis with the origin
 * at image center, and plots via `convertGameToLeaflet([x,y]) = [-y + MAP_SIZE/2, x + MAP_SIZE/2]`
 * onto a Leaflet L.CRS.Simple image overlay (where the first/"lat" component increases upward on
 * screen, the second/"lng" component increases rightward). Composing that with how
 * L.imageOverlay anchors bounds [[0,0],[MAP_SIZE,MAP_SIZE]] to image corners and simplifying
 * collapses to a single pair of fractions with no sign flip at all:
 *   colFraction = 0.5 + (unrealX / 100) / MAP_SIZE
 *   rowFraction = 0.5 + (unrealY / 100) / MAP_SIZE
 * i.e. the in-game origin sits at the image's exact center, +X moves right, +Y moves down.
 */
internal class VotvMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    enum class Season(val assetPath: String) {
        WINTER("votv_map/votv_map_winter.webp"),
        SPRING("votv_map/votv_map_spring.webp"),
        SUMMER("votv_map/votv_map_summer.webp"),
        AUTUMN("votv_map/votv_map_autumn.webp"),
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private var mapBitmap: Bitmap? = null
    private var season: Season? = null

    private var playerUnrealX: Float? = null
    private var playerUnrealY: Float? = null

    private val destRect = RectF()

    private val markerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#FF3B30".toColorInt()
        style = Paint.Style.FILL
    }
    private val markerOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }

    fun setSeason(newSeason: Season) {
        if (season == newSeason) return
        season = newSeason
        mapBitmap?.recycle()
        mapBitmap = try {
            context.assets.open(newSeason.assetPath).use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
        invalidate()
    }

    fun setPlayerPosition(unrealX: Float?, unrealY: Float?) {
        if (playerUnrealX == unrealX && playerUnrealY == unrealY) return
        playerUnrealX = unrealX
        playerUnrealY = unrealY
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = mapBitmap ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // The map image is square; fit it into the view preserving aspect ratio, centered.
        val side = minOf(w, h)
        val left = (w - side) / 2f
        val top = (h - side) / 2f
        destRect.set(left, top, left + side, top + side)
        canvas.drawBitmap(bitmap, null, destRect, null)

        val x = playerUnrealX
        val y = playerUnrealY
        if (x != null && y != null) {
            val colFraction = (0.5f + (x / 100f) / MAP_SIZE).coerceIn(0f, 1f)
            val rowFraction = (0.5f + (y / 100f) / MAP_SIZE).coerceIn(0f, 1f)
            val px = destRect.left + colFraction * destRect.width()
            val py = destRect.top + rowFraction * destRect.height()

            val radius = dp(6f)
            canvas.drawCircle(px, py, radius, markerFillPaint)
            canvas.drawCircle(px, py, radius, markerOutlinePaint)
        }
    }

    private companion object {
        // Game-unit side length of the square map, per the community map's "mapSize" constant.
        const val MAP_SIZE = 1489.995f
    }
}
