package de.timklge.karooheadwind.datatypes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import de.timklge.karooheadwind.MainActivity
import kotlin.math.roundToInt

fun getArrowBitmapByBearing(baseBitmap: Bitmap, bearing: Int): Bitmap {
    val bearingRounded = (((bearing + 360) / 10.0).roundToInt() * 10) % 360

    val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    canvas.save()
    canvas.scale((bitmap.width / baseBitmap.width.toFloat()), (bitmap.height / baseBitmap.height.toFloat()), (bitmap.width / 2).toFloat(), (bitmap.height / 2).toFloat())
    canvas.rotate(bearingRounded.toFloat(), (bitmap.width / 2).toFloat(), (bitmap.height / 2).toFloat())
    canvas.drawBitmap(baseBitmap, ((bitmap.width - baseBitmap.width) / 2).toFloat(), ((bitmap.height - baseBitmap.height) / 2).toFloat(), paint)
    canvas.restore()

    return bitmap
}

@Composable
fun HeadwindDirection(
    baseBitmap: Bitmap, bearing: Int, fontSize: Int,
    overlayText: String, overlaySubText: String? = null,
    nightColor: Color = Color.Black, dayColor: Color = Color.White, preview: Boolean = false,
    wideMode: Boolean
) {
    val baseModifier = GlanceModifier.fillMaxSize().padding(5.dp).background(dayColor, nightColor).cornerRadius(10.dp)

    Box(
        modifier = if (!preview) baseModifier.clickable(actionStartActivity<MainActivity>()) else baseModifier,
        contentAlignment = Alignment(
            vertical = Alignment.Vertical.CenterVertically,
            horizontal = Alignment.Horizontal.CenterHorizontally,
        ),
    ) {
        if (overlayText.isNotEmpty()){
            if (overlaySubText == null){
                Image(
                    modifier = GlanceModifier.fillMaxSize(),
                    provider = ImageProvider(getArrowBitmapByBearing(baseBitmap, bearing)),
                    contentDescription = "Relative wind direction indicator",
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(ColorProvider(Color.Black, Color.White))
                )

                Text(
                    overlayText,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontSize = (0.6 * fontSize).sp, fontFamily = FontFamily.Monospace),
                    modifier = GlanceModifier.padding(1.dp)
                )
            } else {
                if (wideMode){
                    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = GlanceModifier.size(50.dp)) {
                                Image(
                                    provider = ImageProvider(getArrowBitmapByBearing(baseBitmap, bearing)),
                                    contentDescription = "Relative wind direction indicator",
                                    contentScale = ContentScale.Fit,
                                    colorFilter = ColorFilter.tint(ColorProvider(Color.Black, Color.White))
                                )
                            }

                            Text(
                                overlaySubText,
                                maxLines = 1,
                                style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontSize = (0.35 * fontSize).sp, fontFamily = FontFamily.Monospace),
                                modifier = GlanceModifier.padding(1.dp)
                            )
                        }

                        // Spacer(modifier = GlanceModifier.width(10.dp))

                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                overlayText,
                                maxLines = 1,
                                modifier = GlanceModifier.padding(5.dp),
                                style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontSize = (0.75 * fontSize).sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                } else {
                    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = GlanceModifier.size(40.dp)) {
                                Image(
                                    provider = ImageProvider(getArrowBitmapByBearing(baseBitmap, bearing)),
                                    contentDescription = "Relative wind direction indicator",
                                    contentScale = ContentScale.Fit,
                                    colorFilter = ColorFilter.tint(ColorProvider(Color.Black, Color.White))
                                )
                            }
                        }

                        Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                            Text(
                                overlayText,
                                maxLines = 1,
                                style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontSize = (0.65 * fontSize).sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                modifier = GlanceModifier.padding(1.dp)
                            )

                            Row(){
                                Text(
                                    overlaySubText,
                                    maxLines = 1,
                                    style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontSize = (0.4 * fontSize).sp, fontFamily = FontFamily.Monospace),
                                    modifier = GlanceModifier.padding(1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}