package com.mindtype.mobile.ime

import android.content.Context
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet

/**
 * Custom KeyboardView subclass to allow future extensibility
 * (e.g., pressure-sensitive touch handling via MotionEvent override).
 */
class MindTypeKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : KeyboardView(context, attrs)
