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
import de.timklge.karooheadwind.WindUnit
import de.timklge.karooheadwind.screens.LineGraphBuilder
import de.timklge.karooheadwind.streamDatatypeIsVisible
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.streamUserProfile
import de.timklge.karooheadwind.util.msInWindUnit
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
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
        val speedSigma = Random.nextDouble(5.0, 9.0)
        val gustSigma = speedSigma * Random.nextDouble(1.1, 1.4)
        val speedAmplitude = Random.nextDouble(60.0, 120.0)
        val gustAmplitude = speedAmplitude * Random.nextDouble(1.1, 2.0)

        fun bell(mean: Int, sigma: Double, amplitude: Double): Map<Int, Int> = (-60..60).mapNotNull { bucket ->
            val z = (bucket - mean) / sigma
            val base = amplitude * exp(-0.5 * z * z)
            val count = (base * Random.nextDouble(0.85, 1.15)).roundToInt()
            if (count > 0) bucket to count else null
        }.toMap()

        return bell(speedMean, speedSigma, speedAmplitude) to bell(gustMean, gustSigma, gustAmplitude)
    }

    private fun buildLines(
        headwindSpeedDistribution: Map<Int, Int>,
        headwindGustDistribution: Map<Int, Int>,
        windUnit: WindUnit
    ): Set<LineGraphBuilder.Line> {
        val allBuckets = headwindSpeedDistribution.keys + headwindGustDistribution.keys
        if (allBuckets.isEmpty()) return emptySet()

        // Symmetric range around 0 so that 0 is always centred on the x axis
        val bound = allBuckets.maxOf { abs(it) }

        // Time spent per bucket in minutes, including empty buckets so lines return to zero between peaks
        fun toDataPoints(distribution: Map<Int, Int>): List<LineGraphBuilder.DataPoint> {
            return (-bound..bound).map { bucket ->
                val speedInMs = bucket * WindAggregator.BUCKET_SIZE
                val speed = msInWindUnit(speedInMs, windUnit)
                val minutes = (distribution[bucket] ?: 0) * WindAggregator.SAMPLE_INTERVAL_MS / 60_000f

                LineGraphBuilder.DataPoint(x = speed.toFloat(), y = minutes)
            }
        }

        return buildSet {
            add(LineGraphBuilder.Line(
                dataPoints = toDataPoints(headwindGustDistribution),
                color = 0xFFFF9800.toInt(), // Orange
                label = "Gusts",
                drawCircles = false
            ))
            add(LineGraphBuilder.Line(
                dataPoints = toDataPoints(headwindSpeedDistribution),
                color = 0xFF2196F3.toInt(), // Blue
                label = "Headwind",
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

            val ticks = flow {
                while (true) {
                    emit(Unit)
                    delay(refreshRate)
                }
            }

            val updates = if (config.preview) {
                ticks
            } else {
                karooSystem.streamDatatypeIsVisible(dataTypeId)
                    .distinctUntilChanged()
                    .flatMapLatest { isVisible -> if (isVisible) ticks else emptyFlow() }
            }

            updates.collect {
                val (headwindSpeedDistribution, headwindGustDistribution) = if (config.preview) {
                    previewDistributions()
                } else {
                    windAggregator.getHeadwindSpeedDistribution() to windAggregator.getHeadwindGustDistribution()
                }

                val windUnit = context.streamSettings(karooSystem).first().getWindUnit(isImperial)
                val lines = buildLines(headwindSpeedDistribution, headwindGustDistribution, windUnit)

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
            }
        }

        emitter.setCancellable {
            configJob.cancel()
            viewJob.cancel()
        }
    }
}
