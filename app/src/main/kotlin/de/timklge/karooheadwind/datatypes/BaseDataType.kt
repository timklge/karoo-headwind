package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.util.Log
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamUserProfile
import de.timklge.karooheadwind.throttle
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

abstract class BaseDataType(
    private val karooSystemService: KarooSystemService,
    private val applicationContext: Context,
    dataTypeId: String
) : DataTypeImpl("karoo-headwind", dataTypeId) {
    abstract fun getValue(data: WeatherData, userProfile: UserProfile): Double?

    open fun getFormatDataType(): String? = null

    override fun startStream(emitter: Emitter<StreamState>) {
        Log.d(KarooHeadwindExtension.TAG, "start $dataTypeId stream")
        val job = CoroutineScope(Dispatchers.IO).launch {
            data class StreamData(val weatherData: WeatherData, val userProfile: UserProfile)

            val currentWeatherData = combine(applicationContext.streamCurrentWeatherData(karooSystemService).filterNotNull(), karooSystemService.streamUserProfile()) { weatherData, userProfile ->
                StreamData(weatherData, userProfile)
            }

            val refreshRate = karooSystemService.getRefreshRateInMilliseconds(applicationContext)

            currentWeatherData.filterNotNull()
                .throttle(refreshRate)
                .collect { (data, userProfile) ->
                    val value = getValue(data, userProfile)
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

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(KarooHeadwindExtension.TAG, "Starting $dataTypeId view with $emitter")

        if (getFormatDataType() != null){
            emitter.onNext(UpdateGraphicConfig(formatDataTypeId = getFormatDataType()))
        }
    }
}
