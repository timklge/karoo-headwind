package de.timklge.karooheadwind.datatypes

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import de.timklge.karooheadwind.screens.LineGraphBuilder
import de.timklge.karooheadwind.streamUserProfile
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

class HeadwindTimeDistributionDataType(
    private val karooSystem: KarooSystemService,
    private val context: Context,
    private val windAggregator: WindAggregator
) : DataTypeImpl("karoo-headwind", "headwindTimeDistribution") {

    private fun previewDistributions(): Pair<Map<Int, Int>, Map<Int, Int>> {
        val speedMean = Random.nextInt(-20, 20)
        val gustMean = speedMean + Random.nextInt(0, 15)

        fun peak(mean: Int): Map<Int, Int> = (-60..60).mapNotNull { bucket ->
            val count = (100 - abs(bucket - mean) * 3).coerceAtLeast(0)
            if (count > 0) bucket to count else null
        }.toMap()

        return peak(speedMean) to peak(gustMean)
    }

    private fun buildLines(
        headwindSpeedDistribution: Map<Int, Int>,
        headwindGustDistribution: Map<Int, Int>,
        isImperial: Boolean
    ): Set<LineGraphBuilder.Line> {
        val allBuckets = headwindSpeedDistribution.keys + headwindGustDistribution.keys
        if (allBuckets.isEmpty()) return emptySet()

        val minBucket = allBuckets.min()
        val maxBucket = allBuckets.max()

        // Time spent per bucket in minutes, including empty buckets so lines return to zero between peaks
        fun toDataPoints(distribution: Map<Int, Int>): List<LineGraphBuilder.DataPoint> {
            return (minBucket..maxBucket).map { bucket ->
                val speedInMs = bucket * WindAggregator.BUCKET_SIZE
                val speed = if (isImperial) speedInMs * 2.23694 else speedInMs * 3.6
                val minutes = (distribution[bucket] ?: 0) * WindAggregator.SAMPLE_INTERVAL_MS / 60_000f

                LineGraphBuilder.DataPoint(x = speed.toFloat(), y = minutes)
            }
        }

        return buildSet {
            add(LineGraphBuilder.Line(
                dataPoints = toDataPoints(headwindSpeedDistribution),
                color = 0xFF2196F3.toInt(), // Blue
                label = "Headwind",
                drawCircles = false
            ))
            add(LineGraphBuilder.Line(
                dataPoints = toDataPoints(headwindGustDistribution),
                color = 0xFFFF9800.toInt(), // Orange
                label = "Gusts",
                drawCircles = false
            ))
        }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            awaitCancellation()
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(ShowCustomStreamState("", null))

            val glance = GlanceRemoteViews()
            val refreshRate = karooSystem.getRefreshRateInMilliseconds(context)
            val isImperial = karooSystem.streamUserProfile().first().preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
            var isShowingMessage = false

            while (isActive) {
                val (headwindSpeedDistribution, headwindGustDistribution) = if (config.preview) {
                    previewDistributions()
                } else {
                    windAggregator.getHeadwindSpeedDistribution() to windAggregator.getHeadwindGustDistribution()
                }

                val lines = buildLines(headwindSpeedDistribution, headwindGustDistribution, isImperial)

                if (lines.isEmpty()) {
                    emitter.onNext(ShowCustomStreamState("No wind data", null))
                    isShowingMessage = true
                } else {
                    if (isShowingMessage) {
                        emitter.onNext(ShowCustomStreamState("", null))
                        isShowingMessage = false
                    }

                    val bitmap = LineGraphBuilder(context).drawLineGraph(
                        config.viewSize.first,
                        config.viewSize.second,
                        config.gridSize.first,
                        config.gridSize.second,
                        lines,
                        leftYLabelProvider = { minutes -> if (minutes < 10f) "%.1f".format(minutes) else minutes.roundToInt().toString() }
                    ) { x -> x.roundToInt().toString() }

                    val result = glance.compose(context, DpSize.Unspecified) {
                        Box(modifier = GlanceModifier.fillMaxSize()) {
                            Image(
                                ImageProvider(bitmap),
                                "Headwind Time Distribution",
                                modifier = GlanceModifier.fillMaxSize()
                            )
                        }
                    }

                    emitter.updateView(result.remoteViews)
                }

                delay(refreshRate)
            }
        }

        emitter.setCancellable {
            configJob.cancel()
            viewJob.cancel()
        }
    }
}
