package com.doujinzip.android.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.abs

/** A single-line editor with a caret handle that keeps moving while held at either edge. */
internal class SearchQueryInput(context: Context) : AppCompatEditText(context) {
    private val density = resources.displayMetrics.density
    private val caretPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var draggingCaret = false
    private var dragX = 0f
    private var blink = true
    var caretColor: Int = currentTextColor
        set(value) { field = value; invalidate() }

    init {
        isFocusableInTouchMode = true
        // Keep platform range selection/clipboard, but own the insertion handle and its edge scrolling.
        isCursorVisible = false
    }

    internal fun caretHandlePosition(): PointF {
        val textLayout = layout ?: return PointF()
        val offset = selectionStart.coerceIn(0, text?.length ?: 0)
        val line = textLayout.getLineForOffset(offset)
        return PointF((totalPaddingLeft + textLayout.getPrimaryHorizontal(offset) - scrollX)
            .coerceIn(totalPaddingLeft.toFloat(), (width - totalPaddingRight).toFloat().coerceAtLeast(totalPaddingLeft.toFloat())),
            baseline - textLayout.getLineBaseline(0) + textLayout.getLineBottom(line) - scrollY + 5 * density)
    }

    private val edgeScroll = object : Runnable {
        override fun run() {
            if (!draggingCaret || !hasFocus()) return
            val edge = 24 * density
            val direction = when {
                dragX < totalPaddingLeft + edge -> -1
                dragX > width - totalPaddingRight - edge -> 1
                else -> 0
            }
            if (direction != 0) {
                val previousScroll = scrollX
                val previousSelection = selectionStart
                val contentWidth = layout?.getLineWidth(0) ?: 0f
                val maxScroll = (contentWidth - width + totalPaddingLeft + totalPaddingRight).toInt().coerceAtLeast(0)
                scrollTo((scrollX + direction * (3 * density).toInt().coerceAtLeast(1)).coerceIn(0, maxScroll), scrollY)
                moveCaretToDrag()
                if (scrollX != previousScroll || selectionStart != previousSelection) postOnAnimation(this)
            }
        }
    }

    private fun moveCaretToDrag() {
        val position = caretHandlePosition()
        val x = dragX.coerceIn(totalPaddingLeft.toFloat(), (width - totalPaddingRight).toFloat())
        setSelection(getOffsetForPosition(x, position.y - 6 * density))
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && hasFocus() && selectionStart == selectionEnd) {
            val handle = caretHandlePosition()
            if (abs(event.x - handle.x) <= 20 * density && abs(event.y - handle.y) <= 18 * density) {
                draggingCaret = true
                dragX = event.x
                parent?.requestDisallowInterceptTouchEvent(true)
                removeCallbacks(edgeScroll)
                postOnAnimation(edgeScroll)
                return true
            }
        }
        if (!draggingCaret) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                dragX = event.x
                moveCaretToDrag()
                removeCallbacks(edgeScroll)
                postOnAnimation(edgeScroll)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingCaret = false
                removeCallbacks(edgeScroll)
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun performLongClick(): Boolean {
        val platformHandled = super.performLongClick()
        if (selectionStart >= 0 && selectionEnd > selectionStart) return platformHandled
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val popup = android.widget.PopupMenu(context, this)
        val selected = selectionStart >= 0 && selectionEnd > selectionStart
        if (selected) {
            popup.menu.add(0, android.R.id.copy, 0, android.R.string.copy)
            popup.menu.add(0, android.R.id.cut, 1, android.R.string.cut)
        }
        if (clipboard.hasPrimaryClip()) popup.menu.add(0, android.R.id.paste, 2, android.R.string.paste)
        if (!text.isNullOrEmpty()) popup.menu.add(0, android.R.id.selectAll, 3, android.R.string.selectAll)
        popup.setOnMenuItemClickListener { onTextContextMenuItem(it.itemId) }
        if (!popup.menu.hasVisibleItems()) return platformHandled
        popup.show()
        return true
    }

    private val cursorBlink = object : Runnable {
        override fun run() {
            if (!hasFocus()) return
            blink = !blink
            invalidate()
            postDelayed(this, 500)
        }
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (!isAttachedToWindow) return
        if (focused) { blink = true; postDelayed(cursorBlink, 500) }
        else { removeCallbacks(cursorBlink); removeCallbacks(edgeScroll); draggingCaret = false }
        invalidate()
    }

    override fun onSelectionChanged(start: Int, end: Int) {
        super.onSelectionChanged(start, end)
        if (!isAttachedToWindow) return
        blink = true
        removeCallbacks(cursorBlink)
        if (hasFocus()) postDelayed(cursorBlink, 500)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasFocus() || selectionStart != selectionEnd) return
        val textLayout = layout ?: return
        val handle = caretHandlePosition()
        val line = textLayout.getLineForOffset(selectionStart.coerceIn(0, text?.length ?: 0))
        caretPaint.color = caretColor
        caretPaint.strokeWidth = 2 * density
        // TextView draws its content on a canvas already translated by its scroll position.
        val x = handle.x + scrollX
        val top = baseline - textLayout.getLineBaseline(0)
        val bottom = top + textLayout.getLineBottom(line).toFloat()
        if (blink || draggingCaret) canvas.drawLine(x, top + textLayout.getLineTop(line).toFloat(), x, bottom, caretPaint)
        canvas.drawCircle(x, handle.y + scrollY, 5 * density, caretPaint)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(edgeScroll)
        removeCallbacks(cursorBlink)
        draggingCaret = false
        super.onDetachedFromWindow()
    }
}
