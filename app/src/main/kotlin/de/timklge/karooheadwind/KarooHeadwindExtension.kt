package de.timklge.karooheadwind

import android.util.Log
import androidx.compose.ui.util.fastZip
import com.mapbox.geojson.LineString
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import de.timklge.karooheadwind.datatypes.CloudCoverDataType
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.datatypes.GraphicalForecastDataType
import de.timklge.karooheadwind.datatypes.HeadwindDirectionDataType
import de.timklge.karooheadwind.datatypes.HeadwindSpeedDataType
import de.timklge.karooheadwind.datatypes.PrecipitationDataType
import de.timklge.karooheadwind.datatypes.PrecipitationForecastDataType
import de.timklge.karooheadwind.datatypes.RelativeHumidityDataType
import de.timklge.karooheadwind.datatypes.SealevelPressureDataType
import de.timklge.karooheadwind.datatypes.SurfacePressureDataType
import de.timklge.karooheadwind.datatypes.TailwindAndRideSpeedDataType
import de.timklge.karooheadwind.datatypes.TailwindDataType
import de.timklge.karooheadwind.datatypes.TemperatureDataType
import de.timklge.karooheadwind.datatypes.TemperatureForecastDataType
import de.timklge.karooheadwind.datatypes.UserWindSpeedDataType
import de.timklge.karooheadwind.datatypes.WeatherDataType
import de.timklge.karooheadwind.datatypes.WeatherForecastDataType
import de.timklge.karooheadwind.datatypes.WindDirectionDataType
import de.timklge.karooheadwind.datatypes.WindForecastDataType
import de.timklge.karooheadwind.datatypes.WindGustsDataType
import de.timklge.karooheadwind.datatypes.WindSpeedDataType
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.debounce
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.zip.GZIPInputStream
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class KarooHeadwindExtension : KarooExtension("karoo-headwind", BuildConfig.VERSION_NAME) {
    companion object {
        const val TAG = "karoo-headwind"
    }

    private lateinit var karooSystem: KarooSystemService

    private var updateLastKnownGpsJob: Job? = null
    private var serviceJob: Job? = null

    override val types by lazy {
        listOf(
            HeadwindDirectionDataType(karooSystem, applicationContext),
            TailwindAndRideSpeedDataType(karooSystem, applicationContext),
            HeadwindSpeedDataType(karooSystem, applicationContext),
            WeatherDataType(karooSystem, applicationContext),
            WeatherForecastDataType(karooSystem),
            HeadwindSpeedDataType(karooSystem, applicationContext),
            RelativeHumidityDataType(applicationContext),
            CloudCoverDataType(applicationContext),
            WindGustsDataType(applicationContext),
            WindSpeedDataType(applicationContext),
            TemperatureDataType(applicationContext),
            WindDirectionDataType(karooSystem, applicationContext),
            PrecipitationDataType(applicationContext),
            SurfacePressureDataType(applicationContext),
            SealevelPressureDataType(applicationContext),
            UserWindSpeedDataType(karooSystem, applicationContext),
            TemperatureForecastDataType(karooSystem),
            PrecipitationForecastDataType(karooSystem),
            WindForecastDataType(karooSystem),
            GraphicalForecastDataType(karooSystem),
            TailwindDataType(karooSystem, applicationContext)
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun onCreate() {
        super.onCreate()

        karooSystem = KarooSystemService(applicationContext)
        ServiceStatusSingleton.getInstance().setServiceStatus(true)


        updateLastKnownGpsJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.updateLastKnownGps(this@KarooHeadwindExtension)
        }

        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.connect { connected ->
                if (connected) {
                    Log.d(TAG, "Connected to Karoo system")
                }
            }

            val gpsFlow = karooSystem
                .getGpsCoordinateFlow(this@KarooHeadwindExtension)
                .distinctUntilChanged { old, new ->
                    if (old != null && new != null) {
                        old.distanceTo(new).absoluteValue < 0.001
                    } else {
                        old == new
                    }
                }
                .debounce(Duration.ofSeconds(5))

            var requestedGpsCoordinates: List<GpsCoordinates> = mutableListOf()

            val settingsStream = streamSettings(karooSystem)
                .filter { it.welcomeDialogAccepted }

            data class StreamData(val settings: HeadwindSettings, val gps: GpsCoordinates?, val profile: UserProfile?, val upcomingRoute: UpcomingRoute?)
            data class StreamDataIdentity(val settings: HeadwindSettings, val gpsLat: Double?, val gpsLon: Double?, val profile: UserProfile?, val routePolyline: LineString?)

            combine(settingsStream, gpsFlow, karooSystem.streamUserProfile(), karooSystem.streamUpcomingRoute()) { settings, gps, profile, upcomingRoute ->
                StreamData(settings, gps, profile, upcomingRoute)
            }
            .distinctUntilChangedBy { StreamDataIdentity(it.settings, it.gps?.lat, it.gps?.lon, it.profile, it.upcomingRoute?.routePolyline) }
            .transformLatest { value ->
                while(true){
                    emit(value)
                    delay(1.hours)
                }
            }
            .map { (settings: HeadwindSettings, gps, profile, upcomingRoute) ->
                Log.d(TAG, "Acquired updated gps coordinates: $gps")

                val lastKnownStats = try {
                    streamStats().first()
                } catch(e: Exception){
                    Log.e(TAG, "Failed to read stats", e)
                    HeadwindStats()
                }

                if (gps == null){
                    error("No GPS coordinates available")
                }

                if (upcomingRoute != null){
                    val positionOnRoute = upcomingRoute.distanceAlongRoute
                    Log.i(TAG, "Position on route: ${positionOnRoute}m")
                    val distancePerHour = settings.getForecastMetersPerHour(profile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL).toDouble()
                    val msSinceFullHour = let {
                        val now = LocalDateTime.now()
                        val startOfHour = now.truncatedTo(ChronoUnit.HOURS)

                        ChronoUnit.MILLIS.between(startOfHour, now)
                    }
                    val msToNextFullHour = (1_000 * 60 * 60) - msSinceFullHour
                    val calculatedDistanceToNextFullHour = ((msToNextFullHour / (1_000.0 * 60 * 60)) * distancePerHour).coerceIn(0.0, distancePerHour)

                    Log.d(TAG, "Minutes to next full hour: ${msToNextFullHour / 1000 / 60}, Distance to next full hour: ${(calculatedDistanceToNextFullHour / 1000).roundToInt()}km")

                    requestedGpsCoordinates = buildList {
                        add(gps)

                        var currentPosition = positionOnRoute + calculatedDistanceToNextFullHour
                        var lastRequestedPosition = positionOnRoute

                        while (currentPosition < upcomingRoute.routeLength && size < 10) {
                            val point = TurfMeasurement.along(
                                upcomingRoute.routePolyline,
                                currentPosition,
                                TurfConstants.UNIT_METERS
                            )
                            add(
                                GpsCoordinates(
                                    point.latitude(),
                                    point.longitude(),
                                    distanceAlongRoute = currentPosition
                                )
                            )

                            lastRequestedPosition = currentPosition
                            currentPosition += distancePerHour
                        }

                        if (upcomingRoute.routeLength > lastRequestedPosition + 1_000) {
                            val point = TurfMeasurement.along(
                                upcomingRoute.routePolyline,
                                upcomingRoute.routeLength,
                                TurfConstants.UNIT_METERS
                            )
                            add(
                                GpsCoordinates(
                                    point.latitude(),
                                    point.longitude(),
                                    distanceAlongRoute = upcomingRoute.routeLength
                                )
                            )
                        }
                    }
                } else {
                    requestedGpsCoordinates = mutableListOf(gps)
                }

                val response = karooSystem.makeOpenMeteoHttpRequest(requestedGpsCoordinates, settings, profile)
                if (response.error != null){
                    try {

                        val stats = lastKnownStats.copy(failedWeatherRequest = System.currentTimeMillis())
                        launch { saveStats(this@KarooHeadwindExtension, stats) }
                    } catch(e: Exception){
                        Log.e(TAG, "Failed to write stats", e)
                    }
                    error("HTTP request failed: ${response.error}")
                } else {
                    try {
                        val responseBody = response.body?.let { String(it) }
                        var weatherDataProvider: WeatherDataProvider? = null

                        try {
                            if (responseBody != null) {
                                if (responseBody.trim().startsWith("[")) {
                                    val responseArray =
                                        jsonWithUnknownKeys.decodeFromString<List<OpenMeteoCurrentWeatherResponse>>(
                                            responseBody
                                        )
                                    weatherDataProvider = responseArray.firstOrNull()?.provider
                                } else {
                                    val responseObject =
                                        jsonWithUnknownKeys.decodeFromString<OpenMeteoCurrentWeatherResponse>(
                                            responseBody
                                        )
                                    weatherDataProvider = responseObject.provider
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error decoding provider", e)
                        }

                        val stats = lastKnownStats.copy(
                            lastSuccessfulWeatherRequest = System.currentTimeMillis(),
                            lastSuccessfulWeatherPosition = gps,
                            lastSuccessfulWeatherProvider = weatherDataProvider
                        )
                        launch { saveStats(this@KarooHeadwindExtension, stats) }
                    } catch(e: Exception){
                        Log.e(TAG, "Failed to write stats", e)
                    }
                }

                response
            }.retry(Long.MAX_VALUE) { e ->
                Log.w(TAG, "Failed to get weather data", e)
                delay(1.minutes); true
            }.collect { response ->
                try {
                    val inputStream = java.io.ByteArrayInputStream(response.body ?: ByteArray(0))
                    val lowercaseHeaders = response.headers.map { (k: String, v: String) -> k.lowercase() to v.lowercase() }.toMap()
                    val isGzippedResponse = lowercaseHeaders["content-encoding"]?.contains("gzip") == true
                    val responseString = if(isGzippedResponse){
                        val gzipStream = withContext(Dispatchers.IO) { GZIPInputStream(inputStream) }
                        gzipStream.use { stream -> String(stream.readBytes()) }
                    } else {
                        inputStream.use { stream -> String(stream.readBytes()) }
                    }
                    if (requestedGpsCoordinates.size == 1){
                        val weatherData = jsonWithUnknownKeys.decodeFromString<OpenMeteoCurrentWeatherResponse>(responseString)
                        val data = WeatherDataResponse(weatherData, requestedGpsCoordinates.single())

                        saveCurrentData(applicationContext, listOf(data))

                        Log.d(TAG, "Got updated weather info: $data")
                    } else {
                        val weatherData = jsonWithUnknownKeys.decodeFromString<List<OpenMeteoCurrentWeatherResponse>>(responseString)
                        val data = weatherData.fastZip(requestedGpsCoordinates) { weather, gps -> WeatherDataResponse(weather, gps) }

                        saveCurrentData(applicationContext, data)

                        Log.d(TAG, "Got updated weather info: $data")
                    }

                    saveWidgetSettings(applicationContext, HeadwindWidgetSettings(currentForecastHourOffset = 0))
                } catch(e: Exception){
                    Log.e(TAG, "Failed to read current weather data", e)
                }
            }
        }
    }

    override fun onDestroy() {
        serviceJob?.cancel()
        serviceJob = null

        updateLastKnownGpsJob?.cancel()
        updateLastKnownGpsJob = null

        karooSystem.disconnect()
        super.onDestroy()
    }
}