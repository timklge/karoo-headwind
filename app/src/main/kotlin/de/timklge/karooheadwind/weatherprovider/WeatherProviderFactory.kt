package de.timklge.karooheadwind.weatherprovider

import android.util.Log
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.WeatherDataProvider
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.weatherprovider.openmeteo.OpenMeteoWeatherProvider
import de.timklge.karooheadwind.weatherprovider.openweathermap.OpenWeatherMapWeatherProvider
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile

object WeatherProviderFactory {
    suspend fun makeWeatherRequest(
        karooSystemService: KarooSystemService,
        gpsCoordinates: List<GpsCoordinates>,
        settings: HeadwindSettings,
        profile: UserProfile?
    ): WeatherDataResponse {
        val provider = getProvider(settings)

        try {
            val response = provider.getWeatherData(karooSystemService, gpsCoordinates, settings, profile)

            return response
        } catch(e: Throwable){
            Log.d(KarooHeadwindExtension.TAG, "Weather request failed: $e")

            throw e;
        }
    }

    private fun getProvider(settings: HeadwindSettings): WeatherProvider {
        return when (settings.weatherProvider) {
            WeatherDataProvider.OPEN_METEO -> OpenMeteoWeatherProvider()
            WeatherDataProvider.OPEN_WEATHER_MAP -> OpenWeatherMapWeatherProvider(settings.openWeatherMapApiKey)
        }
    }
}