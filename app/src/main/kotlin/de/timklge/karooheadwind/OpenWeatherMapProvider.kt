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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
data class OneCallResponse(
    val lat: Double,
    val lon: Double,
    val timezone: String,
    @SerialName("timezone_offset") val timezoneOffset: Int,
    val current: CurrentWeather,
    val hourly: List<HourlyForecast>
)

@Serializable
data class CurrentWeather(
    val dt: Long,
    val sunrise: Long,
    val sunset: Long,
    val temp: Double,
    val feels_like: Double,
    val pressure: Int,
    val humidity: Int,
    val clouds: Int,
    val visibility: Int,
    val wind_speed: Double,
    val wind_deg: Int,
    val wind_gust: Double? = null,
    val rain: Rain? = null,
    val snow: Snow? = null,
    val weather: List<Weather>
)

@Serializable
data class HourlyForecast(
    val dt: Long,
    val temp: Double,
    val feels_like: Double,
    val pressure: Int,
    val humidity: Int,
    val clouds: Int,
    val visibility: Int,
    val wind_speed: Double,
    val wind_deg: Int,
    val wind_gust: Double? = null,
    val pop: Double,
    val rain: Rain? = null,
    val snow: Snow? = null,
    val weather: List<Weather>
)


@Serializable
data class Weather(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

@Serializable
data class Rain(
    @SerialName("1h") val h1: Double = 0.0,
    @SerialName("3h") val h3: Double = 0.0
)

@Serializable
data class Snow(
    @SerialName("1h") val h1: Double = 0.0,
    @SerialName("3h") val h3: Double = 0.0
)


class OpenWeatherMapProvider(private val apiKey: String) : WeatherProvider {
    override suspend fun getWeatherData(
        service: KarooSystemService,
        coordinates: List<GpsCoordinates>,
        settings: HeadwindSettings,
        profile: UserProfile?
    ): HttpResponseState.Complete {

        val response = makeOpenWeatherMapRequest(service, coordinates, apiKey)

        if (response.error != null || response.body == null) {
            return response
        }

        try {
            val responseBody = response.body?.let { String(it) }
                ?: throw Exception("Respuesta nula de OpenWeatherMap")


            if (coordinates.size > 1) {
                val responses = mutableListOf<OpenMeteoCurrentWeatherResponse>()


                val oneCallResponse = jsonWithUnknownKeys.decodeFromString<OneCallResponse>(responseBody)
                responses.add(convertToOpenMeteoFormat(oneCallResponse))

                val finalBody = jsonWithUnknownKeys.encodeToString(responses)
                return HttpResponseState.Complete(
                    statusCode = response.statusCode,
                    headers = response.headers,
                    body = finalBody.toByteArray(),
                    error = null
                )
            } else {

                val oneCallResponse = jsonWithUnknownKeys.decodeFromString<OneCallResponse>(responseBody)
                val convertedResponse = convertToOpenMeteoFormat(oneCallResponse)

                val finalBody = jsonWithUnknownKeys.encodeToString(convertedResponse)
                return HttpResponseState.Complete(
                    statusCode = response.statusCode,
                    headers = response.headers,
                    body = finalBody.toByteArray(),
                    error = null
                )
            }
        } catch (e: Exception) {
            Log.e(KarooHeadwindExtension.TAG, "Error OpenWeatherMap answer processing", e)
            return HttpResponseState.Complete(
                statusCode = 500,
                headers = mapOf(),
                body = null,
                error = "Error processing data: ${e.message}"
            )
        }
    }

    private fun convertToOpenMeteoFormat(oneCallResponse: OneCallResponse): OpenMeteoCurrentWeatherResponse {

        val current = OpenMeteoData(
            time = oneCallResponse.current.dt,
            interval = 3600,
            temperature = oneCallResponse.current.temp,
            relativeHumidity = oneCallResponse.current.humidity,
            precipitation = oneCallResponse.current.rain?.h1 ?: 0.0,
            cloudCover = oneCallResponse.current.clouds,
            surfacePressure = oneCallResponse.current.pressure.toDouble(),
            sealevelPressure = oneCallResponse.current.pressure.toDouble(),
            windSpeed = oneCallResponse.current.wind_speed,
            windDirection = oneCallResponse.current.wind_deg.toDouble(),
            windGusts = oneCallResponse.current.wind_gust ?: oneCallResponse.current.wind_speed,
            weatherCode = convertWeatherCodeToOpenMeteo(oneCallResponse.current.weather.firstOrNull()?.id ?: 800)
        )


        val forecastHours = minOf(12, oneCallResponse.hourly.size)
        val hourlyForecasts = oneCallResponse.hourly.take(forecastHours)

        val forecastData = OpenMeteoForecastData(
            time = hourlyForecasts.map { it.dt },
            temperature = hourlyForecasts.map { it.temp },
            precipitationProbability = hourlyForecasts.map { (it.pop * 100).toInt() },
            precipitation = hourlyForecasts.map { it.rain?.h1 ?: 0.0 },
            weatherCode = hourlyForecasts.map { convertWeatherCodeToOpenMeteo(it.weather.firstOrNull()?.id ?: 800) },
            windSpeed = hourlyForecasts.map { it.wind_speed },
            windDirection = hourlyForecasts.map { it.wind_deg.toDouble() },
            windGusts = hourlyForecasts.map { it.wind_gust ?: it.wind_speed }
        )

        return OpenMeteoCurrentWeatherResponse(
            current = current,
            latitude = oneCallResponse.lat,
            longitude = oneCallResponse.lon,
            timezone = oneCallResponse.timezone,
            elevation = 0.0,
            utfOffsetSeconds = oneCallResponse.timezoneOffset,
            forecastData = forecastData,
            provider = WeatherDataProvider.OPEN_WEATHER_MAP
        )
    }

    private fun convertWeatherCodeToOpenMeteo(owmCode: Int): Int {
        // Mapping OpenWeatherMap a WMO OpenMeteo
        return when (owmCode) {
            in 200..299 -> 95 // Tormentas
            in 300..399 -> 51 // Llovizna
            in 500..599 -> 61 // Lluvia
            in 600..699 -> 71 // Nieve
            800 -> 0        // Despejado
            in 801..804 -> 1 // Nubosidad
            else -> 0
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun makeOpenWeatherMapRequest(
        service: KarooSystemService,
        coordinates: List<GpsCoordinates>,
        apiKey: String
    ): HttpResponseState.Complete {
        return callbackFlow {
            val coordinate = coordinates.first()
            // URL API 3.0 con endpoint onecall
            val url = "https://api.openweathermap.org/data/3.0/onecall?lat=${coordinate.lat}&lon=${coordinate.lon}" +
                    "&appid=$apiKey&units=metric&exclude=minutely,daily,alerts"

            Log.d(KarooHeadwindExtension.TAG, "Http request to OpenWeatherMap API 3.0: $url")

            val listenerId = service.addConsumer(
                OnHttpResponse.MakeHttpRequest(
                    "GET",
                    url,
                    waitForConnection = false,
                    headers = mapOf("User-Agent" to KarooHeadwindExtension.TAG)
                ),
                onEvent = { event: OnHttpResponse ->
                    if (event.state is HttpResponseState.Complete) {
                        Log.d(KarooHeadwindExtension.TAG, "Http response received from OpenWeatherMap")
                        trySend(event.state as HttpResponseState.Complete)
                        close()
                    }
                },
                onError = { err ->
                    Log.e(KarooHeadwindExtension.TAG, "Http error: $err")
                    close(RuntimeException(err))
                }
            )

            awaitClose {
                service.removeConsumer(listenerId)
            }
        }.timeout(30.seconds).catch { e: Throwable ->
            if (e is TimeoutCancellationException) {
                emit(HttpResponseState.Complete(500, mapOf(), null, "Timeout"))
            } else {
                throw e
            }
        }.single()
    }
}