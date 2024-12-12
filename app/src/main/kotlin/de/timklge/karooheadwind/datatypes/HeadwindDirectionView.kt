package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.background
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.timklge.karooheadwind.KarooHeadwindExtension
import kotlin.math.roundToInt


data class BitmapWithBearing(val bitmap: Bitmap, val bearing: Int)

val bitmapsByBearing = mutableMapOf<BitmapWithBearing, Bitmap>()


fun getArrowBitmapByBearing(baseBitmap: Bitmap, bearing: Int): Bitmap {
    synchronized(bitmapsByBearing) {
        val bearingRounded = (((bearing + 360) / 10.0).roundToInt() * 10) % 360

        val bitmapWithBearing = BitmapWithBearing(baseBitmap, bearingRounded)
        val storedBitmap = bitmapsByBearing[bitmapWithBearing]
        if (storedBitmap != null) return storedBitmap

        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
//            strokeWidth = 15f
            isAntiAlias = true
        }

        canvas.save()
        canvas.scale((bitmap.width / baseBitmap.width.toFloat()), (bitmap.height / baseBitmap.height.toFloat()), (bitmap.width / 2).toFloat(), (bitmap.height / 2).toFloat())
        Log.d(KarooHeadwindExtension.TAG, "Drawing arrow at $bearingRounded")
        canvas.rotate(bearing.toFloat(), (bitmap.width / 2).toFloat(), (bitmap.height / 2).toFloat())
        canvas.drawBitmap(baseBitmap, ((bitmap.width - baseBitmap.width) / 2).toFloat(), ((bitmap.height - baseBitmap.height) / 2).toFloat(), paint)
        canvas.restore()

        bitmapsByBearing[bitmapWithBearing] = bitmap

        return bitmap
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 150)
@Composable
fun HeadwindDirection(baseBitmap: Bitmap, bearing: Int, fontSize: Int, overlayText: String) {
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(5.dp),
        contentAlignment = Alignment(
            vertical = Alignment.Vertical.CenterVertically,
            horizontal = Alignment.Horizontal.CenterHorizontally,
        ),
    ) {
        Image(
            modifier = GlanceModifier.fillMaxSize(),
            provider = ImageProvider(getArrowBitmapByBearing(baseBitmap, bearing)),
            contentDescription = "Relative wind direction indicator",
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(ColorProvider(Color.Black, Color.White))
        )

        Text(
            overlayText,
            style = TextStyle(ColorProvider(Color.Black, Color.White), fontSize = (0.5 * fontSize).sp, fontFamily = FontFamily.Monospace),
            modifier = GlanceModifier.background(Color(1f, 1f, 1f, 0.5f), Color(0f, 0f, 0f, 0.5f)).padding(2.dp)
        )
    }
}