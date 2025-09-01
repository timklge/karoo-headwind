package de.timklge.karooheadwind.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.ColorInt
import androidx.core.graphics.createBitmap
import kotlin.math.abs

class BarChartBuilder(val context: Context) {

    data class BarData(
        val value: Double,
        val label: String,
        val smallLabel: String,
        @ColorInt val color: Int
    )

    fun drawBarChart(
        width: Int,
        height: Int,
        small: Boolean,
        bars: List<BarData>
    ): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val isNightMode = isNightMode(context)

        val backgroundColor = if (isNightMode) Color.BLACK else Color.WHITE
        val primaryTextColor = if (isNightMode) Color.WHITE else Color.BLACK

        canvas.drawColor(backgroundColor)

        if (bars.isEmpty()) {
            val emptyPaint = Paint().apply {
                color = primaryTextColor
                textSize = 30f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("No data to display", width / 2f, height / 2f, emptyPaint)
            return bitmap
        }

        val marginTop = 45f
        val marginBottom = 45f
        val marginLeft = 5f
        val marginRight = 5f

        // Find the maximum absolute value to determine scale
        val maxValue = bars.maxOfOrNull { abs(it.value) } ?: 1.0
        val minValue = bars.minOfOrNull { it.value } ?: 0.0

        // Determine if we need to show negative values
        val hasNegativeValues = minValue < 0

        val chartWidth = width - marginLeft - marginRight
        val chartHeight = height - marginTop - marginBottom
        val chartLeft = marginLeft
        val chartTop = marginTop
        val chartBottom = if (hasNegativeValues) height - marginBottom else height - 5.0f

        val zeroY = if (hasNegativeValues) {
            chartTop + chartHeight * (maxValue / (maxValue - minValue)).toFloat()
        } else {
            chartBottom
        }

        // Calculate bar dimensions
        val barSpacing = 10f
        val totalSpacing = (bars.size - 1) * barSpacing
        val barWidth = (chartWidth - totalSpacing) / bars.size

        // Draw bars
        val barPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        bars.forEachIndexed { index, bar ->
            val barLeft = chartLeft + index * (barWidth + barSpacing)
            val barRight = barLeft + barWidth

            val barHeight = if (hasNegativeValues) {
                (abs(bar.value) / (maxValue - minValue) * chartHeight).toFloat()
            } else {
                (bar.value / maxValue * chartHeight).toFloat()
            }

            val barTop = if (bar.value >= 0) {
                zeroY - barHeight
            } else {
                zeroY
            }

            val barBottom = if (bar.value >= 0) {
                zeroY
            } else {
                zeroY + barHeight
            }

            // Draw bar
            barPaint.color = bar.color
            val rect = RectF(barLeft, barTop, barRight, barBottom)
            canvas.drawRect(rect, barPaint)

            // Draw label where value used to be with increased font size
            val labelPaint = Paint().apply {
                color = primaryTextColor
                textSize = 32f  // Increased from 24f and 28f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }

            // Use smallLabel if small is true, otherwise use regular label
            val labelToUse = if (small) bar.smallLabel else bar.label

            val labelY = if (bar.value >= 0) {
                barTop - 10f  // Position above positive bars
            } else {
                barBottom + labelPaint.textSize + 10f  // Position below negative bars
            }

            // Create semi-transparent background box for label
            val backgroundPaint = Paint().apply {
                color = if (isNightMode) Color.argb(200, 0, 0, 0) else Color.argb(200, 255, 255, 255)
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            // Calculate text bounds for background box
            val textBounds = android.graphics.Rect()
            labelPaint.getTextBounds(labelToUse, 0, labelToUse.length, textBounds)

            val padding = 8f
            val boxLeft = barLeft + barWidth / 2f - textBounds.width() / 2f - padding
            val boxRight = barLeft + barWidth / 2f + textBounds.width() / 2f + padding
            val boxTop = labelY - textBounds.height() - padding / 2f
            val boxBottom = labelY + padding / 2f

            // Draw rounded rectangle background
            val backgroundRect = RectF(boxLeft, boxTop, boxRight, boxBottom)
            val cornerRadius = 6f
            canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint)

            canvas.drawText(labelToUse, barLeft + barWidth / 2f, labelY, labelPaint)
        }

        return bitmap
    }
}
