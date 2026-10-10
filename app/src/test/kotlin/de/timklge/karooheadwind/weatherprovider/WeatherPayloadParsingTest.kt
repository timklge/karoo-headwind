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

import de.timklge.karooheadwind.WeatherTestPayloads
import de.timklge.karooheadwind.jsonWithUnknownKeys
import de.timklge.karooheadwind.weatherprovider.openmeteo.OpenMeteoWeatherDataForLocation
import de.timklge.karooheadwind.weatherprovider.openweathermap.OpenWeatherMapWeatherDataForLocation
import de.timklge.karooheadwind.weatherprovider.openweathermap.OpenWeatherMapWeatherProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The provider payload mappers, exercised directly. These are pure JVM tests: no Robolectric and no
 * fake Karoo system, since decoding a response does not touch the binder.
 */
class WeatherPayloadParsingTest {
    private val openMeteo = WeatherTestPayloads.OpenMeteo()

    @Test
    fun `an open-meteo current block maps into si weather data`() {
        val location =
            jsonWithUnknownKeys.decodeFromString<OpenMeteoWeatherDataForLocation>(
                WeatherTestPayloads.openMeteo()
            )
        val data = location.toWeatherDataForLocation(distanceAlongRoute = 1_234.5)

        assertEquals(12.5, data.current.temperature, 0.0)
        assertEquals(70, data.current.relativeHumidity)
        assertEquals(40.0, data.current.cloudCover, 0.0)
        assertEquals(1_010.0, data.current.surfacePressure, 0.0)
        assertEquals(1_015.0, data.current.sealevelPressure, 0.0)
        assertEquals(5.0, data.current.windSpeed, 0.0)
        assertEquals(270.0, data.current.windDirection, 0.0)
        assertEquals(9.0, data.current.windGusts, 0.0)
        assertEquals(1, data.current.weatherCode)
        assertEquals(2.0, data.current.uvi, 0.0)
        assertTrue(data.current.isForecast.not())
        assertTrue(data.current.isNight.not())
    }

    @Test
    fun `an open-meteo location takes its position from the response and its route distance from the request`() {
        val location =
            jsonWithUnknownKeys.decodeFromString<OpenMeteoWeatherDataForLocation>(
                WeatherTestPayloads.openMeteo()
            )

        val data = location.toWeatherDataForLocation(distanceAlongRoute = 1_234.5)

        assertEquals(openMeteo.latitude, data.coords.lat, 0.0)
        assertEquals(openMeteo.longitude, data.coords.lon, 0.0)
        assertNull("the response has no rider bearing", data.coords.bearing)
        assertEquals(1_234.5, data.coords.distanceAlongRoute!!, 0.0)
        assertEquals("GMT", data.timezone)
        assertEquals(38.0, data.elevation!!, 0.0)
    }

    @Test
    fun `open-meteo hourly entries all become forecast points`() {
        val location =
            jsonWithUnknownKeys.decodeFromString<OpenMeteoWeatherDataForLocation>(
                WeatherTestPayloads.openMeteo()
            )

        val forecasts = location.toWeatherDataForLocation(distanceAlongRoute = null).forecasts!!

        assertEquals(3, forecasts.size)
        assertTrue("every hourly entry is a forecast", forecasts.all { it.isForecast })
        assertTrue("every hourly entry is daytime", forecasts.none { it.isNight })
        assertEquals(WeatherTestPayloads.CURRENT_TIME_SECONDS, forecasts.first().time)
    }

    @Test
    fun `an open-meteo response without hourly leaves the forecast absent`() {
        val location =
            jsonWithUnknownKeys.decodeFromString<OpenMeteoWeatherDataForLocation>(
                WeatherTestPayloads.openMeteoWithoutForecast()
            )

        val data = location.toWeatherDataForLocation(distanceAlongRoute = null)

        assertNull(data.forecasts)
        assertEquals(5.0, data.current.windSpeed, 0.0)
    }

    @Test
    fun `an open-meteo is_day of zero reads as night`() {
        val location =
            jsonWithUnknownKeys.decodeFromString<OpenMeteoWeatherDataForLocation>(
                WeatherTestPayloads.openMeteo(WeatherTestPayloads.OpenMeteo(isDay = 0))
            )

        assertTrue(location.toWeatherDataForLocation(null).current.isNight)
    }

