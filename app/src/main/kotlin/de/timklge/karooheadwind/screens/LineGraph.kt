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
import androidx.core.graphics.createBitmap

class LineGraphBuilder(val context: Context) {
    enum class YAxis {
        LEFT, RIGHT
    }

    data class DataPoint(val x: Float, val y: Float)

    data class Line(
        val dataPoints: List<DataPoint>,
        @ColorInt val color: Int,
        val label: String? = null,
        val yAxis: YAxis = YAxis.LEFT, // Default to left Y-axis
        val drawCircles: Boolean = true // Default to true
    )

    private fun isNightMode(): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    fun drawLineGraph(
        width: Int,
        height: Int,
        gridWidth: Int,
        gridHeight: Int,
        lines: Set<Line>,
        labelProvider: ((Float) -> String)
    ): Bitmap {
        val isNightMode = isNightMode()

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        val backgroundColor = if (isNightMode) Color.BLACK else Color.WHITE
        val primaryTextColor = if (isNightMode) Color.WHITE else Color.BLACK
        val secondaryTextColor = if (isNightMode) Color.LTGRAY else Color.DKGRAY // For axes

        canvas.drawColor(backgroundColor)

        if (lines.isEmpty() || lines.all { it.dataPoints.isEmpty() }) {
            val emptyPaint = Paint().apply {
                color = primaryTextColor
                textSize = 24f // Increased from 20f
                textAlign = Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("No data to display", width / 2f, height / 2f, emptyPaint)
            return bitmap
        }

        val marginTop = 10f
        val marginBottom = 40f // Increased from 30f
        var marginRight = 20f // Increased from 5f // Made var

        var dataMinX = Float.MAX_VALUE
        var dataMaxX = Float.MIN_VALUE
        var dataMinYLeft = Float.MAX_VALUE
        var dataMaxYLeft = Float.MIN_VALUE
        var dataMinYRight = Float.MAX_VALUE
        var dataMaxYRight = Float.MIN_VALUE
        var hasLeftYAxisData = false
        var hasRightYAxisData = false

        var hasData = false
        lines.forEach { line ->
            if (line.dataPoints.isNotEmpty()) {
                hasData = true
                if (line.yAxis == YAxis.LEFT) {
                    hasLeftYAxisData = true
                    line.dataPoints.forEach { point ->
                        dataMinX = minOf(dataMinX, point.x)
                        dataMaxX = maxOf(dataMaxX, point.x)
                        dataMinYLeft = minOf(dataMinYLeft, point.y)
                        dataMaxYLeft = maxOf(dataMaxYLeft, point.y)
                    }
                } else { // YAxis.RIGHT
                    hasRightYAxisData = true
                    line.dataPoints.forEach { point ->
                        dataMinX = minOf(dataMinX, point.x)
                        dataMaxX = maxOf(dataMaxX, point.x)
                        dataMinYRight = minOf(dataMinYRight, point.y)
                        dataMaxYRight = maxOf(dataMaxYRight, point.y)
                    }
                }
            }
        }
        if (!hasData) {
            val emptyPaint = Paint().apply {
                color = primaryTextColor
                textSize = 48f // Increased from 40f
                textAlign = Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("No data points", width / 2f, height / 2f, emptyPaint)
            return bitmap
        }

        // Dynamically calculate marginLeft based on Y-axis label widths
        val yAxisLabelPaint = Paint().apply {
            textSize = 32f
            isAntiAlias = true
        }

        var maxLabelWidthLeft = 0f
        if (hasLeftYAxisData) {
            val yLabelStringsLeft = mutableListOf<String>()
            val numYTicksForCalc = 2 // As used later for drawing Y-axis ticks

            // Determine Y-axis label strings (mirrors logic from where labels are drawn)
            if (abs(dataMaxYLeft - dataMinYLeft) < 0.0001f) {
                yLabelStringsLeft.add(
                    String.format(
                        java.util.Locale.getDefault(),
                        "%.0f",
                        dataMinYLeft
                    )
                )
            } else {
                for (i in 0..numYTicksForCalc) {
                    val value =
                        dataMinYLeft + ((dataMaxYLeft - dataMinYLeft) / numYTicksForCalc) * i
                    yLabelStringsLeft.add(
                        String.format(
                            java.util.Locale.getDefault(),
                            "%.0f",
                            value
                        )
                    )
                }
            }

            for (labelStr in yLabelStringsLeft) {
                maxLabelWidthLeft =
                    kotlin.math.max(maxLabelWidthLeft, yAxisLabelPaint.measureText(labelStr))
            }
        }

        val yAxisTextRightToAxisGap = 15f // Current gap used: graphLeft - 15f
        val canvasEdgePadding = 5f       // Desired padding from the canvas edge

        val dynamicMarginLeft =
            if (hasLeftYAxisData) maxLabelWidthLeft + yAxisTextRightToAxisGap + canvasEdgePadding else canvasEdgePadding

        // Dynamically calculate marginRight based on Right Y-axis label widths
        var maxLabelWidthRight = 0f
        if (hasRightYAxisData) {
            val yLabelStringsRight = mutableListOf<String>()
            val numYTicksForCalc = 2 // As used later for drawing Y-axis ticks

            if (abs(dataMaxYRight - dataMinYRight) < 0.0001f) {
                yLabelStringsRight.add(
                    String.format(
                        java.util.Locale.getDefault(),
                        "%.0f",
                        dataMinYRight
                    )
                )
            } else {
                for (i in 0..numYTicksForCalc) {
                    val value =
                        dataMinYRight + ((dataMaxYRight - dataMinYRight) / numYTicksForCalc) * i
                    yLabelStringsRight.add(
                        String.format(
                            java.util.Locale.getDefault(),
                            "%.0f",
                            value
                        )
                    )
                }
            }

            for (labelStr in yLabelStringsRight) {
                maxLabelWidthRight =
                    kotlin.math.max(maxLabelWidthRight, yAxisLabelPaint.measureText(labelStr))
            }
            val dynamicMarginRight =
                maxLabelWidthRight + yAxisTextRightToAxisGap + canvasEdgePadding
            marginRight = dynamicMarginRight // Update marginRight
        }

        val graphWidth = width - dynamicMarginLeft - marginRight
        val graphHeight = height - marginTop - marginBottom
        val graphLeft = dynamicMarginLeft
        val graphTop = marginTop
        val graphBottom = height - marginBottom
        val graphRight = width - marginRight // Define graphRight for clarity

        // Legend properties
        val legendTextSize = 26f // Increased from 22f
        val legendTextColor = primaryTextColor
        val legendPadding = 5f
        val legendEntryHeight = 30f // Increased from 25f
        val legendColorBoxSize = 24f // Increased from 20f
        val legendTextMargin = 5f

        var effectiveMinX = dataMinX
        var effectiveMaxX = dataMaxX
        var effectiveMinYLeft = dataMinYLeft
        var effectiveMaxYLeft = dataMaxYLeft
        var effectiveMinYRight = dataMinYRight
        var effectiveMaxYRight = dataMaxYRight

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

        // Y-axis Left: Adjust effective range based on new rules
        if (hasLeftYAxisData) {
            // effectiveMinYLeft is dataMinYLeft, effectiveMaxYLeft is dataMaxYLeft at this point
            if (abs(dataMaxYLeft - dataMinYLeft) < 0.0001f) { // All Y_Left values are equal
                val commonValue = dataMinYLeft
                if (commonValue >= 0f) {
                    effectiveMinYLeft = 0f
                    effectiveMaxYLeft =
                        if (commonValue == 0f) 1f else commonValue + kotlin.math.max(
                            abs(commonValue * 0.1f),
                            1f
                        )
                } else { // commonValue < 0f
                    effectiveMaxYLeft = 0f
                    effectiveMinYLeft = commonValue - kotlin.math.max(abs(commonValue * 0.1f), 1f)
                }
            } else { // Y_Left values are not all equal, apply standard 5% padding
                val paddingYLeft = (dataMaxYLeft - dataMinYLeft) * 0.05f
                if (paddingYLeft > 0.0001f) {
                    effectiveMinYLeft -= paddingYLeft // equivalent to dataMinYLeft - padding
                    effectiveMaxYLeft += paddingYLeft // equivalent to dataMaxYLeft + padding
                } else {
                    effectiveMinYLeft -= 1f
                    effectiveMaxYLeft += 1f
                }
            }
            // Safety check: ensure min < max for left Y-axis
            if (effectiveMinYLeft >= effectiveMaxYLeft) {
                effectiveMaxYLeft = effectiveMinYLeft + 1f
            }
        }

        // Y-axis Right: Adjust effective range based on new rules
        if (hasRightYAxisData) {
            // effectiveMinYRight is dataMinYRight, effectiveMaxYRight is dataMaxYRight at this point
            if (abs(dataMaxYRight - dataMinYRight) < 0.0001f) { // All Y_Right values are equal
                val commonValue = dataMinYRight
                if (commonValue >= 0f) {
                    effectiveMinYRight = 0f
                    effectiveMaxYRight =
                        if (commonValue == 0f) 1f else commonValue + kotlin.math.max(
                            abs(commonValue * 0.1f),
                            1f
                        )
                } else { // commonValue < 0f
                    effectiveMaxYRight = 0f
                    effectiveMinYRight = commonValue - kotlin.math.max(abs(commonValue * 0.1f), 1f)
                }
            } else { // Y_Right values are not all equal, apply standard 5% padding
                val paddingYRight = (dataMaxYRight - dataMinYRight) * 0.05f
                if (paddingYRight > 0.0001f) {
                    effectiveMinYRight -= paddingYRight
                    effectiveMaxYRight += paddingYRight
                } else {
                    effectiveMinYRight -= 1f
                    effectiveMaxYRight += 1f
                }
            }
            // Safety check: ensure min < max for right Y-axis
            if (effectiveMinYRight >= effectiveMaxYRight) {
                effectiveMaxYRight = effectiveMinYRight + 1f
            }
        }

        val rangeX =
            if (abs(effectiveMaxX - effectiveMinX) < 0.0001f) 1f else (effectiveMaxX - effectiveMinX)
        val rangeYLeft =
            if (!hasLeftYAxisData || abs(effectiveMaxYLeft - effectiveMinYLeft) < 0.0001f) 1f else (effectiveMaxYLeft - effectiveMinYLeft)
        val rangeYRight =
            if (!hasRightYAxisData || abs(effectiveMaxYRight - effectiveMinYRight) < 0.0001f) 1f else (effectiveMaxYRight - effectiveMinYRight)

        fun mapX(originalX: Float): Float {
            return graphLeft + ((originalX - effectiveMinX) / rangeX) * graphWidth
        }

        fun mapYLeft(originalY: Float): Float {
            return graphBottom - ((originalY - effectiveMinYLeft) / rangeYLeft) * graphHeight
        }

        fun mapYRight(originalY: Float): Float {
            return graphBottom - ((originalY - effectiveMinYRight) / rangeYRight) * graphHeight
        }

        val axisPaint = Paint().apply {
            color = secondaryTextColor
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawLine(
            graphLeft,
            graphBottom,
            graphLeft + graphWidth,
            graphBottom,
            axisPaint
        ) // X-axis
        if (hasLeftYAxisData) {
            canvas.drawLine(graphLeft, graphTop, graphLeft, graphBottom, axisPaint) // Left Y-axis
        }
        if (hasRightYAxisData) {
            canvas.drawLine(
                graphRight, // Use graphRight for clarity and consistency
                graphTop,
                graphRight,
                graphBottom,
                axisPaint
            ) // Right Y-axis
        }

        // Grid line paint
        val gridLinePaint = Paint().apply {
            color = if (isNightMode) Color.DKGRAY else Color.LTGRAY // Faint color
            strokeWidth = 1f
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            strokeWidth = 6f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val textPaint = Paint().apply {
            color = primaryTextColor
            textSize = 32f // Increased from 28f
            isAntiAlias = true
        }

        for (line in lines) {
            if (line.dataPoints.isEmpty()) continue

            linePaint.color = line.color
            val path = Path()
            val firstPoint = line.dataPoints.first()
            val mapY = if (line.yAxis == YAxis.LEFT) ::mapYLeft else ::mapYRight

            path.moveTo(mapX(firstPoint.x), mapY(firstPoint.y))
            if (line.drawCircles) {
                canvas.drawCircle(
                    mapX(firstPoint.x),
                    mapY(firstPoint.y),
                    8f,
                    linePaint.apply { style = Paint.Style.FILL })
            }
            linePaint.style = Paint.Style.STROKE

            for (i in 1 until line.dataPoints.size) {
                val point = line.dataPoints[i]
                path.lineTo(mapX(point.x), mapY(point.y))
                if (line.drawCircles) {
                    canvas.drawCircle(
                        mapX(point.x),
                        mapY(point.y),
                        8f,
                        linePaint.apply { style = Paint.Style.FILL })
                }
                linePaint.style = Paint.Style.STROKE
            }

            canvas.drawPath(path, linePaint)
        }

        // Draw Left Y-axis ticks and labels
        if (hasLeftYAxisData) {
            textPaint.textAlign = Align.RIGHT
            val numYTicks = if (gridWidth > 15) 2 else 1
            if (abs(dataMaxYLeft - dataMinYLeft) > 0.0001f) {
                for (i in 0..numYTicks) {
                    val value = dataMinYLeft + ((dataMaxYLeft - dataMinYLeft) / numYTicks) * i
                    val yPos = mapYLeft(value)
                    if (yPos >= graphTop - 5f && yPos <= graphBottom + 5f) {
                        canvas.drawLine(graphLeft - 5f, yPos, graphLeft + 5f, yPos, axisPaint)
                        // Draw faint horizontal grid line
                        canvas.drawLine(graphLeft, yPos, graphRight, yPos, gridLinePaint)
                        canvas.drawText(
                            String.format(java.util.Locale.getDefault(), "%.0f", value),
                            graphLeft - 15f,
                            yPos + (textPaint.textSize / 3),
                            textPaint
                        )
                    }
                }
            } else {
                val yPos = mapYLeft(dataMinYLeft)
                canvas.drawLine(graphLeft - 5f, yPos, graphLeft + 5f, yPos, axisPaint)
                // Draw faint horizontal grid line
                canvas.drawLine(graphLeft, yPos, graphRight, yPos, gridLinePaint)
                canvas.drawText(
                    String.format(java.util.Locale.getDefault(), "%.0f", dataMinYLeft),
                    graphLeft - 15f,
                    yPos + (textPaint.textSize / 3),
                    textPaint
                )
            }
        }

        // Draw Right Y-axis ticks and labels
        if (hasRightYAxisData) {
            textPaint.textAlign = Align.LEFT
            val numYTicks = if (gridWidth > 15) 2 else 1
            if (abs(dataMaxYRight - dataMinYRight) > 0.0001f) {
                for (i in 0..numYTicks) {
                    val value = dataMinYRight + ((dataMaxYRight - dataMinYRight) / numYTicks) * i
                    val yPos = mapYRight(value)
                    if (yPos >= graphTop - 5f && yPos <= graphBottom + 5f) {
                        canvas.drawLine(
                            graphRight - 5f,
                            yPos,
                            graphRight + 5f,
                            yPos,
                            axisPaint
                        )
                        // Draw faint horizontal grid line
                        canvas.drawLine(graphLeft, yPos, graphRight, yPos, gridLinePaint)
                        canvas.drawText(
                            String.format(java.util.Locale.getDefault(), "%.0f", value),
                            graphRight + 15f,
                            yPos + (textPaint.textSize / 3),
                            textPaint
                        )
                    }
                }
            } else {
                val yPos = mapYRight(dataMinYRight)
                canvas.drawLine(
                    graphRight - 5f,
                    yPos,
                    graphRight + 5f,
                    yPos,
                    axisPaint
                )
                // Draw faint horizontal grid line
                canvas.drawLine(graphLeft, yPos, graphRight, yPos, gridLinePaint)
                canvas.drawText(
                    String.format(java.util.Locale.getDefault(), "%.0f", dataMinYRight),
                    graphRight + 15f,
                    yPos + (textPaint.textSize / 3),
                    textPaint
                )
            }
        }

        // Draw Y zero line (solid, using axisPaint)
        // This is drawn after faint grid lines from Y-ticks, so it will be on top.
        // It will not be drawn if it coincides with the X-axis (graphBottom), as X-axis is already solid.
        val yZeroLinePaint = axisPaint // Use the same paint as other axes for consistency

        if (hasLeftYAxisData) {
            // If left Y-axis has data and its range includes 0
            if (effectiveMinYLeft <= 0f && effectiveMaxYLeft >= 0f) {
                val yZeroPos = mapYLeft(0f)
                // Draw if the zero position is within graph bounds (inclusive top)
                // and not effectively the same as the X-axis (graphBottom).
                if (yZeroPos in graphTop..graphBottom && abs(yZeroPos - graphBottom) > 0.1f) {
                    canvas.drawLine(graphLeft, yZeroPos, graphRight, yZeroPos, yZeroLinePaint)
                }
            }
        } else if (hasRightYAxisData) {
            // Else, if no left Y-axis data, but right Y-axis has data and its range includes 0
            if (effectiveMinYRight <= 0f && effectiveMaxYRight >= 0f) {
                val yZeroPos = mapYRight(0f)
                // Draw if the zero position is within graph bounds (inclusive top)
                // and not effectively the same as the X-axis (graphBottom).
                if (yZeroPos in graphTop..graphBottom && abs(yZeroPos - graphBottom) > 0.1f) {
                    canvas.drawLine(graphLeft, yZeroPos, graphRight, yZeroPos, yZeroLinePaint)
                }
            }
        }

        textPaint.textAlign = Align.CENTER
        val numXTicks = if (gridHeight > 15) 3 else 1
        if (abs(dataMaxX - dataMinX) > 0.0001f) {
            for (i in 0..numXTicks) {
                val value = dataMinX + ((dataMaxX - dataMinX) / numXTicks) * i
                val xPos = mapX(value)
                if (xPos >= graphLeft - 5f && xPos <= graphLeft + graphWidth + 5f) {
                    canvas.drawLine(xPos, graphBottom - 5f, xPos, graphBottom + 5f, axisPaint)
                    canvas.drawText(labelProvider(value), xPos, graphBottom + 30f, textPaint)
                }
            }
        } else {
            val xPos = mapX(dataMinX)
            canvas.drawLine(xPos, graphBottom - 5f, xPos, graphBottom + 5f, axisPaint)
            canvas.drawText(labelProvider(dataMinX), xPos, graphBottom + 30f, textPaint)
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
                maxLegendLabelWidth =
                    kotlin.math.max(maxLegendLabelWidth, legendPaint.measureText(item.label!!))
            }

            val legendContentActualLeft =
                (width - marginRight - legendPadding - legendColorBoxSize - legendTextMargin - maxLegendLabelWidth)
            val legendContentActualRight =
                (width - marginRight - legendPadding) // Right edge of the color box

            val legendContentActualTop = graphTop + legendPadding // Top edge of the first color box
            val legendContentActualBottom =
                legendContentActualTop + (legendItems.size - 1) * legendEntryHeight + legendColorBoxSize // Bottom edge of the last color box

            val legendBgPaint = Paint().apply {
                color = if (isNightMode) {
                    Color.argb(210, 0, 0, 0)
                } else {
                    Color.argb(210, 255, 255, 255)
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

        for (line in legendItems) {
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
                width - marginRight - legendPadding - legendColorBoxSize - legendTextMargin - legendPaint.measureText(
                    line.label
                ), // x: Align text to the left of the color box
                currentLegendY + legendColorBoxSize / 2 + legendTextSize / 3, // y: Vertically center text with color box
                legendPaint
            )
            currentLegendY += legendEntryHeight
        }

        return bitmap
    }
}
