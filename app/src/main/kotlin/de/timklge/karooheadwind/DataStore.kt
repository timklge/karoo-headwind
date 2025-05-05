package de.timklge.karooheadwind

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.weatherprovider.WeatherData
import de.timklge.karooheadwind.weatherprovider.WeatherDataForLocation
import de.timklge.karooheadwind.weatherprovider.WeatherDataResponse
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.minutes

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings", corruptionHandler = ReplaceFileCorruptionHandler {
    Log.w(KarooHeadwindExtension.TAG, "Error reading settings, using default values")
    emptyPreferences()
})

val jsonWithUnknownKeys = Json { ignoreUnknownKeys = true }

val settingsKey = stringPreferencesKey("settings")
val widgetSettingsKey = stringPreferencesKey("widgetSettings")
val currentDataKey = stringPreferencesKey("currentForecastsUnified")
val statsKey = stringPreferencesKey("stats")
val lastKnownPositionKey = stringPreferencesKey("lastKnownPosition")

suspend fun saveSettings(context: Context, settings: HeadwindSettings) {
    context.dataStore.edit { t ->
        t[settingsKey] = Json.encodeToString(settings)
    }
}

suspend fun saveWidgetSettings(context: Context, settings: HeadwindWidgetSettings) {
    context.dataStore.edit { t ->
        t[widgetSettingsKey] = Json.encodeToString(settings)
    }
}

suspend fun saveStats(context: Context, stats: HeadwindStats) {
    context.dataStore.edit { t ->
        t[statsKey] = Json.encodeToString(stats)
    }
}

suspend fun saveCurrentData(context: Context, response: WeatherDataResponse) {
    context.dataStore.edit { t ->
        t[currentDataKey] = Json.encodeToString(response)
    }
}

suspend fun saveLastKnownPosition(context: Context, gpsCoordinates: GpsCoordinates) {
    Log.i(KarooHeadwindExtension.TAG, "Saving last known position: $gpsCoordinates")

    try {
        context.dataStore.edit { t ->
            t[lastKnownPositionKey] = Json.encodeToString(gpsCoordinates)
        }
    } catch(e: Throwable){
        Log.e(KarooHeadwindExtension.TAG, "Failed to save last known position", e)
    }
}

fun Context.streamWidgetSettings(): Flow<HeadwindWidgetSettings> {
    return dataStore.data.map { settingsJson ->
        try {
            if (settingsJson.contains(widgetSettingsKey)){
                jsonWithUnknownKeys.decodeFromString<HeadwindWidgetSettings>(settingsJson[widgetSettingsKey]!!)
            } else {
                jsonWithUnknownKeys.decodeFromString<HeadwindWidgetSettings>(HeadwindWidgetSettings.defaultWidgetSettings)
            }
        } catch(e: Throwable){
            Log.e(KarooHeadwindExtension.TAG, "Failed to read widget preferences", e)
            jsonWithUnknownKeys.decodeFromString<HeadwindWidgetSettings>(HeadwindWidgetSettings.defaultWidgetSettings)
        }
    }.distinctUntilChanged()
}

fun Context.streamSettings(karooSystemService: KarooSystemService): Flow<HeadwindSettings> {
    return dataStore.data.map { settingsJson ->
        try {
            if (settingsJson.contains(settingsKey)){
                jsonWithUnknownKeys.decodeFromString<HeadwindSettings>(settingsJson[settingsKey]!!)
            } else {
                val defaultSettings = jsonWithUnknownKeys.decodeFromString<HeadwindSettings>(
                    HeadwindSettings.defaultSettings)

                defaultSettings.copy()
            }
        } catch(e: Throwable){
            Log.e(KarooHeadwindExtension.TAG, "Failed to read preferences", e)
            jsonWithUnknownKeys.decodeFromString<HeadwindSettings>(HeadwindSettings.defaultSettings)
        }
    }.distinctUntilChanged()
}

data class UpcomingRoute(val distanceAlongRoute: Double, val routePolyline: LineString, val routeLength: Double)

