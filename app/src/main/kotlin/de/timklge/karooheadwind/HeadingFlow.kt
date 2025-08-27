package de.timklge.karooheadwind

import android.content.Context
import android.util.Log
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.util.signedAngleDifference
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

sealed class HeadingResponse {
    data object NoGps: HeadingResponse()
    data object NoWeatherData: HeadingResponse()
    data class Value(val diff: Double): HeadingResponse()
}

fun KarooSystemService.getRelativeHeadingFlow(context: Context): Flow<HeadingResponse> {
    val currentWeatherData = context.streamCurrentWeatherData(this)

    return getHeadingFlow(context)
        .combine(currentWeatherData) { bearing, data -> bearing to data }
        .map { (bearing, data) ->
            when {
                bearing is HeadingResponse.Value && data != null -> {
                    val windBearing = data.windDirection + 180
                    val diff = signedAngleDifference(bearing.diff, windBearing)

                    Log.d(KarooHeadwindExtension.TAG, "Wind bearing: Heading $bearing vs wind $windBearing => $diff")

                    HeadingResponse.Value(diff)
                }
                bearing is HeadingResponse.NoGps -> HeadingResponse.NoGps
                bearing is HeadingResponse.NoWeatherData || data == null -> HeadingResponse.NoWeatherData
                else -> bearing
            }
        }
}

fun KarooSystemService.getHeadingFlow(context: Context): Flow<HeadingResponse> {
    // return flowOf(HeadingResponse.Value(20.0))

    return getGpsCoordinateFlow(context)
        .map { coords ->
            val heading = coords?.bearing
            Log.d(KarooHeadwindExtension.TAG, "Updated gps bearing: $heading")
            val headingValue = heading?.let { HeadingResponse.Value(it) }

            headingValue ?: HeadingResponse.NoGps
        }
        .distinctUntilChanged()
}

fun <T> concatenate(vararg flows: Flow<T>) = flow {
    for (flow in flows) {
        emitAll(flow)
    }
}

fun<T> Flow<T>.dropNullsIfNullEncountered(): Flow<T?> = flow {
    var hadValue = false

    collect { value ->
        if (!hadValue) {
            emit(value)
            if (value != null) hadValue = true
        } else {
            if (value != null) emit(value)
        }
    }
}

suspend fun KarooSystemService.updateLastKnownGps(context: Context) {
    while (true) {
        getGpsCoordinateFlow(context)
            .filterNotNull()
            .throttle(60 * 1_000) // Only update last known gps position once every minute
            .collect { gps ->
                saveLastKnownPosition(context, gps)
            }
        delay(1_000)
    }
}

fun KarooSystemService.getGpsCoordinateFlow(context: Context): Flow<GpsCoordinates?> {
    /* return flow {
        // emit(GpsCoordinates(52.5164069,13.3784))
        emit(GpsCoordinates(32.46,-111.524))
        awaitCancellation()
    } */

    val initialFlow = flow {
        val lastKnownPosition = context.getLastKnownPosition()

        if (lastKnownPosition == null) {
            val initialState = streamDataFlow(DataType.Type.LOCATION).firstOrNull()?.let { it as? StreamState.Streaming }

            initialState?.dataPoint?.let { dataPoint ->
                val lat = dataPoint.values[DataType.Field.LOC_LATITUDE]
                val lng = dataPoint.values[DataType.Field.LOC_LONGITUDE]
                val orientation = dataPoint.values[DataType.Field.LOC_BEARING]
                val accuracy = dataPoint.values[DataType.Field.LOC_ACCURACY]

                if (lat != null && lng != null && accuracy != null && accuracy < 500) {
                    emit(GpsCoordinates(lat, lng, orientation))

                    Log.i(KarooHeadwindExtension.TAG, "No last known position found, fetched initial GPS position")
                } else {
                    emit(null)

                    Log.w(KarooHeadwindExtension.TAG, "No last known position found, initial GPS position is unavailable")
                }
            } ?: run {
                emit(null)

                Log.w(KarooHeadwindExtension.TAG, "No last known position found, initial GPS position is unavailable")
            }
        } else {
            emit(lastKnownPosition)

            Log.i(KarooHeadwindExtension.TAG, "Using last known position: $lastKnownPosition")
        }
    }

    val gpsFlow = streamDataFlow(DataType.Type.LOCATION).mapNotNull { it as? StreamState.Streaming }
        .mapNotNull { dataPoint ->
            val lat = dataPoint.dataPoint.values[DataType.Field.LOC_LATITUDE]
            val lng = dataPoint.dataPoint.values[DataType.Field.LOC_LONGITUDE]
            val orientation = dataPoint.dataPoint.values[DataType.Field.LOC_BEARING]
            val accuracy = dataPoint.dataPoint.values[DataType.Field.LOC_ACCURACY]

            Log.i(KarooHeadwindExtension.TAG, "Received GPS update: lat=$lat, lng=$lng, accuracy=$accuracy, orientation=$orientation")

            if (lat != null && lng != null && accuracy != null && accuracy < 500) {
                GpsCoordinates(lat, lng, orientation)
            } else {
                null
            }
        }

    val concatenatedFlow = concatenate(initialFlow, gpsFlow)

    return concatenatedFlow
        .combine(context.streamSettings(this)) { gps, settings -> gps to settings }
        .map { (gps, settings) ->
            gps?.round(settings.roundLocationTo.km.toDouble())
        }
        .dropNullsIfNullEncountered()
}
