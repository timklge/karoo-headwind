package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import de.timklge.karooheadwind.HeadingResponse
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.MainActivity
import de.timklge.karooheadwind.getHeadingFlow
import de.timklge.karooheadwind.screens.isNightMode
import de.timklge.karooheadwind.streamDatatypeIsVisible
import de.timklge.karooheadwind.throttle
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class CompassDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("karoo-headwind", "compass") {
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private val glance = GlanceRemoteViews()

    data class StreamData(val bearing: Float, val isVisible: Boolean)

    private fun drawCompassNeedle(bitmap: Bitmap, baseColor: Int) {
        val canvas = Canvas(bitmap)
        val centerX = bitmap.width / 2f
        val centerY = bitmap.height / 2f

        // Draw black needle pointing south (downward)
        val blackPaint = Paint().apply {
            color = baseColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val southNeedle = Path().apply {
            moveTo(centerX, centerY * 1.9f)  // Bottom point
            lineTo(centerX - 12f, centerY)    // Left base
            lineTo(centerX + 12f, centerY)    // Right base
            close()
        }
        canvas.drawPath(southNeedle, blackPaint)

        // Draw red needle pointing north (upward) SECOND so it's on top
        val redPaint = Paint().apply {
            color = android.graphics.Color.RED
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val northNeedle = Path().apply {
            moveTo(centerX, centerY * 0.1f)  // Top point
            lineTo(centerX - 12f, centerY)    // Left base
            lineTo(centerX + 12f, centerY)    // Right base
            close()
        }
        canvas.drawPath(northNeedle, redPaint)

        // Draw text labels
        val textPaint = Paint().apply {
            color = baseColor
            textSize = 24f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }

        // Draw "N" at the top
        // canvas.drawText("N", centerX, centerY * 0.3f, textPaint)

        // Draw "S" at the bottom
        // canvas.drawText("S", centerX, centerY * 1.7f, textPaint)

        // Draw center circle
        val centerPaint = Paint().apply {
            color = android.graphics.Color.DKGRAY
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(centerX, centerY, 8f, centerPaint)
    }

    private fun previewFlow(): Flow<StreamData> {
        return flow {
            while (true) {
                emit(StreamData(isVisible = true, bearing = 360f * Math.random().toFloat()))
                delay(5_000)
            }
        }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(KarooHeadwindExtension.TAG, "Starting compass view with $emitter")

        val baseModifier = GlanceModifier.fillMaxSize().padding(2.dp).background(Color.White, Color.Black).cornerRadius(10.dp)

        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            awaitCancellation()
        }

        val isNight = isNightMode(context)

        val baseBitmap = createBitmap(128, 128).also {
            drawCompassNeedle(it, if (isNight) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
        }

        val flow = if (config.preview) {
            previewFlow()
        } else {
            combine(karooSystem.getHeadingFlow(context), karooSystem.streamDatatypeIsVisible(dataTypeId)){ bearingResponse, isVisible ->
                val bearing = when(bearingResponse) {
                    is HeadingResponse.Value -> ((bearingResponse.diff + 360) % 360).toFloat()
                    else -> null
                }

                StreamData(bearing = bearing ?: 0f, isVisible = isVisible)
            }
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(ShowCustomStreamState("", null))

            val refreshRate = karooSystem.getRefreshRateInMilliseconds(context)
            flow.filter { it.isVisible }.throttle(refreshRate).collect { streamData ->
                val result = glance.compose(context, androidx.compose.ui.unit.DpSize.Unspecified) {
                    Box(
                        modifier = if (!config.preview) baseModifier.clickable(actionStartActivity<MainActivity>()) else baseModifier,
                    ) {
                        Image(
                            modifier = GlanceModifier.fillMaxSize(),
                            provider = ImageProvider(getArrowBitmapByBearing(baseBitmap, 0-streamData.bearing.toInt())),
                            contentDescription = "Relative wind direction indicator",
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                emitter.updateView(result.remoteViews)
            }
        }

        emitter.setCancellable {
            Log.d(KarooHeadwindExtension.TAG, "Stopping compass view with $emitter")
            configJob.cancel()
            viewJob.cancel()
        }
    }
}