fun KarooSystemService.streamUpcomingRoute(): Flow<UpcomingRoute?> {
    val distanceToDestinationStream = streamDataFlow(DataType.Type.DISTANCE_TO_DESTINATION)
        .map { (it as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.DISTANCE_TO_DESTINATION) }
        .distinctUntilChanged()

    var lastKnownDistanceAlongRoute = 0.0
    var lastKnownRoutePolyline: LineString? = null

    val navigationStateStream = streamNavigationState()
        .map { it.state as? OnNavigationState.NavigationState.NavigatingRoute }
        .map { navigationState ->
            navigationState?.let { LineString.fromPolyline(it.routePolyline, 5) }
        }
        .distinctUntilChanged()
        .combine(distanceToDestinationStream) { routePolyline, distanceToDestination ->
            Log.d(KarooHeadwindExtension.TAG, "Route polyline size: ${routePolyline?.coordinates()?.size}, distance to destination: $distanceToDestination")
            if (routePolyline != null){
                val length = TurfMeasurement.length(routePolyline, TurfConstants.UNIT_METERS)
                if (routePolyline != lastKnownRoutePolyline){
                    lastKnownDistanceAlongRoute = 0.0
                }
                val distanceAlongRoute = distanceToDestination?.let { toDest -> length - toDest } ?: lastKnownDistanceAlongRoute
                lastKnownDistanceAlongRoute = distanceAlongRoute
                lastKnownRoutePolyline = routePolyline

                UpcomingRoute(distanceAlongRoute, routePolyline, length)
            } else {
                null
            }
        }

    return navigationStateStream
}

fun Context.streamStats(): Flow<HeadwindStats> {
    return dataStore.data.map { statsJson ->
        try {
            jsonWithUnknownKeys.decodeFromString<HeadwindStats>(
                statsJson[statsKey] ?: HeadwindStats.defaultStats
            )
        } catch(e: Throwable){
            Log.e(KarooHeadwindExtension.TAG, "Failed to read stats", e)
            jsonWithUnknownKeys.decodeFromString<HeadwindStats>(HeadwindStats.defaultStats)
        }
    }.distinctUntilChanged()
}

suspend fun Context.getLastKnownPosition(): GpsCoordinates? {
    val settingsJson = dataStore.data.first()

    try {
        val lastKnownPositionString = settingsJson[lastKnownPositionKey] ?: return null
        val lastKnownPosition = jsonWithUnknownKeys.decodeFromString<GpsCoordinates>(
            lastKnownPositionString
        )

        return lastKnownPosition
    } catch(e: Throwable){
        Log.e(KarooHeadwindExtension.TAG, "Failed to read last known position", e)
        return null
    }
}

