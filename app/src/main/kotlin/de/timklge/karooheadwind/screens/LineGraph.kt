package de.timklge.karooheadwind.screens

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Path
import androidx.annotation.ColorInt
import kotlin.math.abs

class LineGraphBuilder(val context: Context) {
    data class DataPoint(val x: Float, val y: Float)

    data class AxisLabel(val x: Float, val label: String)

    data class Line(
        val dataPoints: List<DataPoint>,
        @ColorInt val color: Int,
        val label: String? = null,
    )

    private fun isNightMode(): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    fun drawLineGraph(width: Int, height: Int, gridWidth: Int, gridHeight: Int, lines: Set<Line>, labelProvider: ((Float) -> String)): Bitmap {
        val isNightMode = isNightMode()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val backgroundColor = if (isNightMode) Color.BLACK else Color.WHITE
        val primaryTextColor = if (isNightMode) Color.WHITE else Color.BLACK
        val secondaryTextColor = if (isNightMode) Color.LTGRAY else Color.DKGRAY // For axes

        canvas.drawColor(backgroundColor)

        if (lines.isEmpty() || lines.all { it.dataPoints.isEmpty() }) {
            val emptyPaint = Paint().apply {
                color = primaryTextColor
                textSize = 20f
                textAlign = Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("No data to display", width / 2f, height / 2f, emptyPaint)
            return bitmap
        }

        val marginTop = 10f
        val marginBottom = 40f // Increased from 30f
        val marginRight = 20f // Increased from 5f

        var dataMinX = Float.MAX_VALUE
        var dataMaxX = Float.MIN_VALUE
        var dataMinY = Float.MAX_VALUE
        var dataMaxY = Float.MIN_VALUE

        var hasData = false
        lines.forEach { line ->
            if (line.dataPoints.isNotEmpty()) hasData = true
            line.dataPoints.forEach { point ->
                dataMinX = minOf(dataMinX, point.x)
                dataMaxX = maxOf(dataMaxX, point.x)
                dataMinY = minOf(dataMinY, point.y)
                dataMaxY = maxOf(dataMaxY, point.y)
            }
        }
        if (!hasData) {
            val emptyPaint = Paint().apply {
                color = primaryTextColor
                textSize = 40f
                textAlign = Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("No data points", width / 2f, height / 2f, emptyPaint)
            return bitmap
        }

        // Dynamically calculate marginLeft based on Y-axis label widths
        val yAxisLabelPaint = Paint().apply {
            textSize = 28f // Matches textPaint for Y-axis labels
            isAntiAlias = true
        }

        var maxLabelWidth = 0f
        val yLabelStrings = mutableListOf<String>()
        val numYTicksForCalc = 2 // As used later for drawing Y-axis ticks

        // Determine Y-axis label strings (mirrors logic from where labels are drawn)
        if (kotlin.math.abs(dataMaxY - dataMinY) < 0.0001f) {
            yLabelStrings.add(String.format(java.util.Locale.getDefault(), "%.0f", dataMinY))
        } else {
            for (i in 0..numYTicksForCalc) {
                val value = dataMinY + ((dataMaxY - dataMinY) / numYTicksForCalc) * i
                yLabelStrings.add(String.format(java.util.Locale.getDefault(), "%.0f", value))
            }
        }

        for (labelStr in yLabelStrings) {
            maxLabelWidth = kotlin.math.max(maxLabelWidth, yAxisLabelPaint.measureText(labelStr))
        }

        val yAxisTextRightToAxisGap = 15f // Current gap used: graphLeft - 15f
        val canvasEdgePadding = 5f       // Desired padding from the canvas edge

        val dynamicMarginLeft = maxLabelWidth + yAxisTextRightToAxisGap + canvasEdgePadding

        val marginLeft = dynamicMarginLeft

        val graphWidth = width - marginLeft - marginRight
        val graphHeight = height - marginTop - marginBottom
        val graphLeft = marginLeft
        val graphTop = marginTop
        val graphBottom = height - marginBottom

        // Legend properties
        val legendTextSize = 22f // Increased from 18f
        val legendTextColor = primaryTextColor
        val legendPadding = 5f
        val legendEntryHeight = 30f // Increased from 25f
        val legendColorBoxSize = 24f // Increased from 20f
        val legendTextMargin = 5f

        var effectiveMinX = dataMinX
        var effectiveMaxX = dataMaxX
        var effectiveMinY = dataMinY
        var effectiveMaxY = dataMaxY

        if (dataMinX == dataMaxX) {
            effectiveMinX -= 1f
            effectiveMaxX += 1f
        } else {
            val paddingX = (dataMaxX - dataMinX) * 0.05f
            if (paddingX > 0.0001f) {
                effectiveMinX -= paddingX
                effectiveMaxX += paddingX
            } else {
                effectiveMinX -= 1f
                effectiveMaxX += 1f
            }
        }

        if (dataMinY == dataMaxY) {
            effectiveMinY -= 1f
            effectiveMaxY += 1f
        } else {
            val paddingY = (dataMaxY - dataMinY) * 0.05f
            if (paddingY > 0.0001f) {
                effectiveMinY -= paddingY
                effectiveMaxY += paddingY
            } else {
                effectiveMinY -= 1f
                effectiveMaxY += 1f
            }
        }

        val rangeX = if (abs(effectiveMaxX - effectiveMinX) < 0.0001f) 1f else (effectiveMaxX - effectiveMinX)
        val rangeY = if (abs(effectiveMaxY - effectiveMinY) < 0.0001f) 1f else (effectiveMaxY - effectiveMinY)

        fun mapX(originalX: Float): Float {
            return graphLeft + ((originalX - effectiveMinX) / rangeX) * graphWidth
        }

        fun mapY(originalY: Float): Float {
            return graphBottom - ((originalY - effectiveMinY) / rangeY) * graphHeight
        }

        val axisPaint = Paint().apply {
            color = secondaryTextColor
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawLine(graphLeft, graphBottom, graphLeft + graphWidth, graphBottom, axisPaint)
        canvas.drawLine(graphLeft, graphTop, graphLeft, graphBottom, axisPaint)

        val linePaint = Paint().apply {
            strokeWidth = 6f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val textPaint = Paint().apply {
            color = primaryTextColor
            textSize = 28f
            isAntiAlias = true
        }

        for (line in lines) {
            if (line.dataPoints.isEmpty()) continue

            linePaint.color = line.color
            val path = Path()
            val firstPoint = line.dataPoints.first()
            path.moveTo(mapX(firstPoint.x), mapY(firstPoint.y))
            canvas.drawCircle(mapX(firstPoint.x), mapY(firstPoint.y), 8f, linePaint.apply { style = Paint.Style.FILL })
            linePaint.style = Paint.Style.STROKE

            for (i in 1 until line.dataPoints.size) {
                val point = line.dataPoints[i]
                path.lineTo(mapX(point.x), mapY(point.y))
                canvas.drawCircle(mapX(point.x), mapY(point.y), 8f, linePaint.apply { style = Paint.Style.FILL })
                linePaint.style = Paint.Style.STROKE
            }
            canvas.drawPath(path, linePaint)
        }

        textPaint.textAlign = Align.RIGHT
        val numYTicks = if (gridWidth > 15) 2 else 1
        if (abs(dataMaxY - dataMinY) > 0.0001f) {
            for (i in 0..numYTicks) {
                val value = dataMinY + ((dataMaxY - dataMinY) / numYTicks) * i
                val yPos = mapY(value)
                if (yPos >= graphTop - 5f && yPos <= graphBottom + 5f) {
                    canvas.drawLine(graphLeft - 5f, yPos, graphLeft + 5f, yPos, axisPaint)
                    canvas.drawText(String.format(java.util.Locale.getDefault(), "%.0f", value), graphLeft - 15f, yPos + (textPaint.textSize / 3), textPaint) // Adjusted horizontal offset from -10f
                }
            }
        } else {
            val yPos = mapY(dataMinY)
            canvas.drawLine(graphLeft - 5f, yPos, graphLeft + 5f, yPos, axisPaint)
            canvas.drawText(String.format(java.util.Locale.getDefault(), "%.0f", dataMinY), graphLeft - 15f, yPos + (textPaint.textSize / 3), textPaint) // Adjusted horizontal offset from -10f
        }

        textPaint.textAlign = Align.CENTER
        val numXTicks = if (gridHeight > 15) 3 else 1
        if (abs(dataMaxX - dataMinX) > 0.0001f) {
            for (i in 0..numXTicks) {
                val value = dataMinX + ((dataMaxX - dataMinX) / numXTicks) * i
                val xPos = mapX(value)
                if (xPos >= graphLeft - 5f && xPos <= graphLeft + graphWidth + 5f) {
                    canvas.drawLine(xPos, graphBottom - 5f, xPos, graphBottom + 5f, axisPaint)
                    canvas.drawText(labelProvider(xPos), xPos, graphBottom + 30f, textPaint)
                }
            }
        } else {
            val xPos = mapX(dataMinX)
            canvas.drawLine(xPos, graphBottom - 5f, xPos, graphBottom + 5f, axisPaint)
            canvas.drawText(labelProvider(xPos), xPos, graphBottom + 30f, textPaint)
        }

        textPaint.textAlign = Align.CENTER
        textPaint.color = primaryTextColor // Ensure textPaint color is reset before drawing legend

        // Draw Legend
        val legendPaint = Paint().apply {
            textSize = legendTextSize
            color = legendTextColor
            isAntiAlias = true
            textAlign = Align.LEFT // Important for measuring text width correctly
        }
        val legendColorPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val legendItems = lines.filter { it.label != null }
        if (legendItems.isNotEmpty()) {
            var maxLegendLabelWidth = 0f
            for (item in legendItems) {
                maxLegendLabelWidth = kotlin.math.max(maxLegendLabelWidth, legendPaint.measureText(item.label!!))
            }

            val legendContentActualLeft = (width - marginRight - legendPadding - legendColorBoxSize - legendTextMargin - maxLegendLabelWidth)
            val legendContentActualRight = (width - marginRight - legendPadding) // Right edge of the color box

            val legendContentActualTop = graphTop + legendPadding // Top edge of the first color box
            val legendContentActualBottom = legendContentActualTop + (legendItems.size - 1) * legendEntryHeight + legendColorBoxSize // Bottom edge of the last color box

            val legendBgPaint = Paint().apply {
                color = if (isNightMode) {
                    Color.argb(200, 0, 0, 0)
                } else {
                    Color.argb(200, 255, 255, 255)
                }
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawRoundRect(
                legendContentActualLeft,
                legendContentActualTop,
                legendContentActualRight,
                legendContentActualBottom,
                5f,
                5f,
                legendBgPaint
            )
        }

        var currentLegendY = graphTop + legendPadding

        for (line in legendItems) { // Use the filtered list, was: lines.filter { it.label != null }
            // Draw color box
            legendColorPaint.color = line.color
            canvas.drawRect(
                width - marginRight - legendPadding - legendColorBoxSize, // left
                currentLegendY, // top
                width - marginRight - legendPadding, // right
                currentLegendY + legendColorBoxSize, // bottom
                legendColorPaint
            )

            // Draw label text
            canvas.drawText(
                line.label!!,
                width - marginRight - legendPadding - legendColorBoxSize - legendTextMargin - legendPaint.measureText(line.label), // x: Align text to the left of the color box
                currentLegendY + legendColorBoxSize / 2 + legendTextSize / 3, // y: Vertically center text with color box
                legendPaint
            )
            currentLegendY += legendEntryHeight
        }

        return bitmap
    }
}
