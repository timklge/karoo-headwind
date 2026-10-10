/*
 * Copyright 2024-2026 karoo-headwind contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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