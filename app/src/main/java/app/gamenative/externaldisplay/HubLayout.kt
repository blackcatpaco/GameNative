package app.gamenative.externaldisplay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import app.gamenative.R
import com.winlator.container.Container
import com.winlator.widget.TouchpadView
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer
import java.util.Locale

/** Bottom-screen hub: a menu page plus a page for each input mode. */
internal class HubLayout(
    context: Context,
    private val xServer: XServer,
    private val touchpadViewProvider: () -> TouchpadView?,
    container: Container,
) : FrameLayout(context) {

    // Live game state from the GameNativeBridge UE4SS mod (see VotvBridgeState); null fields
    // mean the mod hasn't reported that value yet (e.g. still at the main menu).
    private var latestState: VotvBridgeState? = null
    private var mapCoordText: TextView? = null
    private var invGaugeText: TextView? = null
    private val bridgeReader = VotvBridgeReader(VotvBridgeReader.stateFileFor(container)) { state ->
        post { applyState(state) }
    }

    // Edit this list to change the hotkey buttons (label to key).
    private val hotkeys = listOf(
        "Esc" to XKeycode.KEY_ESC,
        "Tab" to XKeycode.KEY_TAB,
        "E" to XKeycode.KEY_E,
        "F" to XKeycode.KEY_F,
        "Space" to XKeycode.KEY_SPACE,
        "Enter" to XKeycode.KEY_ENTER,
    )

    // Hotbar slots 1-9, moved here off the d-pad so the physical controller has those directions free.
    private val hotbarSlots = listOf(
        "1" to XKeycode.KEY_1,
        "2" to XKeycode.KEY_2,
        "3" to XKeycode.KEY_3,
        "4" to XKeycode.KEY_4,
        "5" to XKeycode.KEY_5,
        "6" to XKeycode.KEY_6,
        "7" to XKeycode.KEY_7,
        "8" to XKeycode.KEY_8,
        "9" to XKeycode.KEY_9,
    )

    private val hubTypeface = ResourcesCompat.getFont(context, R.font.share_tech_mono_regular)

    // Green-phosphor CRT palette, styled after VOTV's in-fiction SCADA/radar terminals:
    // black backdrop, bright green strokes/text, a dim green for secondary graticule lines.
    private val hubBackground = Color.BLACK
    private val hubGreen = Color.parseColor("#33FF33")
    private val hubGreenDim = Color.parseColor("#124312")
    private val hubTextColor = hubGreen

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun <T : TextView> T.applyHubFont(): T = apply { typeface = hubTypeface }

    // Black fill / green outline normally; reverse-video (green fill / black text) while
    // pressed, like a phosphor terminal's keyed-in response rather than a mobile ripple.
    private fun phosphorButtonBackground(): StateListDrawable {
        val normal = GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(dp(2), hubGreen)
        }
        val pressed = GradientDrawable().apply {
            setColor(hubGreen)
            setStroke(dp(2), hubGreen)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private val phosphorTextColors = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
        intArrayOf(Color.BLACK, hubGreen),
    )

    private fun Button.applyHubButtonStyle(): Button = apply {
        background = phosphorButtonBackground()
        setTextColor(phosphorTextColors)
        setAllCaps(true)
        letterSpacing = 0.08f
    }

    init {
        setBackgroundColor(hubBackground)
        showMenu()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bridgeReader.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        bridgeReader.stop()
    }

    private fun applyState(state: VotvBridgeState) {
        latestState = state
        mapCoordText?.text = formatMapText(state)
        invGaugeText?.text = formatInventoryText(state)
    }

    private fun showMenu() {
        removeAllViews()
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        column.addView(menuButton("Mouse + Keyboard") {
            showPage(HybridInputLayout(context, xServer, touchpadViewProvider))
        })
        column.addView(menuButton("Hotkeys") { showPage(buildHotkeyPage()) })
        column.addView(menuButton("Hotbar") { showPage(buildKeyGridPage(hotbarSlots)) })
        column.addView(menuButton("Map") { showPage(mapPage()) })
        column.addView(menuButton("Terminal") { showPage(placeholderPage("Terminal — coming soon")) })
        column.addView(menuButton("Inventory") { showPage(inventoryPage()) })
        addView(
            column,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    private fun mapPage(): View = FrameLayout(context).apply {
        val text = TextView(context).applyHubFont().apply {
            text = formatMapText(latestState)
            textSize = 18f
            setTextColor(hubTextColor)
            gravity = Gravity.CENTER
            letterSpacing = 0.05f
        }
        mapCoordText = text
        addView(
            text,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            },
        )
    }

    private fun formatMapText(state: VotvBridgeState?): String {
        val x = state?.playerLocX
        val y = state?.playerLocY
        val z = state?.playerLocZ
        if (x == null || y == null || z == null) return "MAP\nwaiting for game data"
        return String.format(Locale.US, "MAP\nX: %.0f\nY: %.0f\nZ: %.0f", x, y, z)
    }

    private fun inventoryPage(): View = FrameLayout(context).apply {
        val text = TextView(context).applyHubFont().apply {
            text = formatInventoryText(latestState)
            textSize = 18f
            setTextColor(hubTextColor)
            gravity = Gravity.CENTER
            letterSpacing = 0.05f
        }
        invGaugeText = text
        addView(
            text,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            },
        )
    }

    private fun formatInventoryText(state: VotvBridgeState?): String {
        val curr = state?.invCurrVol
        val max = state?.invMaxVol
        if (curr == null || max == null || max <= 0.0) return "INVENTORY\nwaiting for game data"
        val pct = (curr / max * 100).toInt()
        return String.format(Locale.US, "INVENTORY\n%d%% FULL", pct)
    }

    // Weighted so the 5 options evenly fill the available height with no title and no scrolling.
    private fun menuButton(label: String, onClick: () -> Unit) = Button(context).applyHubFont().applyHubButtonStyle().apply {
        text = label
        textSize = 18f
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(0, dp(8), 0, dp(8))
        }
    }

    private fun showPage(page: View) {
        removeAllViews()
        addView(
            page,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        val back = Button(context).applyHubFont().applyHubButtonStyle().apply {
            text = "Menu"
            setOnClickListener { showMenu() }
        }
        addView(
            back,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(dp(8), dp(8), 0, 0)
            },
        )
    }

    private fun placeholderPage(message: String): View = FrameLayout(context).apply {
        addView(
            TextView(context).applyHubFont().apply {
                text = message
                textSize = 20f
                setTextColor(hubTextColor)
                gravity = Gravity.CENTER
            },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER
            },
        )
    }

    // Hotkey grid plus the scroll-wheel control, for VOTV's radio/radar knobs that read a
    // PC mouse wheel.
    private fun buildHotkeyPage(): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(56), dp(16), dp(16))
        }
        column.addView(buildKeyGrid(hotkeys))
        column.addView(scrollWheelSection())
        return column
    }

    private fun scrollWheelSection(): View {
        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        section.addView(
            TextView(context).applyHubFont().apply {
                text = "SCROLL"
                textSize = 14f
                setTextColor(hubGreenDim)
                letterSpacing = 0.2f
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
                bottomMargin = dp(4)
            },
        )
        section.addView(ScrollWheelView(context, xServer), LinearLayout.LayoutParams(dp(120), dp(180)))
        return section
    }

    private fun buildKeyGridPage(keys: List<Pair<String, XKeycode>>): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(56), dp(16), dp(16))
        }
        column.addView(buildKeyGrid(keys))
        return column
    }

    private fun buildKeyGrid(keys: List<Pair<String, XKeycode>>): LinearLayout {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        keys.chunked(3).forEach { rowKeys ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            rowKeys.forEach { (label, key) ->
                row.addView(
                    hotkeyButton(label, key),
                    LinearLayout.LayoutParams(0, dp(96), 1f).apply {
                        setMargins(dp(6), dp(6), dp(6), dp(6))
                    },
                )
            }
            column.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        return column
    }

    // Press while touched, release on lift, so holding a button holds the key.
    private fun hotkeyButton(label: String, key: XKeycode) = Button(context).applyHubFont().applyHubButtonStyle().apply {
        text = "[$label]"
        textSize = 20f
        setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    xServer.injectKeyPress(key)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    xServer.injectKeyRelease(key)
                }
            }
            true
        }
    }
}
