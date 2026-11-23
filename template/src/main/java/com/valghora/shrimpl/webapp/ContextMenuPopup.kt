package com.valghora.shrimpl.webapp

import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.updatePadding
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * The context menu of links and images (docs/TEMPLATE_SPEC.md T5.5), drawn as Android 17 draws its own
 * menus: Material 3 Expressive's menu on the theme's dynamic colors (T3.1), a 16 dp container on the
 * surface container color with 12 dp rounded item highlights and a leading icon per item. It opens at
 * the pointer or finger, for right-click and long press alike, and keeps inside the window.
 */
object ContextMenuPopup {
    /** One item: its icon, label and action; [dividerBefore] starts a new group. */
    class Item(val icon: Int, val label: String, val dividerBefore: Boolean = false, val onClick: () -> Unit)

    private const val CORNER_DP = 16
    private const val ITEM_CORNER_DP = 12
    private const val MIN_WIDTH_DP = 200

    /** Item height: Material's 48 dp touch target (accessibility skill), for fingers as well as pointers. */
    private const val ITEM_HEIGHT_DP = 48

    /**
     * Shows [items] at [x], [y] in [anchor]'s coordinates and returns the popup, to dismiss later. Focusable,
     * so arrow keys move between items, Enter selects and Esc or Back closes it.
     */
    fun show(anchor: View, x: Float, y: Float, items: List<Item>): PopupWindow {
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val popup = PopupWindow(context)
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumWidth = dp(MIN_WIDTH_DP)
            updatePadding(top = dp(4), bottom = dp(4))
            accessibilityPaneTitle = context.getString(R.string.context_menu_title)
        }
        items.forEach { item ->
            if (item.dividerBefore) {
                list.addView(
                    View(context).apply { setBackgroundColor(MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorOutlineVariant)) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                        setMargins(dp(12), dp(4), dp(12), dp(4))
                    },
                )
            }
            list.addView(row(anchor, item, ::dp) { popup.dismiss() })
        }

        val shape = MaterialShapeDrawable(ShapeAppearanceModel.builder().setAllCornerSizes(dp(CORNER_DP).toFloat()).build()).apply {
            fillColor = ColorStateList.valueOf(MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorSurfaceContainer))
        }
        popup.apply {
            contentView = list
            setBackgroundDrawable(shape)
            elevation = dp(3).toFloat()
            isFocusable = true
            isOutsideTouchable = true
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        // Keep the whole menu inside the window: it opens below and after the point, and flips when it
        // would run past an edge.
        list.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val origin = IntArray(2).also { anchor.getLocationInWindow(it) }
        val root = anchor.rootView
        var left = origin[0] + x.toInt()
        var top = origin[1] + y.toInt()
        if (left + list.measuredWidth > root.width) left = (left - list.measuredWidth).coerceAtLeast(0)
        if (top + list.measuredHeight > root.height) top = (top - list.measuredHeight).coerceAtLeast(0)
        popup.showAtLocation(anchor, Gravity.TOP or Gravity.START, left, top)
        list.getChildAt(0)?.takeIf { !it.isInTouchMode }?.requestFocus()
        return popup
    }

    private fun row(anchor: View, item: Item, dp: (Int) -> Int, dismiss: () -> Unit): View {
        val context = anchor.context
        val highlight = MaterialShapeDrawable(ShapeAppearanceModel.builder().setAllCornerSizes(dp(ITEM_CORNER_DP).toFloat()).build())
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ITEM_HEIGHT_DP)
            updatePadding(left = dp(12), right = dp(16))
            isClickable = true
            isFocusable = true
            // The ripple and the hover and focus highlight share the item's rounded shape.
            background = RippleDrawable(
                ColorStateList.valueOf(MaterialColors.getColor(anchor, androidx.appcompat.R.attr.colorControlHighlight)),
                null,
                highlight,
            )
            addView(
                ImageView(context).apply {
                    setImageResource(item.icon)
                    imageTintList = ColorStateList.valueOf(MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorOnSurfaceVariant))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) },
            )
            addView(
                TextView(context).apply {
                    text = item.label
                    setTextAppearance(resolve(context, com.google.android.material.R.attr.textAppearanceLabelLarge))
                    setTextColor(MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorOnSurface))
                },
            )
            setOnClickListener {
                dismiss()
                item.onClick()
            }
        }
    }

    private fun resolve(context: android.content.Context, attr: Int): Int =
        TypedValue().also { context.theme.resolveAttribute(attr, it, true) }.resourceId
}