    @Test
    fun `openweathermap current block maps into si weather data`() {
        val location =
            jsonWithUnknownKeys.decodeFromString<OpenWeatherMapWeatherDataForLocation>(
                WeatherTestPayloads.openWeatherMap()
            )

        val current = location.toWeatherDataForLocation(distanceAlongRoute = 42.0).current

        assertEquals(12.5, current.temperature, 0.0)
        assertEquals(70, current.relativeHumidity)
        assertEquals(40.0, current.cloudCover, 0.0)
        assertEquals(5.0, current.windSpeed, 0.0)
        assertEquals(270.0, current.windDirection, 0.0)
        assertEquals(9.0, current.windGusts, 0.0)
        assertEquals(2.0, current.uvi, 0.0)
        assertEquals(WeatherTestPayloads.CURRENT_TIME_SECONDS, current.time)
    }

    @Test
    fun `openweathermap falls back to the wind speed when the response has no gust`() {
        val location =
            jsonWithUnknownKeys.decodeFromString<OpenWeatherMapWeatherDataForLocation>(
                WeatherTestPayloads.openWeatherMap(WeatherTestPayloads.OpenWeatherMap(windGust = null))
            )

        assertEquals(5.0, location.current.toWeatherData().windGusts, 0.0)
    }

    @Test
    fun `openweathermap reports a timestamp outside its sunrise window as night`() {
        val location =
            jsonWithUnknownKeys.decodeFromString<OpenWeatherMapWeatherDataForLocation>(
                WeatherTestPayloads.openWeatherMap()
            )

        assertTrue(
            "the recorded time is inside the sunrise window",
            location.current.toWeatherData().isNight.not(),
        )

        val night = location.current.copy(dt = 1_760_040_000).toWeatherData()
        assertTrue("a time after sunset is night", night.isNight)
    }

    @Test
    fun `openweathermap takes precipitation from the one hour rain figure`() {
        val location =
            jsonWithUnknownKeys.decodeFromString<OpenWeatherMapWeatherDataForLocation>(
                WeatherTestPayloads.openWeatherMap(
                    WeatherTestPayloads.OpenWeatherMap(rain = 0.8, weatherId = 501),
                )
            )

        val current = location.current.toWeatherData()

        assertEquals(0.8, current.precipitation, 0.0)
        assertEquals("moderate rain maps to the wmo rain code", 61, current.weatherCode)
    }

    @Test
    fun `openweathermap maps its condition ranges onto wmo codes`() {
        val convert = { code: Int -> OpenWeatherMapWeatherProvider.convertWeatherCodeToOpenMeteo(code) }

        assertEquals(95, convert(200))
        assertEquals(95, convert(299))
        assertEquals(51, convert(300))
        assertEquals(51, convert(399))
        assertEquals(61, convert(500))
        assertEquals(61, convert(599))
        assertEquals(71, convert(600))
        assertEquals(71, convert(699))
        assertEquals(0, convert(800))
        assertEquals(1, convert(801))
        assertEquals(1, convert(804))
    }

    @Test
    fun `openweathermap forecast points are all forecasts and carry the current condition`() {
        val location =
            jsonWithUnknownKeys.decodeFromString<OpenWeatherMapWeatherDataForLocation>(
                WeatherTestPayloads.openWeatherMap()
            )

        val forecast = location.toWeatherDataForLocation(null).forecasts!!

        assertEquals(3, forecast.size)
        assertTrue(forecast.all { it.isForecast })
        assertEquals("cloudy", 1, forecast.first().weatherCode)
    }

    @Test
    fun `every known weather code has an interpretation and unknown codes are unknown`() {
        val known = WeatherInterpretation.getKnownWeatherCodes()

        assertTrue("the known set is not empty", known.isNotEmpty())
        assertTrue(
            "every known code has an interpretation",
            known.all { WeatherInterpretation.fromWeatherCode(it) != WeatherInterpretation.UNKNOWN },
        )
        assertEquals(WeatherInterpretation.UNKNOWN, WeatherInterpretation.fromWeatherCode(null))
        assertEquals(WeatherInterpretation.UNKNOWN, WeatherInterpretation.fromWeatherCode(4))
        assertEquals(WeatherInterpretation.CLEAR, WeatherInterpretation.fromWeatherCode(0))
        assertEquals(WeatherInterpretation.CLOUDY, WeatherInterpretation.fromWeatherCode(2))
        assertEquals(WeatherInterpretation.RAINY, WeatherInterpretation.fromWeatherCode(65))
        assertEquals(WeatherInterpretation.SNOWY, WeatherInterpretation.fromWeatherCode(75))
        assertEquals(WeatherInterpretation.DRIZZLE, WeatherInterpretation.fromWeatherCode(53))
        assertEquals(WeatherInterpretation.THUNDERSTORM, WeatherInterpretation.fromWeatherCode(96))
    }
}
