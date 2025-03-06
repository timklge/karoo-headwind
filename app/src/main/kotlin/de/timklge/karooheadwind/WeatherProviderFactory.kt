package de.timklge.karooheadwind

import android.util.Log
import java.time.LocalDate

object WeatherProviderFactory {
    private var openWeatherMapConsecutiveFailures = 0
    private var openWeatherMapTotalFailures = 0
    private var openMeteoSuccessfulAfterFailures = false
    private var fallbackUntilDate: LocalDate? = null

    private const val MAX_FAILURES_BEFORE_TEMP_FALLBACK = 3
    private const val MAX_FAILURES_BEFORE_DAILY_FALLBACK = 20

    fun getProvider(settings: HeadwindSettings): WeatherProvider {
        val currentDate = LocalDate.now()


        if (fallbackUntilDate != null && !currentDate.isAfter(fallbackUntilDate)) {
            Log.d(KarooHeadwindExtension.TAG, "Using diary fallback OpenMeteo until $fallbackUntilDate")
            return OpenMeteoProvider()
        }


        if (settings.weatherProvider == WeatherDataProvider.OPEN_WEATHER_MAP &&
            openWeatherMapConsecutiveFailures >= MAX_FAILURES_BEFORE_TEMP_FALLBACK) {
            openWeatherMapConsecutiveFailures = 0
            Log.d(KarooHeadwindExtension.TAG, "Using temporary fallback OpenMeteo")
            return OpenMeteoProvider()
        }


        return when (settings.weatherProvider) {
            WeatherDataProvider.OPEN_METEO -> OpenMeteoProvider()
            WeatherDataProvider.OPEN_WEATHER_MAP -> OpenWeatherMapProvider(settings.openWeatherMapApiKey)
        }
    }

    fun handleOpenWeatherMapFailure() {
        openWeatherMapConsecutiveFailures++
        openWeatherMapTotalFailures++

        Log.d(KarooHeadwindExtension.TAG, "OpenWeatherMap failed $openWeatherMapConsecutiveFailures times consecutive, $openWeatherMapTotalFailures total times")

        if (openWeatherMapTotalFailures >= MAX_FAILURES_BEFORE_DAILY_FALLBACK && openMeteoSuccessfulAfterFailures) {
            fallbackUntilDate = LocalDate.now()
            Log.d(KarooHeadwindExtension.TAG, "Activated daily fallback OpenMeteo until $fallbackUntilDate")
        }
    }

    fun handleOpenMeteoSuccess() {
        openMeteoSuccessfulAfterFailures = openWeatherMapTotalFailures > 0
    }

    fun resetOpenWeatherMapFailures() {
        openWeatherMapConsecutiveFailures = 0
    }

    fun resetAllFailures() {
        openWeatherMapConsecutiveFailures = 0
        openWeatherMapTotalFailures = 0
        openMeteoSuccessfulAfterFailures = false
        fallbackUntilDate = null
    }
}


data class ProviderState(
    val provider: WeatherDataProvider,
    var consecutiveFailures: Int = 0
)