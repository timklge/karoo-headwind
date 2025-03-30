package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.util.Log
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.weatherprovider.WeatherData
import de.timklge.karooheadwind.streamCurrentWeatherData
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

abstract class BaseDataType(
    private val karooSystemService: KarooSystemService,
    private val applicationContext: Context,
    dataTypeId: String
) : DataTypeImpl("karoo-headwind", dataTypeId) {
    abstract fun getValue(data: WeatherData): Double?

    override fun startStream(emitter: Emitter<StreamState>) {
        Log.d(KarooHeadwindExtension.TAG, "start $dataTypeId stream")
        val job = CoroutineScope(Dispatchers.IO).launch {
            val currentWeatherData = applicationContext.streamCurrentWeatherData(karooSystemService)

            currentWeatherData
                .filterNotNull()
                .collect { data ->
                    val value = getValue(data)
                    Log.d(KarooHeadwindExtension.TAG, "$dataTypeId: $value")

                    if (value != null) {
                        emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to value))))
                    } else {
                        emitter.onNext(StreamState.NotAvailable)
                    }
            }
        }
        emitter.setCancellable {
            Log.d(KarooHeadwindExtension.TAG, "stop $dataTypeId stream")
            job.cancel()
        }
    }
}
