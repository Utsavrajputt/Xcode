package com.invictus.xcode.feature.editor

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import com.invictus.xcode.R
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow

/**
 * Sora's selection popup (select all / copy / paste / cut) ships as icon-only buttons. This swaps
 * each button's glyph for a text label while keeping the original button (and so its click and
 * enabled handling) intact. Sora's window internals aren't a stable API, so every step is
 * defensive: on any surprise the popup simply keeps its default icons.
 */
internal fun applyTextLabelsToSelectionPopup(editor: CodeEditor) {
    runCatching {
        val window = editor.getComponent(EditorTextActionWindow::class.java)
        val root = findContentView(window) ?: return
        relabel(root, editor.context)
    }
}

private fun findContentView(window: Any): View? {
    var cls: Class<*>? = window.javaClass
    while (cls != null && cls != Any::class.java) {
        for (field in cls.declaredFields) {
            runCatching {
                field.isAccessible = true
                when (val value = field.get(window)) {
                    is PopupWindow -> value.contentView?.let { return it }
                    is View -> return value
                }
            }
        }
        cls = cls.superclass
    }
    return null
}

private fun relabel(view: View, context: Context) {
    if (view is ImageView) {
        labelFor(view, context)?.let { label ->
            val density = context.resources.displayMetrics.density
            view.scaleType = ImageView.ScaleType.CENTER
            view.minimumWidth = 0
            view.setPadding((12 * density).toInt(), view.paddingTop, (12 * density).toInt(), view.paddingBottom)
            view.layoutParams = view.layoutParams?.apply { width = ViewGroup.LayoutParams.WRAP_CONTENT }
            view.setImageDrawable(LabelDrawable(label, context, view.imageTintList))
        }
        return
    }
    if (view is ViewGroup) for (i in 0 until view.childCount) relabel(view.getChildAt(i), context)
}

private fun labelFor(view: View, context: Context): String? {
    val entry = runCatching { context.resources.getResourceEntryName(view.id) }.getOrNull().orEmpty().lowercase()
    return when {
        "select_all" in entry -> context.getString(R.string.qa_select_all)
        "cut" in entry -> context.getString(R.string.qa_cut)
        "copy" in entry -> context.getString(R.string.qa_copy)
        "paste" in entry -> context.getString(R.string.qa_paste)
        else -> view.contentDescription?.toString()?.takeIf { it.isNotBlank() }
    }
}

/** Draws a text label where an icon drawable would go; follows the button's tint/enabled state. */
private class LabelDrawable(
    private val label: String,
    context: Context,
    private var tint: ColorStateList?,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val fallback: Int = TypedValue().let {
        context.theme.resolveAttribute(android.R.attr.textColorPrimary, it, true)
        if (it.resourceId != 0) context.getColor(it.resourceId) else it.data
    }
    private var color = tint?.defaultColor ?: fallback

    override fun isStateful() = true
    override fun onStateChange(state: IntArray): Boolean {
        val next = tint?.getColorForState(state, tint!!.defaultColor) ?: fallback
        val changed = next != color
        color = next
        return changed
    }

    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint
        color = tint?.getColorForState(state, tint.defaultColor) ?: fallback
        invalidateSelf()
    }

    override fun getIntrinsicWidth() = paint.measureText(label).toInt() + 1
    override fun getIntrinsicHeight() = paint.textSize.toInt()

    override fun draw(canvas: Canvas) {
        paint.color = color
        val b = bounds
        val y = b.exactCenterY() - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(label, b.exactCenterX(), y, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
