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

package de.timklge.karooheadwind

import de.timklge.karooheadwind.weatherprovider.WeatherDataResponse
import de.timklge.karooheadwind.weatherprovider.openmeteo.OpenMeteoWeatherDataForLocation
import fi.nikosavola.karooext.testing.HttpResponder
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse

/**
 * Provider payload builders for tests.
 *
 * The hourly block repeats the current values on purpose. The extension interpolates the cached
 * forecast onto the wall clock, so a payload whose hours differ from `current` would make every
 * streamed field depend on the time the test runs at.
 */
object WeatherTestPayloads {
    const val CURRENT_TIME_SECONDS = 1_760_000_000L

    /**
     * The Open-Meteo `current` values the single-location payload carries, in SI units. Also the
     * values repeated for each forecast hour.
     */
    data class OpenMeteo(
        val latitude: Double = 52.513_514,
        val longitude: Double = 13.377_657,
        val windSpeed: Double = 5.0,
        val windDirection: Double = 270.0,
        val windGusts: Double = 9.0,
        val temperature: Double = 12.5,
        val relativeHumidity: Int = 70,
        val precipitation: Double = 0.0,
        val cloudCover: Int = 40,
        val surfacePressure: Double = 1010.0,
        val sealevelPressure: Double = 1015.0,
        val weatherCode: Int = 1,
        val isDay: Int = 1,
        val uvi: Double = 2.0,
    )

    /** A single-location Open-Meteo body, the shape its API returns when one coordinate is asked for. */
    fun openMeteo(openMeteo: OpenMeteo = OpenMeteo()): String =
        openMeteoLocation(openMeteo, forecastHours = FORECAST_HOURS)

    /**
     * An Open-Meteo body with [locations] entries, the shape its API returns when several
     * coordinates are asked for in one request. Extra entries are harmless: the extension zips the
     * response with the coordinates it requested and drops the rest.
     */
    fun openMeteoMultiLocation(locations: Int): String {
        require(locations > 0) { "locations must be positive, was $locations" }
        val entries =
            (0 until locations).joinToString(",") { index ->
                openMeteoLocation(
                    OpenMeteo(latitude = 52.5 + index * 0.01, longitude = 13.4 + index * 0.01),
                    forecastHours = FORECAST_HOURS,
                )
            }
        return "[$entries]"
    }

    /** An Open-Meteo body with no hourly block, so the response carries only current weather. */
    fun openMeteoWithoutForecast(openMeteo: OpenMeteo = OpenMeteo()): String =
        openMeteoLocation(openMeteo, forecastHours = 0)

    val OPEN_METEO_BERLIN: String = openMeteo()

    private const val FORECAST_HOURS = 3

    private fun openMeteoLocation(openMeteo: OpenMeteo, forecastHours: Int): String {
        val current =
            """
            {"time":$CURRENT_TIME_SECONDS,"interval":900,"temperature_2m":${openMeteo.temperature},
             "relative_humidity_2m":${openMeteo.relativeHumidity},"precipitation":${openMeteo.precipitation},
             "cloud_cover":${openMeteo.cloudCover},"surface_pressure":${openMeteo.surfacePressure},
             "pressure_msl":${openMeteo.sealevelPressure},"wind_speed_10m":${openMeteo.windSpeed},
             "wind_direction_10m":${openMeteo.windDirection},"wind_gusts_10m":${openMeteo.windGusts},
             "weather_code":${openMeteo.weatherCode},"is_day":${openMeteo.isDay},"uv_index":${openMeteo.uvi}}
            """.trimIndent()
        val hourly = if (forecastHours == 0) "null" else openMeteoHourly(openMeteo, forecastHours)
        return """
            {"latitude":${openMeteo.latitude},"longitude":${openMeteo.longitude},"timezone":"GMT",
             "elevation":38.0,"utc_offset_seconds":0,"hourly":$hourly,"current":$current}
        """.trimIndent()
    }

    private fun openMeteoHourly(openMeteo: OpenMeteo, hours: Int): String {
        fun times(offsetSeconds: Long): String =
            (0 until hours).joinToString(",") { "${CURRENT_TIME_SECONDS + it * offsetSeconds}" }
        fun doubles(value: Double): String = (0 until hours).joinToString(",") { "$value" }
        fun ints(value: Int): String = (0 until hours).joinToString(",") { "$value" }

        return """
            {"time":[${times(3_600)}],"temperature_2m":[${doubles(openMeteo.temperature)}],
             "precipitation_probability":[${ints(0)}],"precipitation":[${doubles(openMeteo.precipitation)}],
             "weather_code":[${ints(openMeteo.weatherCode)}],"wind_speed_10m":[${doubles(openMeteo.windSpeed)}],
             "wind_direction_10m":[${doubles(openMeteo.windDirection)}],
             "wind_gusts_10m":[${doubles(openMeteo.windGusts)}],"cloud_cover":[${doubles(openMeteo.cloudCover.toDouble())}],
             "surface_pressure":[${doubles(openMeteo.surfacePressure)}],
             "pressure_msl":[${doubles(openMeteo.sealevelPressure)}],"is_day":[${ints(openMeteo.isDay)}],
             "relative_humidity_2m":[${ints(openMeteo.relativeHumidity)}],"uv_index":[${doubles(openMeteo.uvi)}]}
        """.trimIndent()
    }

