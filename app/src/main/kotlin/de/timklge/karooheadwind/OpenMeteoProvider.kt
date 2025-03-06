package de.timklge.karooheadwind

import android.util.Log
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.UserProfile

class OpenMeteoProvider : WeatherProvider {
    override suspend fun getWeatherData(
        karooSystem: KarooSystemService,
        coordinates: List<GpsCoordinates>,
        settings: HeadwindSettings,
        profile: UserProfile?
    ): HttpResponseState.Complete {
        val response = karooSystem.originalOpenMeteoRequest(coordinates, settings, profile)

        if (response.error != null || response.body == null) {
            return response
        }

        try {

            val responseBody = response.body?.let { String(it) }
                ?: return response

            val weatherData = jsonWithUnknownKeys.decodeFromString<OpenMeteoCurrentWeatherResponse>(responseBody)


            val updatedData = weatherData.copy(provider = WeatherDataProvider.OPEN_METEO)
            val updatedJson = jsonWithUnknownKeys.encodeToString(updatedData)

            return response.copy(body = updatedJson.toByteArray())
        } catch (e: Exception) {
            Log.e(KarooHeadwindExtension.TAG, "Error processing OpenMeteo response", e)
            return response
        }
    }
}