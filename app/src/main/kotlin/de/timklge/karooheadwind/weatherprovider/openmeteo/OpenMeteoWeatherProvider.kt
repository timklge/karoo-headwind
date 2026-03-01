package de.timklge.karooheadwind.weatherprovider.openmeteo

import android.util.Log
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.WeatherDataProvider
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.jsonWithUnknownKeys
import de.timklge.karooheadwind.util.buildKarooOkHttpClient
import de.timklge.karooheadwind.weatherprovider.WeatherDataResponse
import de.timklge.karooheadwind.weatherprovider.WeatherProvider
import de.timklge.karooheadwind.weatherprovider.WeatherProviderException
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.Locale

class OpenMeteoWeatherProvider : WeatherProvider {

    private suspend fun makeOpenMeteoWeatherRequest(
        karooSystemService: KarooSystemService,
        gpsCoordinates: List<GpsCoordinates>
    ): String {
        val lats = gpsCoordinates.joinToString(",") { String.format(Locale.US, "%.6f", it.lat) }
        val lons = gpsCoordinates.joinToString(",") { String.format(Locale.US, "%.6f", it.lon) }
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${lats}&longitude=${lons}" +
            "&current=is_day,surface_pressure,pressure_msl,uv_index,temperature_2m,relative_humidity_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
            "&hourly=uv_index,temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,is_day,surface_pressure,pressure_msl,relative_humidity_2m,cloud_cover" +
            "&timeformat=unixtime&past_hours=0&forecast_days=1&forecast_hours=12&wind_speed_unit=ms"

        Log.d(KarooHeadwindExtension.TAG, "OpenMeteo: GET $url")

        val client = buildKarooOkHttpClient(karooSystemService)
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (response.code !in 200..299) {
            Log.e(KarooHeadwindExtension.TAG, "OpenMeteo API request failed with status code ${response.code}")
            throw WeatherProviderException(response.code, "OpenMeteo API request failed with status code ${response.code}")
        }

        return response.body?.string() ?: throw WeatherProviderException(500, "Null response from OpenMeteo")
    }

    override suspend fun getWeatherData(
        karooSystem: KarooSystemService,
        coordinates: List<GpsCoordinates>,
        settings: HeadwindSettings,
        profile: UserProfile?
    ): WeatherDataResponse {
        val responseBody = makeOpenMeteoWeatherRequest(karooSystem, coordinates)

        val weatherData = if (coordinates.size == 1) {
            listOf(jsonWithUnknownKeys.decodeFromString<OpenMeteoWeatherDataForLocation>(responseBody))
        } else {
            jsonWithUnknownKeys.decodeFromString<List<OpenMeteoWeatherDataForLocation>>(responseBody)
        }

        return WeatherDataResponse(
            provider = WeatherDataProvider.OPEN_METEO,
            data = weatherData.zip(coordinates) { openMeteoWeatherDataForLocation, location ->
                openMeteoWeatherDataForLocation.toWeatherDataForLocation(location.distanceAlongRoute)
            }
        )
    }
}
