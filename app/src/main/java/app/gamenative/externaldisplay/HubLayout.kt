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
import androidx.core.graphics.toColorInt
import app.gamenative.R
import com.winlator.container.Container
import com.winlator.widget.TouchpadView
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer
import java.util.Locale

/** Bottom-screen hub: a menu page plus a page for each input mode. */
internal class HubLayout @JvmOverloads constructor(
    context: Context,
    private val xServer: XServer,
    private val touchpadViewProvider: () -> TouchpadView?,
    container: Container,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

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
    private val hubGreen = "#33FF33".toColorInt()
    private val hubGreenDim = "#124312".toColorInt()
    private val hubTextColor = hubGreen

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun <T : TextView> T.applyHubFont(): T = apply { typeface = hubTypeface }

    private fun phosphorBoxBackground(isDim: Boolean = false): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(dp(2), if (isDim) hubGreenDim else hubGreen)
            cornerRadius = dp(2).toFloat()
        }
    }

    // Black fill / green outline normally; reverse-video (green fill / black text) while
    // pressed, like a phosphor terminal's keyed-in response rather than a mobile ripple.
    private fun phosphorButtonBackground(): StateListDrawable {
        val normal = phosphorBoxBackground(false)
        val pressed = GradientDrawable().apply {
            setColor(hubGreen)
            setStroke(dp(2), hubGreen)
            cornerRadius = dp(2).toFloat()
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
        val scrollView = android.widget.ScrollView(context).apply {
            isFillViewport = true
        }
        val dashboard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // Header
        dashboard.addView(TextView(context).applyHubFont().apply {
            text = "V.O.T.V. // SYSTEM DASHBOARD"
            textSize = 20f
            setTextColor(hubGreen)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
            setShadowLayer(4f, 0f, 0f, hubGreenDim)
        })

        // Live Widgets Row
        val widgetsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
        }
        val mapWidget = TextView(context).applyHubFont().apply {
            text = formatMapText(latestState)
            textSize = 14f
            setTextColor(hubTextColor)
            background = phosphorBoxBackground(true)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(4) }
            setOnClickListener { showPage("MAP DATA", mapPage()) }
        }
        mapCoordText = mapWidget

        val invWidget = TextView(context).applyHubFont().apply {
            text = formatInventoryText(latestState)
            textSize = 14f
            setTextColor(hubTextColor)
            background = phosphorBoxBackground(true)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(4) }
            setOnClickListener { showPage("INVENTORY", inventoryPage()) }
        }
        invGaugeText = invWidget

        widgetsRow.addView(mapWidget)
        widgetsRow.addView(invWidget)
        dashboard.addView(widgetsRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) })

        // Divider
        dashboard.addView(View(context).apply { background = android.graphics.drawable.ColorDrawable(hubGreenDim) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)).apply { bottomMargin = dp(16) })

        // Grid Navigation
        val grid1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        grid1.addView(gridButton("MOUSE +\nKEYBOARD") { showPage("HYBRID INPUT", HybridInputLayout(context, xServer, touchpadViewProvider)) })
        grid1.addView(gridButton("TERMINAL\nCONSOLE") { showPage("TERMINAL", placeholderPage("Terminal — coming soon")) })

        val grid2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        grid2.addView(gridButton("HOTKEYS\n(ACTIONS)") { showPage("HOTKEYS", buildHotkeyPage()) })
        grid2.addView(gridButton("HOTBAR\n(SLOTS 1-9)") { showPage("HOTBAR", buildKeyGridPage(hotbarSlots)) })

        dashboard.addView(grid1)
        dashboard.addView(grid2)

        scrollView.addView(dashboard)
        addView(scrollView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
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
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            },
        )
    }

    private fun formatMapText(state: VotvBridgeState?): String {
        val x = state?.playerLocX
        val y = state?.playerLocY
        val z = state?.playerLocZ
        if (x == null || y == null || z == null) return "LOCALIZATION\nWAITING FOR SIGNAL"
        return String.format(Locale.US, "LOCALIZATION (XYZ)\n%.0f  //  %.0f  //  %.0f", x, y, z)
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
        if (curr == null || max == null || max <= 0.0) return "CARGO CAPACITY\nWAITING FOR SIGNAL"
        val pct = (curr / max * 100).toInt()
        return String.format(Locale.US, "CARGO CAPACITY\n%d%% UTILIZED", pct)
    }

    private fun gridButton(label: String, onClick: () -> Unit) = Button(context).applyHubFont().applyHubButtonStyle().apply {
        text = label
        textSize = 15f
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, dp(72), 1f).apply {
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }
    }

    private fun showPage(title: String, page: View) {
        removeAllViews()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Header Bar
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = android.graphics.drawable.ColorDrawable(Color.BLACK)
        }
        val backBtn = Button(context).applyHubFont().applyHubButtonStyle().apply {
            text = "◀ MENU"
            textSize = 14f
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { showMenu() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
        }
        val titleText = TextView(context).applyHubFont().apply {
            text = " // $title"
            textSize = 18f
            setTextColor(hubGreen)
            setPadding(dp(12), 0, 0, 0)
        }
        header.addView(backBtn)
        header.addView(titleText)

        container.addView(header)

        // Separator
        container.addView(View(context).apply {
            background = android.graphics.drawable.ColorDrawable(hubGreenDim)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)))

        // Page Content
        container.addView(page, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        addView(container, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
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
        val scrollView = android.widget.ScrollView(context).apply {
            isFillViewport = true
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        column.addView(buildKeyGrid(hotkeys))
        column.addView(scrollWheelSection())
        scrollView.addView(column)
        return scrollView
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
                topMargin = dp(8)
                bottomMargin = dp(4)
            },
        )
        section.addView(ScrollWheelView(context, xServer), LinearLayout.LayoutParams(dp(90), dp(120)))
        return section
    }

    private fun buildKeyGridPage(keys: List<Pair<String, XKeycode>>): View {
        val scrollView = android.widget.ScrollView(context).apply {
            isFillViewport = true
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        column.addView(buildKeyGrid(keys))
        scrollView.addView(column)
        return scrollView
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
                    LinearLayout.LayoutParams(0, dp(64), 1f).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
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
        textSize = 16f
        setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    xServer.injectKeyPress(key)
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    xServer.injectKeyRelease(key)
                    v.performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    xServer.injectKeyRelease(key)
                }
            }
            true
        }
    }
}
