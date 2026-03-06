package com.example.doorframedetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    
    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 50f
        style = Paint.Style.FILL
    }

    private var detectionRect: RectF? = null
    private var detectionText: String? = null

    fun setDetection(rect: RectF?, text: String? = null) {
        detectionRect = rect
        detectionText = text
        invalidate() // Request redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        detectionRect?.let { rect ->
            canvas.drawRect(rect, boxPaint)
            
            detectionText?.let { text ->
                canvas.drawText(text, rect.left, rect.top - 20f, textPaint)
            }
        }
    }
}
