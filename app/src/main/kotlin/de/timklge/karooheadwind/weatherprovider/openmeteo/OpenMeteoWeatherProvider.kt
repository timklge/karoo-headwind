package de.timklge.karooheadwind.weatherprovider.openmeteo

import android.util.Log
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.PrecipitationUnit
import de.timklge.karooheadwind.TemperatureUnit
import de.timklge.karooheadwind.WeatherDataProvider
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.jsonWithUnknownKeys
import de.timklge.karooheadwind.util.ungzip
import de.timklge.karooheadwind.weatherprovider.WeatherDataResponse
import de.timklge.karooheadwind.weatherprovider.WeatherProvider
import de.timklge.karooheadwind.weatherprovider.WeatherProviderException
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.timeout
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class OpenMeteoWeatherProvider : WeatherProvider {
    @OptIn(FlowPreview::class)
    private suspend fun makeOpenMeteoWeatherRequest(karooSystemService: KarooSystemService, gpsCoordinates: List<GpsCoordinates>, settings: HeadwindSettings, profile: UserProfile?): String {
        val precipitationUnit = if (profile?.preferredUnit?.distance != UserProfile.PreferredUnit.UnitType.IMPERIAL) PrecipitationUnit.MILLIMETERS else PrecipitationUnit.INCH
        val temperatureUnit = if (profile?.preferredUnit?.temperature != UserProfile.PreferredUnit.UnitType.IMPERIAL) TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT

        val response = callbackFlow {
            // https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=surface_pressure,pressure_msl,temperature_2m,relative_humidity_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m&timeformat=unixtime&past_hours=1&forecast_days=1&forecast_hours=12
            val lats = gpsCoordinates.joinToString(",") { String.format(Locale.US, "%.6f", it.lat) }
            val lons = gpsCoordinates.joinToString(",") { String.format(Locale.US, "%.6f", it.lon) }
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${lats}&longitude=${lons}&current=surface_pressure,pressure_msl,temperature_2m,relative_humidity_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m&timeformat=unixtime&past_hours=0&forecast_days=1&forecast_hours=12&wind_speed_unit=${settings.windUnit.id}&precipitation_unit=${precipitationUnit.id}&temperature_unit=${temperatureUnit.id}"

            Log.d(KarooHeadwindExtension.TAG, "Http request to ${url}...")

            val listenerId = karooSystemService.addConsumer(
                OnHttpResponse.MakeHttpRequest(
                    "GET",
                    url,
                    waitForConnection = false,
                    headers = mapOf("User-Agent" to KarooHeadwindExtension.TAG, "Accept-Encoding" to "gzip"),
                ),
                onEvent = { event: OnHttpResponse ->
                    if (event.state is HttpResponseState.Complete){
                        Log.d(KarooHeadwindExtension.TAG, "Http response received")
                        trySend(event.state as HttpResponseState.Complete)
                        close()
                    }
                },
                onError = { err ->
                    Log.d(KarooHeadwindExtension.TAG, "Http error: $err")
                    close(WeatherProviderException(0, "Http error: $err"))
                })
            awaitClose {
                karooSystemService.removeConsumer(listenerId)
            }
        }.timeout(30.seconds).catch { e: Throwable ->
            if (e is TimeoutCancellationException){
                emit(HttpResponseState.Complete(500, mapOf(), null, "Timeout"))
            } else {
                throw e
            }
        }.single()

        if (response.statusCode !in 200..299) {
            Log.e(KarooHeadwindExtension.TAG, "OpenWeatherMap API request failed with status code ${response.statusCode}")
            throw WeatherProviderException(response.statusCode, "OpenWeatherMap API request failed with status code ${response.statusCode}")
        }

        return ungzip(response)
    }

    override suspend fun getWeatherData(
        karooSystem: KarooSystemService,
        coordinates: List<GpsCoordinates>,
        settings: HeadwindSettings,
        profile: UserProfile?
    ): WeatherDataResponse {
        val openMeteoResponse = makeOpenMeteoWeatherRequest(karooSystem, coordinates, settings, profile)

        val weatherData = if (coordinates.size == 1) {
            listOf(jsonWithUnknownKeys.decodeFromString<OpenMeteoWeatherDataForLocation>(openMeteoResponse))
        } else {
            jsonWithUnknownKeys.decodeFromString<List<OpenMeteoWeatherDataForLocation>>(openMeteoResponse)
        }

        val response = WeatherDataResponse(
            provider = WeatherDataProvider.OPEN_METEO,
            data = weatherData.map { it.toWeatherDataForLocation() }
        )

        return response
    }
}