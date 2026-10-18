package cl.nestor.frioseguro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class TrendView(context: Context) : View(context) {
    private val values = ArrayDeque<Float>()
    private var lastId = -1L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Path()
    private val ticks = floatArrayOf(0f,8f,16f,24f)
    private val tickLabels = arrayOf("0","8","16","24")
    fun addSample(id: Long, value: Float) {
        if(id == lastId) return
        if(id < lastId) values.clear()
        lastId = id; values.addLast(value); while(values.size > 60) values.removeFirst(); invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = resources.displayMetrics.density
        val left = 30f * scale; val right = width - 8f * scale
        val top = 14f * scale; val bottom = height - 18f * scale
        fun y(temp: Float) = bottom - (temp.coerceIn(-5f,25f) + 5f) / 30f * (bottom - top)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = scale; paint.color = Color.rgb(217,229,232)
        for(t in ticks) canvas.drawLine(left,y(t),right,y(t),paint)
        paint.style = Paint.Style.FILL; paint.textSize = 10f * scale; paint.color = Color.DKGRAY
        for(i in ticks.indices) canvas.drawText(tickLabels[i],0f,y(ticks[i])+3f*scale,paint)
        paint.style = Paint.Style.STROKE; paint.color = Color.rgb(181,51,34)
        canvas.drawLine(left,y(8f),right,y(8f),paint)
        paint.color = Color.rgb(0,124,130); paint.strokeWidth = 2.5f * scale
        line.reset()
        values.forEachIndexed { i,v -> val x = left + i / 59f * (right-left); if(i == 0) line.moveTo(x,y(v)) else line.lineTo(x,y(v)) }
        canvas.drawPath(line,paint)
        values.lastOrNull()?.let { paint.style=Paint.Style.FILL; canvas.drawCircle(left+(values.size-1)/59f*(right-left),y(it),3f*scale,paint) }
    }
}
