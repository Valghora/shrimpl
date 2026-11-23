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
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/** One row of the App options panel (T5.8). */
sealed interface PanelOption

/** A switch: its fixed [title], which says what it does without a description, its state, and what a change does. */
class OptionSwitch(val title: String, val checked: Boolean, val onChange: (Boolean) -> Unit) : PanelOption

/**
 * A choice of a few values in a segmented button: [labels] name them, [descriptions] say what each does
 * (the row describes the selected value, docs/SHRIMPL_SPEC.md R1.6), and [onChange] gets the new index.
 */
class OptionChoice(
    val title: String,
    val labels: List<String>,
    val descriptions: List<String>,
    val selected: Int,
    val onChange: (Int) -> Unit,
) : PanelOption

/**
 * The App options panel (docs/TEMPLATE_SPEC.md T5.8): the app's own options, which apply at once,
 * and Done. A bottom sheet in a compact window, a dialog in a wider one (the window
 * size class, never the device type). Built in code, like the rest of the template's UI (T3.1).
 */
object AppOptionsPanel {
    /** Below this width the panel is a bottom sheet, Material's compact window class. */
    private const val SHEET_BELOW_DP = 600

    /**
     * The space between a row's text and its switch, as in Shrimpl's settings (the wrap line of
     * docs/SHRIMPL_SPEC.md R3.3), so a label wraps well clear of the switch. Every row's switch is the
     * same width, so the panel's text has one right edge.
     */
    private const val TEXT_GAP_DP = 24

    /** Shows the panel with [options]. Returns it, to dismiss later. */
    fun show(context: Context, options: List<PanelOption>): Dialog {
        val compact = context.resources.configuration.screenWidthDp < SHEET_BELOW_DP
        return if (compact) sheet(context, options) else dialog(context, options)
    }

    private fun dialog(context: Context, options: List<PanelOption>): Dialog {
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.app_options)
            .setView(body(context, options, insetDp = 24))
            .setPositiveButton(R.string.action_done, null)
        return builder.show()
    }

    private fun sheet(context: Context, options: List<PanelOption>): Dialog {
        val sheet = BottomSheetDialog(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            updatePadding(top = dp(context, 8), bottom = dp(context, 16))
            addView(text(context, context.getString(R.string.app_options), com.google.android.material.R.attr.textAppearanceHeadlineSmall).apply {
                updatePadding(left = dp(context, 24), right = dp(context, 24), bottom = dp(context, 8))
                ViewCompat.setAccessibilityHeading(this, true)
            })
            addView(body(context, options, insetDp = 24))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                updatePadding(left = dp(context, 12), right = dp(context, 12), top = dp(context, 8))
                addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
                addView(textButton(context, R.string.action_done) { sheet.dismiss() })
            })
        }
        sheet.setContentView(content)
        sheet.show()
        return sheet
    }

    /** One row per option; every app has at least Ad blocking, Dark mode and the Context menu. */
    private fun body(context: Context, options: List<PanelOption>, insetDp: Int): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            options.forEach { option ->
                addView(
                    when (option) {
                        is OptionSwitch -> row(context, option, insetDp)
                        is OptionChoice -> choiceRow(context, option, insetDp)
                    },
                )
            }
        }

    /**
     * A choice row: the title, the selected value's description and a full-width segmented button below
     * them, 48 dp tall (accessibility skill). Each segment is a toggle button that says whether it is
     * selected; the description is a live region, so a screen reader reads what the new value does.
     */
    private fun choiceRow(context: Context, option: OptionChoice, insetDp: Int): View {
        val description = text(context, option.descriptions[option.selected], com.google.android.material.R.attr.textAppearanceBodyMedium, secondary = true).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val title = text(context, option.title, com.google.android.material.R.attr.textAppearanceBodyLarge)
        val group = MaterialButtonToggleGroup(context).apply {
            isSingleSelection = true
            isSelectionRequired = true
            contentDescription = option.title
        }
        option.labels.forEachIndexed { index, label ->
            group.addView(
                MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    id = View.generateViewId()
                    text = label
                    minHeight = dp(context, 48)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            if (index == option.selected) group.check(group.getChildAt(index).id)
        }
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val index = (0 until group.childCount).first { group.getChildAt(it).id == checkedId }
            description.text = option.descriptions[index]
            option.onChange(index)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            updatePadding(left = dp(context, insetDp), right = dp(context, insetDp), top = dp(context, 12), bottom = dp(context, 12))
            addView(title)
            addView(description)
            addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(context, 12) })
        }
    }

    /**
     * A switch row: the whole row toggles, a target of at least 56 dp, and accessibility services hear
     * one switch named by its title, with its state (accessibility skill).
     */
    private fun row(context: Context, option: OptionSwitch, insetDp: Int): View {
        val switch = MaterialSwitch(context).apply {
            isChecked = option.checked
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val title = text(context, option.title, com.google.android.material.R.attr.textAppearanceBodyLarge)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 56)
            updatePadding(left = dp(context, insetDp), right = dp(context, insetDp), top = dp(context, 8), bottom = dp(context, 8))
            isFocusable = true
            background = selectableBackground(context)
            addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(context, TEXT_GAP_DP) })
            addView(switch)
            contentDescription = option.title
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
