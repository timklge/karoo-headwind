package de.timklge.karooheadwind.weatherprovider

import android.util.Log
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.weatherprovider.openmeteo.OpenMeteoWeatherProvider
import de.timklge.karooheadwind.weatherprovider.openweathermap.OpenWeatherMapWeatherProvider
import de.timklge.karooheadwind.WeatherDataProvider
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile
import java.time.LocalDate

object WeatherProviderFactory {
    private var openWeatherMapConsecutiveFailures = 0
    private var openWeatherMapTotalFailures = 0
    private var openMeteoSuccessfulAfterFailures = false
    private var fallbackUntilDate: LocalDate? = null

    private const val MAX_FAILURES_BEFORE_TEMP_FALLBACK = 3
    private const val MAX_FAILURES_BEFORE_DAILY_FALLBACK = 20

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

            if (provider is OpenWeatherMapWeatherProvider && (e is WeatherProviderException && (e.statusCode == 401 || e.statusCode == 403))) {
                handleOpenWeatherMapFailure()
            }

            throw e;
        }
    }

    private fun getProvider(settings: HeadwindSettings): WeatherProvider {
        val currentDate = LocalDate.now()

        if (fallbackUntilDate != null && !currentDate.isAfter(fallbackUntilDate)) {
            Log.d(KarooHeadwindExtension.TAG, "Using fallback OpenMeteo until $fallbackUntilDate")
            return OpenMeteoWeatherProvider()
        }


        if (settings.weatherProvider == WeatherDataProvider.OPEN_WEATHER_MAP &&
            openWeatherMapConsecutiveFailures >= MAX_FAILURES_BEFORE_TEMP_FALLBACK
        ) {
            openWeatherMapConsecutiveFailures = 0
            Log.d(KarooHeadwindExtension.TAG, "Using temporary fallback OpenMeteo")
            return OpenMeteoWeatherProvider()
        }


        return when (settings.weatherProvider) {
            WeatherDataProvider.OPEN_METEO -> OpenMeteoWeatherProvider()
            WeatherDataProvider.OPEN_WEATHER_MAP -> OpenWeatherMapWeatherProvider(settings.openWeatherMapApiKey)
        }
    }

    private fun handleOpenWeatherMapFailure() {
        openWeatherMapConsecutiveFailures++
        openWeatherMapTotalFailures++

        Log.d(KarooHeadwindExtension.TAG, "OpenWeatherMap failed $openWeatherMapConsecutiveFailures times consecutive, $openWeatherMapTotalFailures total times")

        if (openWeatherMapTotalFailures >= MAX_FAILURES_BEFORE_DAILY_FALLBACK && openMeteoSuccessfulAfterFailures) {
            fallbackUntilDate = LocalDate.now()
            Log.d(KarooHeadwindExtension.TAG, "Activated daily fallback OpenMeteo until $fallbackUntilDate")
        }
    }

}