    /** The OpenWeatherMap `current` values the payload carries, in SI units. */
    data class OpenWeatherMap(
        val latitude: Double = 52.513_514,
        val longitude: Double = 13.377_657,
        val windSpeed: Double = 5.0,
        val windDegrees: Int = 270,
        val windGust: Double? = 9.0,
        val temperature: Double = 12.5,
        val humidity: Int = 70,
        val clouds: Int = 40,
        val pressure: Int = 1010,
        val uvi: Double = 2.0,
        val weatherId: Int = 801,
        val rain: Double? = null,
    )

    /** A single-location OpenWeatherMap onecall body. */
    fun openWeatherMap(openWeatherMap: OpenWeatherMap = OpenWeatherMap()): String {
        fun current(): String = """
            {"dt":$CURRENT_TIME_SECONDS,"sunrise":1759990000,"sunset":1760030000,
             "temp":${openWeatherMap.temperature},"feels_like":${openWeatherMap.temperature - 1},
             "pressure":${openWeatherMap.pressure},"humidity":${openWeatherMap.humidity},
             "clouds":${openWeatherMap.clouds},"visibility":10000,
             "wind_speed":${openWeatherMap.windSpeed},"wind_deg":${openWeatherMap.windDegrees},
             ${openWeatherMap.windGust?.let { "\"wind_gust\":$it," } ?: ""}
             ${openWeatherMap.rain?.let { "\"rain\":{\"1h\":$it}," } ?: ""}
             "uvi":${openWeatherMap.uvi},"weather":[${weatherEntry(openWeatherMap.weatherId)}]}
        """.trimIndent()

        fun hourlyEntry(offsetSeconds: Long): String = """
            {"dt":${CURRENT_TIME_SECONDS + offsetSeconds},"temp":${openWeatherMap.temperature},
             "pressure":${openWeatherMap.pressure},"humidity":${openWeatherMap.humidity},
             "clouds":${openWeatherMap.clouds},"wind_speed":${openWeatherMap.windSpeed},
             "wind_deg":${openWeatherMap.windDegrees},"uvi":${openWeatherMap.uvi},
             "weather":[${weatherEntry(openWeatherMap.weatherId)}]}
        """.trimIndent()

        val hourly = (0 until FORECAST_HOURS).joinToString(",") { hourlyEntry(it * 3_600L) }
        return """
            {"lat":${openWeatherMap.latitude},"lon":${openWeatherMap.longitude},
             "timezone":"Europe/Berlin","timezone_offset":7200,
             "current":${current()},"hourly":[$hourly]}
        """.trimIndent()
    }

    private fun weatherEntry(id: Int): String =
        """{"id":$id,"main":"Clouds","description":"few clouds","icon":"02d"}"""

    /**
     * A cached response covering several positions with different weather, so a test can place the
     * rider between two of them and read what the extension interpolates.
     */
    fun openMeteoResponseFor(vararg locations: OpenMeteo): WeatherDataResponse =
        WeatherDataResponse(
            provider = WeatherDataProvider.OPEN_METEO,
            data = locations.map { openMeteo ->
                jsonWithUnknownKeys
                    .decodeFromString<OpenMeteoWeatherDataForLocation>(
                        openMeteoLocation(openMeteo, FORECAST_HOURS)
                    )
                    .toWeatherDataForLocation(distanceAlongRoute = 0.0)
            },
        )

    /**
     * The [openMeteo] payload already parsed into the response the extension caches, so a test can
     * seed the cache without running a weather download.
     */
    fun openMeteoResponse(openMeteo: OpenMeteo = OpenMeteo()): WeatherDataResponse =
        WeatherDataResponse(
            provider = WeatherDataProvider.OPEN_METEO,
            data = listOf(
                jsonWithUnknownKeys
                    .decodeFromString<OpenMeteoWeatherDataForLocation>(openMeteo(openMeteo))
                    .toWeatherDataForLocation(distanceAlongRoute = 0.0)
            ),
        )

    /**
     * A 200 responder whose body is built from the request it answers. Needed when the body depends
     * on the request, e.g. one location entry per requested latitude; [HttpResponses.success] fixes
     * its body at construction.
     */
    fun responderAnsweringWith(build: (OnHttpResponse.MakeHttpRequest) -> String): HttpResponder =
        HttpResponder { request ->
            HttpResponseState.Complete(200, emptyMap(), build(request).toByteArray(), null)
        }
}
