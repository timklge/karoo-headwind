package de.timklge.karooheadwind

import android.util.Log
import de.timklge.karooheadwind.datatypes.GpsCoordinates
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
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
suspend fun KarooSystemService.originalOpenMeteoRequest(
    gpsCoordinates: List<GpsCoordinates>,
    settings: HeadwindSettings,
    profile: UserProfile?,
    precipitationUnit: PrecipitationUnit = PrecipitationUnit.MILLIMETERS,
    temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS
): HttpResponseState.Complete {
    return callbackFlow {
        // https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=surface_pressure,pressure_msl,temperature_2m,relative_humidity_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m&timeformat=unixtime&past_hours=1&forecast_days=1&forecast_hours=12
        val lats = gpsCoordinates.joinToString(",") { String.format(Locale.US, "%.6f", it.lat) }
        val lons = gpsCoordinates.joinToString(",") { String.format(Locale.US, "%.6f", it.lon) }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${lats}&longitude=${lons}&current=surface_pressure,pressure_msl,temperature_2m,relative_humidity_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m&timeformat=unixtime&past_hours=0&forecast_days=1&forecast_hours=12&wind_speed_unit=${settings.windUnit.id}&precipitation_unit=${precipitationUnit.id}&temperature_unit=${temperatureUnit.id}"

        Log.d(KarooHeadwindExtension.TAG, "Http request to ${url}...")

        val listenerId = addConsumer(
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
            close(RuntimeException(err))
        })
        awaitClose {
            removeConsumer(listenerId)
        }
    }.timeout(30.seconds).catch { e: Throwable ->
        if (e is TimeoutCancellationException){
                Log.d(KarooHeadwindExtension.TAG, "Http request timed out")
                emit(HttpResponseState.Complete(408, emptyMap(), null, "Request timed out"))
            } else {
                Log.d(KarooHeadwindExtension.TAG, "Http request failed", e)
                emit(HttpResponseState.Complete(500, emptyMap(), null, e.message))
            }
    }.single()
}

@OptIn(FlowPreview::class)
suspend fun KarooSystemService.makeOpenMeteoHttpRequest(
    gpsCoordinates: List<GpsCoordinates>,
    settings: HeadwindSettings,
    profile: UserProfile?,
    context: android.content.Context // Agregar el contexto como parámetro
): HttpResponseState.Complete {
    val provider = WeatherProviderFactory.getProvider(settings)
    val response = provider.getWeatherData(this, gpsCoordinates, settings, profile)

    if (response.error != null) {
        if (provider is OpenWeatherMapProvider) {
            WeatherProviderFactory.handleOpenWeatherMapFailure()
        }
    } else {

        val usedProvider = when(provider) {
            is OpenWeatherMapProvider -> WeatherDataProvider.OPEN_WEATHER_MAP
            is OpenMeteoProvider -> WeatherDataProvider.OPEN_METEO
            else -> null
        }

        if (provider is OpenWeatherMapProvider) {
            WeatherProviderFactory.resetOpenWeatherMapFailures()
        } else if (provider is OpenMeteoProvider) {
            WeatherProviderFactory.handleOpenMeteoSuccess()
        }


        try {
            val lastKnownStats = context.streamStats().first()
            val stats = lastKnownStats.copy(
                lastSuccessfulWeatherRequest = System.currentTimeMillis(),
                lastSuccessfulWeatherPosition = gpsCoordinates.firstOrNull(),
                lastSuccessfulWeatherProvider = usedProvider
            )
            saveStats(context, stats)
        } catch(e: Exception) {
            Log.e(KarooHeadwindExtension.TAG, "Error saving stats with provider $usedProvider", e)
        }
    }

    return response
}