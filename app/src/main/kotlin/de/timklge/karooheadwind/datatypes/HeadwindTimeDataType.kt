package de.timklge.karooheadwind.datatypes

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HeadwindTimeDataType(
    private val karooSystemService: KarooSystemService,
    private val context: Context,
    private val windAggregator: WindAggregator
) : DataTypeImpl("karoo-headwind", "headwindTime") {

    companion object {
        const val HEADWIND_THRESHOLD_MS = 1.0
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val refreshRate = karooSystemService.getRefreshRateInMilliseconds(context)

            while (isActive) {
                val headwindTime = windAggregator.getRideTimeInHeadwind()
                emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to headwindTime.toMillis().toDouble()))))

                delay(refreshRate)
            }
        }

        emitter.setCancellable {
            job.cancel()
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(formatDataTypeId = DataType.Type.RIDE_TIME))
    }
}
