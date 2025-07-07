package de.timklge.karooheadwind

import android.util.Log
import com.mapbox.geojson.LineString
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import de.timklge.karooheadwind.datatypes.CloudCoverDataType
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.datatypes.HeadwindDirectionDataType
import de.timklge.karooheadwind.datatypes.HeadwindSpeedDataType
import de.timklge.karooheadwind.datatypes.PrecipitationDataType
import de.timklge.karooheadwind.datatypes.PrecipitationForecastDataType
import de.timklge.karooheadwind.datatypes.RelativeElevationGainDataType
import de.timklge.karooheadwind.datatypes.RelativeGradeDataType
import de.timklge.karooheadwind.datatypes.RelativeHumidityDataType
import de.timklge.karooheadwind.datatypes.SealevelPressureDataType
import de.timklge.karooheadwind.datatypes.SurfacePressureDataType
import de.timklge.karooheadwind.datatypes.TailwindAndRideSpeedDataType
import de.timklge.karooheadwind.datatypes.TemperatureDataType
import de.timklge.karooheadwind.datatypes.UviDataType
import de.timklge.karooheadwind.datatypes.TemperatureForecastDataType
import de.timklge.karooheadwind.datatypes.WeatherForecastDataType
import de.timklge.karooheadwind.datatypes.WindDirectionAndSpeedDataType
import de.timklge.karooheadwind.datatypes.WindDirectionAndSpeedDataTypeCircle
import de.timklge.karooheadwind.datatypes.WindDirectionDataType
import de.timklge.karooheadwind.datatypes.WindForecastDataType
import de.timklge.karooheadwind.datatypes.WindGustsDataType
import de.timklge.karooheadwind.datatypes.WindSpeedDataType
import de.timklge.karooheadwind.weatherprovider.WeatherProviderFactory
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
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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
            WindDirectionAndSpeedDataTypeCircle(karooSystem, applicationContext),
            WeatherForecastDataType(karooSystem),
            HeadwindSpeedDataType(karooSystem, applicationContext),
            RelativeHumidityDataType(karooSystem, applicationContext),
            CloudCoverDataType(karooSystem, applicationContext),
            WindGustsDataType(karooSystem, applicationContext),
            WindSpeedDataType(karooSystem, applicationContext),
            WindDirectionDataType(karooSystem, applicationContext),
            WindDirectionAndSpeedDataType(karooSystem, applicationContext),
            PrecipitationDataType(karooSystem, applicationContext),
            SurfacePressureDataType(karooSystem, applicationContext),
            SealevelPressureDataType(karooSystem, applicationContext),
            TemperatureForecastDataType(karooSystem),
            PrecipitationForecastDataType(karooSystem),
            WindForecastDataType(karooSystem),
            WindDirectionAndSpeedDataType(karooSystem, applicationContext),
            RelativeGradeDataType(karooSystem, applicationContext),
            RelativeElevationGainDataType(karooSystem, applicationContext),
            TemperatureDataType(karooSystem, applicationContext),
            UviDataType(karooSystem, applicationContext)
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
                .throttle(5_000L)

            var requestedGpsCoordinates: List<GpsCoordinates> = emptyList()

            val settingsStream = streamSettings(karooSystem).filter { it.welcomeDialogAccepted }

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
                        add(GpsCoordinates(gps.lat, gps.lon, gps.bearing, distanceAlongRoute = positionOnRoute))

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

                val response = try {
                    WeatherProviderFactory.makeWeatherRequest(karooSystem, requestedGpsCoordinates, settings, profile)
                } catch(e: Throwable){
                    val stats = lastKnownStats.copy(failedWeatherRequest = System.currentTimeMillis())
                    launch {
                        try {
                            saveStats(this@KarooHeadwindExtension, stats)
                        } catch(e: Exception){
                            Log.e(TAG, "Failed to write stats", e)
                        }
                    }
                    throw e
                }

                try {
                    val stats = lastKnownStats.copy(
                        lastSuccessfulWeatherRequest = System.currentTimeMillis(),
                        lastSuccessfulWeatherPosition = gps,
                        lastSuccessfulWeatherProvider = response.provider
                    )
                    launch { saveStats(this@KarooHeadwindExtension, stats) }
                } catch(e: Exception){
                    Log.e(TAG, "Failed to write stats", e)
                }

                response
            }.retry(Long.MAX_VALUE) { e ->
                Log.w(TAG, "Failed to get weather data", e)
                delay(2.minutes); true
            }.collect { response ->
                try {
                    saveCurrentData(applicationContext, response)
                    Log.d(TAG, "Got updated weather info: $response")

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