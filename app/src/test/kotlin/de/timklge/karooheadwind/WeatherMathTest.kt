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

import de.timklge.karooheadwind.datatypes.RelativeGradeDataType
import de.timklge.karooheadwind.weatherprovider.WeatherData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Interpolation between two weather samples, which every streamed field reads through. Pure JVM. */
class WeatherMathTest {

    @Test
    fun `lerpNullable keeps a missing endpoint instead of inventing one`() {
        assertNull(lerpNullable(null, null, 0.5))
        assertEquals(10.0, lerpNullable(null, 10.0, 0.5)!!, 1e-9)
        assertEquals(0.0, lerpNullable(0.0, null, 0.5)!!, 1e-9)
        assertEquals(5.0, lerpNullable(0.0, 10.0, 0.5)!!, 1e-9)
    }

    @Test
    fun `lerpWeather interpolates the continuous fields`() {
        val start = weather(time = 1_000, temperature = 10.0, windSpeed = 4.0, uvi = 1.0)
        val end = weather(time = 2_000, temperature = 20.0, windSpeed = 8.0, uvi = 5.0)

        val mid = lerpWeather(start, end, 0.5)

        assertEquals(1_500, mid.time)
        assertEquals(15.0, mid.temperature, 1e-9)
        assertEquals(6.0, mid.windSpeed, 1e-9)
        assertEquals(3.0, mid.uvi, 1e-9)
    }

    @Test
    fun `lerpWeather takes the discrete fields from the nearer endpoint`() {
        val start = weather(weatherCode = 0, isForecast = false, isNight = false)
        val end = weather(weatherCode = 99, isForecast = true, isNight = true)

        val nearStart = lerpWeather(start, end, 0.4)
        val nearEnd = lerpWeather(start, end, 0.6)

        assertEquals(0, nearStart.weatherCode)
        assertEquals(false, nearStart.isForecast)
        assertEquals(false, nearStart.isNight)
        assertEquals(99, nearEnd.weatherCode)
        assertEquals(true, nearEnd.isForecast)
        assertEquals(true, nearEnd.isNight)
    }

    @Test
    fun `lerpWeather interpolates wind direction the short way round`() {
        val start = weather(windDirection = 350.0)
        val end = weather(windDirection = 10.0)

        assertEquals("through north, not through south", 0.0, lerpWeather(start, end, 0.5).windDirection, 1e-9)
    }

    @Test
    fun `lerpWeather passes a missing probability through as missing`() {
        val start = weather(precipitationProbability = null)
        val end = weather(precipitationProbability = null)

        assertNull(lerpWeather(start, end, 0.5).precipitationProbability)
        assertEquals(
            50.0,
            lerpWeather(weather(precipitationProbability = 0.0), weather(precipitationProbability = 100.0), 0.5)
                .precipitationProbability!!,
            1e-9,
        )
    }

    @Test
    fun `the current weather is interpolated between the surrounding forecast hours`() {
        val nowSeconds = System.currentTimeMillis() / 1_000
        val lastHour = weather(time = nowSeconds - 3_600, temperature = 10.0, windSpeed = 4.0)
        val nextHour = weather(time = nowSeconds + 3_600, temperature = 20.0, windSpeed = 8.0, isForecast = true)

        val current = lerpWeatherTime(listOf(lastHour, nextHour), weather())

        assertEquals("halfway between the two hours", 15.0, current.temperature, 0.1)
        assertEquals(6.0, current.windSpeed, 0.1)
    }

    @Test
    fun `without a later forecast hour the newest one is used as it stands`() {
        val nowSeconds = System.currentTimeMillis() / 1_000
        val older = weather(time = nowSeconds - 7_200, temperature = 10.0)
        val newest = weather(time = nowSeconds - 3_600, temperature = 21.0)

        assertEquals(
            21.0,
            lerpWeatherTime(listOf(older, newest), weather(temperature = 5.0)).temperature,
            1e-9,
        )
        assertEquals(
            "with no forecast hours at all the cached current block is used",
            5.0,
            lerpWeatherTime(null, weather(temperature = 5.0)).temperature,
            1e-9,
        )
    }

    @Test
    fun `a rider with no speed and no wind sees only the road grade`() {
        val grade = RelativeGradeDataType.estimateRelativeGrade(
            actualGrade = 0.04,
            riderSpeed = 0.0,
            windSpeed = 0.0,
            windDirectionDegrees = 0.0,
            totalMass = 80.0,
        )

        assertEquals(0.04, grade, 1e-9)
    }

    @Test
    fun `a headwind reads as a steeper road and a tailwind as a flatter one`() {
        val headwind = RelativeGradeDataType.estimateRelativeGrade(0.0, 8.0, 5.0, 0.0, 80.0)
        val tailwind = RelativeGradeDataType.estimateRelativeGrade(0.0, 8.0, 5.0, 180.0, 80.0)

        // 5 m/s against an 8 m/s rider is about 15% more drag than still air, so a 3.2% grade.
        assertEquals(0.032, headwind, 0.002)
        // Downwind the effective air speed is 3 m/s, less than half the drag: about -1.7%.
        assertEquals(-0.017, tailwind, 0.002)
        assertTrue("a crosswind sits between the two", headwind > 0.0 && tailwind < 0.0)
    }

    private fun weather(
        time: Long = 1_760_000_000,
        temperature: Double = 12.5,
        relativeHumidity: Int = 70,
        precipitation: Double = 0.0,
        precipitationProbability: Double? = null,
        cloudCover: Double = 40.0,
        sealevelPressure: Double = 1_015.0,
        surfacePressure: Double = 1_010.0,
        windSpeed: Double = 5.0,
        windDirection: Double = 270.0,
        windGusts: Double = 9.0,
        weatherCode: Int = 1,
        isForecast: Boolean = false,
        isNight: Boolean = false,
        uvi: Double = 2.0,
    ) = WeatherData(
        time = time,
        temperature = temperature,
        relativeHumidity = relativeHumidity,
        precipitation = precipitation,
        precipitationProbability = precipitationProbability,
        cloudCover = cloudCover,
        sealevelPressure = sealevelPressure,
        surfacePressure = surfacePressure,
        windSpeed = windSpeed,
        windDirection = windDirection,
        windGusts = windGusts,
        weatherCode = weatherCode,
        isForecast = isForecast,
        isNight = isNight,
        uvi = uvi,
    )
}
