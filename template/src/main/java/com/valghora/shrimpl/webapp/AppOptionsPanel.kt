package com.valghora.shrimpl.webapp

import android.app.Dialog
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/** One switch of the App options panel (T5.8): fixed [title] and [description], its state, and what a change does. */
class OptionSwitch(val title: String, val description: String, val checked: Boolean, val onChange: (Boolean) -> Unit)

/**
 * The App options panel (docs/TEMPLATE_SPEC.md T5.8): the app's own switches, which apply at once,
 * Reset to defaults and Done. A bottom sheet in a compact window, a dialog in a wider one (the window
 * size class, never the device type). Built in code, like the rest of the template's UI (T3.1).
 */
object AppOptionsPanel {
    /** Below this width the panel is a bottom sheet, Material's compact window class. */
    private const val SHEET_BELOW_DP = 600

    /**
     * The space between a row's text and its switch, as in Shrimpl's settings (the wrap line of
     * docs/SHRIMPL_SPEC.md R3.3), so a description wraps well clear of the switch. Every row's switch is the
     * same width, so the panel's text has one right edge.
     */
    private const val TEXT_GAP_DP = 24

    /** Shows the panel with [switches]; [onReset] runs on Reset to defaults. Returns it, to dismiss later. */
    fun show(context: Context, switches: List<OptionSwitch>, onReset: () -> Unit): Dialog {
        val compact = context.resources.configuration.screenWidthDp < SHEET_BELOW_DP
        return if (compact) sheet(context, switches, onReset) else dialog(context, switches, onReset)
    }

    private fun dialog(context: Context, switches: List<OptionSwitch>, onReset: () -> Unit): Dialog {
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.app_options)
            .setView(body(context, switches, insetDp = 24))
            .setPositiveButton(R.string.action_done, null)
        if (switches.isNotEmpty()) builder.setNeutralButton(R.string.action_reset_defaults) { _, _ -> onReset() }
        return builder.show()
    }

    private fun sheet(context: Context, switches: List<OptionSwitch>, onReset: () -> Unit): Dialog {
        val sheet = BottomSheetDialog(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            updatePadding(top = dp(context, 8), bottom = dp(context, 16))
            addView(text(context, context.getString(R.string.app_options), com.google.android.material.R.attr.textAppearanceHeadlineSmall).apply {
                updatePadding(left = dp(context, 24), right = dp(context, 24), bottom = dp(context, 8))
                ViewCompat.setAccessibilityHeading(this, true)
            })
            addView(body(context, switches, insetDp = 24))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                updatePadding(left = dp(context, 12), right = dp(context, 12), top = dp(context, 8))
                if (switches.isNotEmpty()) {
                    addView(textButton(context, R.string.action_reset_defaults) {
                        onReset()
                        sheet.dismiss()
                    })
                }
                addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
                addView(textButton(context, R.string.action_done) { sheet.dismiss() })
            })
        }
        sheet.setContentView(content)
        sheet.show()
        return sheet
    }

    /** One row per switch, or a line saying there are none yet. */
    private fun body(context: Context, switches: List<OptionSwitch>, insetDp: Int): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (switches.isEmpty()) {
                addView(text(context, context.getString(R.string.app_options_none), com.google.android.material.R.attr.textAppearanceBodyLarge).apply {
                    updatePadding(left = dp(context, insetDp), right = dp(context, insetDp), top = dp(context, 8), bottom = dp(context, 8))
                })
            }
            switches.forEach { addView(row(context, it, insetDp)) }
        }

    /**
     * A switch row: the whole row toggles, a target of at least 72 dp, and accessibility services hear
     * one switch named by its title and description, with its state (accessibility skill).
     */
    private fun row(context: Context, option: OptionSwitch, insetDp: Int): View {
        val switch = MaterialSwitch(context).apply {
            isChecked = option.checked
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(context, option.title, com.google.android.material.R.attr.textAppearanceBodyLarge))
            addView(text(context, option.description, com.google.android.material.R.attr.textAppearanceBodyMedium, secondary = true))
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 72)
            updatePadding(left = dp(context, insetDp), right = dp(context, insetDp), top = dp(context, 8), bottom = dp(context, 8))
            isFocusable = true
            background = selectableBackground(context)
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(context, TEXT_GAP_DP) })
            addView(switch)
            contentDescription = "${option.title}, ${option.description}"
            setOnClickListener {
                switch.isChecked = !switch.isChecked
                option.onChange(switch.isChecked)
                sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED)
            }
            ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = android.widget.Switch::class.java.name
                    info.isCheckable = true
                    info.setChecked(
                        if (switch.isChecked) AccessibilityNodeInfoCompat.CHECKED_STATE_TRUE else AccessibilityNodeInfoCompat.CHECKED_STATE_FALSE,
                    )
                }
            })
        }
    }

    private fun text(context: Context, value: String, appearance: Int, secondary: Boolean = false) = TextView(context).apply {
        text = value
        setTextAppearance(resolve(context, appearance))
        if (secondary) setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
    }

    private fun textButton(context: Context, label: Int, onClick: () -> Unit) =
        MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            setText(label)
            setOnClickListener { onClick() }
        }

    private fun selectableBackground(context: Context) =
        context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).use { it.getDrawable(0) }

    private fun resolve(context: Context, attr: Int): Int =
        TypedValue().also { context.theme.resolveAttribute(attr, it, true) }.resourceId

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
