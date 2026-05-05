package com.mindtype.mobile.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet

/**
 * Custom KeyboardView subclass with advanced UI rendering.
 * Provides a premium, soft-typing look with rounded keys and clear typography.
 */
class MindTypeKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : KeyboardView(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgRect = RectF()

    override fun onDraw(canvas: Canvas) {
        // Draw modern dark background
        canvas.drawColor(Color.parseColor("#0A0A0B"))

        val keys = keyboard?.keys ?: return
        
        for (key in keys) {
            drawKey(canvas, key)
        }
    }

    private fun drawKey(canvas: Canvas, key: Keyboard.Key) {
        val isSpecialKey = key.codes.firstOrNull() ?: 0 < 0 || key.codes.firstOrNull() == 32
        
        // Key background color
        if (key.pressed) {
            paint.color = Color.parseColor("#475569") // Pressed state
        } else if (isSpecialKey && key.codes.first() != 32) { // 32 is space
            paint.color = Color.parseColor("#2D2D30") // Modifier keys
        } else {
            paint.color = Color.parseColor("#161618") // Normal keys
        }

        // Draw rounded rectangle for key
        val padding = 8f
        bgRect.set(
            key.x.toFloat() + padding,
            key.y.toFloat() + padding,
            (key.x + key.width).toFloat() - padding,
            (key.y + key.height).toFloat() - padding
        )
        canvas.drawRoundRect(bgRect, 16f, 16f, paint)

        // Draw key label or icon
        val label = key.label
        if (label != null) {
            // Text color & font setup
            if (isSpecialKey) {
                paint.color = Color.parseColor("#94A3B8") // Muted for special keys
                paint.typeface = Typeface.DEFAULT
            } else {
                paint.color = Color.parseColor("#F8FAFC") // Bright white for letters
                paint.typeface = Typeface.DEFAULT_BOLD
            }
            paint.textSize = if (label.length > 1) 36f else 54f
            
            // Emoji size override
            if (label.length == 2 && Character.isSurrogatePair(label[0], label[1])) {
                 paint.textSize = 64f
            }

            paint.textAlign = Paint.Align.CENTER
            
            // Vertical centering calculation
            val textHeight = paint.descent() - paint.ascent()
            val textOffset = (textHeight / 2) - paint.descent()
            
            // Uppercase the label if shifted
            val textToDraw = if (keyboard?.isShifted == true && label.length == 1 && label[0].isLetter()) {
                label.toString().uppercase()
            } else {
                label.toString()
            }

            canvas.drawText(
                textToDraw,
                bgRect.centerX(),
                bgRect.centerY() + textOffset,
                paint
            )
        }
    }
}