fun KarooSystemService.streamUserProfile(): Flow<UserProfile> {
    return callbackFlow {
        val listenerId = addConsumer { userProfile: UserProfile ->
            trySendBlocking(userProfile)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

fun Context.streamCurrentForecastWeatherData(): Flow<WeatherDataResponse?> {
    return dataStore.data.map { settingsJson ->
        try {
            val data = settingsJson[currentDataKey]

            data?.let { d -> jsonWithUnknownKeys.decodeFromString<WeatherDataResponse>(d) }
        } catch (e: Throwable) {
            Log.e(KarooHeadwindExtension.TAG, "Failed to read weather data", e)
            null
        }
    }.distinctUntilChanged()
}

fun lerpNullable(
    start: Double?,
    end: Double?,
    factor: Double
): Double? {
    if (start == null && end == null) return null
    if (start == null) return end
    if (end == null) return start

    return start + (end - start) * factor
}

/**
 * Linearly interpolates between two angles in degrees
 * 
 * @param start Starting angle in degrees [0-360]
 * @param end Ending angle in degrees [0-360]
 * @param factor Interpolation factor [0-1]
 * @return Interpolated angle in degrees [0-360]
 */
fun lerpAngle(
    start: Double,
    end: Double,
    factor: Double
): Double {
    val normalizedStart = start % 360.0
    val normalizedEnd = end % 360.0
    
    var diff = normalizedEnd - normalizedStart
    if (diff > 180.0) {
        diff -= 360.0
    } else if (diff < -180.0) {
        diff += 360.0
    }
    
    var result = normalizedStart + diff * factor
    
    if (result < 0) {
        result += 360.0
    } else if (result >= 360.0) {
        result -= 360.0
    }
    
    return result
}

fun lerpWeather(
    start: WeatherData,
    end: WeatherData,
    factor: Double
): WeatherData {
    val closestWeatherData = if (factor < 0.5) start else end

    return WeatherData(
        time = (start.time + (end.time - start.time) * factor).toLong(),
        temperature = start.temperature + (end.temperature - start.temperature) * factor,
        relativeHumidity = lerpNullable(start.relativeHumidity, end.relativeHumidity, factor),
        precipitation = start.precipitation + (end.precipitation - start.precipitation) * factor,
        precipitationProbability = lerpNullable(start.precipitationProbability, end.precipitationProbability, factor),
        cloudCover = lerpNullable(start.cloudCover, end.cloudCover, factor),
        surfacePressure = lerpNullable(start.surfacePressure, end.surfacePressure, factor),
        sealevelPressure = lerpNullable(start.sealevelPressure, end.sealevelPressure, factor),
        windSpeed = start.windSpeed + (end.windSpeed - start.windSpeed) * factor,
        windDirection = lerpAngle(start.windDirection, end.windDirection, factor),
        windGusts = start.windGusts + (end.windGusts - start.windGusts) * factor,
        weatherCode = closestWeatherData.weatherCode,
        isForecast = closestWeatherData.isForecast,
        isNight = closestWeatherData.isNight,
    )
}

fun lerpWeatherTime(
    weatherData: List<WeatherData>?,
    currentWeatherData: WeatherData
): WeatherData {
    val now = System.currentTimeMillis()
    val nextWeatherForecastData = weatherData?.find { forecast -> forecast.time * 1000 >= now }
    val previousWeatherForecastData = weatherData?.findLast { forecast -> forecast.time * 1000 < now }

    val interpolateStartWeatherData = previousWeatherForecastData ?: currentWeatherData
    val interpolateEndWeatherData = nextWeatherForecastData ?: interpolateStartWeatherData

    val lerpFactor = ((now - (interpolateStartWeatherData.time * 1000)).toDouble() / (interpolateEndWeatherData.time * 1000 - (interpolateStartWeatherData.time * 1000)).absoluteValue).coerceIn(0.0, 1.0)

    return lerpWeather(
        start = interpolateStartWeatherData,
        end = interpolateEndWeatherData,
        factor = lerpFactor
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
fun Context.streamCurrentWeatherData(karooSystemService: KarooSystemService): Flow<WeatherData?> {
    val locationFlow = flow {
        emit(null)
        emitAll(karooSystemService.getGpsCoordinateFlow(this@streamCurrentWeatherData))
    }

    return dataStore.data.map { settingsJson ->
        try {
            val data = settingsJson[currentDataKey]
            data?.let { d -> jsonWithUnknownKeys.decodeFromString<WeatherDataResponse>(d) }
        } catch (e: Throwable) {
            Log.e(KarooHeadwindExtension.TAG, "Failed to read weather data", e)
            null
        }
    }.combine(locationFlow) {
        weatherData, location -> weatherData to location
    }.distinctUntilChanged()
    .flatMapLatest { (weatherData, location) ->
        flow {
            if (!weatherData?.data.isNullOrEmpty()) {
                while(true){
                    // Get weather for closest position
                    val weatherDataForCurrentPosition = if (location == null || weatherData?.data?.size == 1) weatherData?.data?.first() else {
                        val weatherDatas = weatherData?.data?.sortedBy { data ->
                            TurfMeasurement.distance(
                                Point.fromLngLat(location.lon, location.lat),
                                Point.fromLngLat(data.coords.lon, data.coords.lat),
                                TurfConstants.UNIT_METERS
                            )
                        }!!.take(2)

                        val location1 = weatherDatas[0]
                        val location2 = weatherDatas[1]
                        val distanceToLocation1 = TurfMeasurement.distance(
                            Point.fromLngLat(location.lon, location.lat),
                            Point.fromLngLat(location1.coords.lon, location1.coords.lat),
                            TurfConstants.UNIT_METERS
                        )
                        val distanceToLocation2 = TurfMeasurement.distance(
                            Point.fromLngLat(location.lon, location.lat),
                            Point.fromLngLat(location2.coords.lon, location2.coords.lat),
                            TurfConstants.UNIT_METERS
                        )
                        val lerpFactor = (distanceToLocation1 / (distanceToLocation1 + distanceToLocation2)).coerceIn(0.0, 1.0)

                        val interpolatedWeatherData = lerpWeather(
                            start = location1.current,
                            end = location2.current,
                            factor = lerpFactor
                        )

                        val interpolatedForecasts = location1.forecasts?.mapIndexed { index, forecast ->
                            val forecast2 = location2.forecasts?.get(index) ?: error("Mismatched forecast lengths")
                            val interpolatedForecast = lerpWeather(
                                start = forecast,
                                end = forecast2,
                                factor = lerpFactor
                            )
                            interpolatedForecast
                        }

                        WeatherDataForLocation(
                            coords = location,
                            current = interpolatedWeatherData,
                            forecasts = interpolatedForecasts
                        )
                    }

                    if (weatherDataForCurrentPosition != null) {
                        emit(lerpWeatherTime(
                            weatherDataForCurrentPosition.forecasts,
                            weatherDataForCurrentPosition.current
                        ))
                    } else {
                        emit(null)
                    }

                    delay(1.minutes)
                }
            } else {
                emit(null)
            }
        }
    }
}
