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

import androidx.datastore.preferences.core.edit
import de.timklge.karooheadwind.datatypes.RelativeElevationGainDataType
import de.timklge.karooheadwind.WeatherTestPayloads.OpenMeteo
import de.timklge.karooheadwind.WeatherTestPayloads.openMeteo
import de.timklge.karooheadwind.WeatherTestPayloads.openMeteoMultiLocation
import de.timklge.karooheadwind.WeatherTestPayloads.openMeteoResponse
import de.timklge.karooheadwind.WeatherTestPayloads.openWeatherMap
import de.timklge.karooheadwind.WeatherTestPayloads.responderAnsweringWith
import de.timklge.karooheadwind.datatypes.RelativeGradeDataType
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.FakeKarooSystem
import fi.nikosavola.karooext.testing.HttpResponses
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The extension end to end: the real service, the real data types and the real provider code run
 * against the fake Karoo system, so a test drives a location and an HTTP response and reads what
 * lands in a data type stream.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KarooHeadwindExtensionTest {
    @get:Rule
    val karoo = FakeKarooRule()

    @Before
    fun setUp(): Unit = runBlocking {
        // preferencesDataStore caches its instance across Robolectric tests, so start clean.
        karoo.app.dataStore.edit { it.clear() }
        // Several data types combine streams the test does not drive, so give them a state.
        karoo.system.initialStreamState = StreamState.NotAvailable
        karoo.system.setLocation(BERLIN_LAT, BERLIN_LON, orientation = INTO_THE_WIND_BEARING)
    }

    @Test
    fun `open-meteo response reaches the wind speed field`() {
        answerOpenMeteo()

        val host = startExtension()

        // 5 m/s in the metric default of km/h
        assertEquals(18.0, streamValue(host, "windSpeed"), 0.001)

        val request = karoo.system.httpRequests.single()
        assertTrue(
            "the position is rounded to the default 3 km grid: ${request.url}",
            request.url.contains("latitude=52.513514"),
        )
        assertEquals("karoo-headwind", request.headers["User-Agent"])
        assertTrue("the request asks for metres per second", request.url.contains("wind_speed_unit=ms"))
        assertTrue("the request asks for a 12 hour forecast", request.url.contains("forecast_hours=12"))
        assertTrue("the request asks for the current block", request.url.contains("current="))
    }

    @Test
    fun `a chosen wind unit overrides the metric default`() {
        answerOpenMeteo()

        val host = startExtension(settings = acceptedSettings().copy(windUnit = WindUnit.KNOTS))

        assertEquals(9.7192225, streamValue(host, "windSpeed"), 0.001)
    }

    @Test
    fun `metres per second leaves the wind speed unconverted`() {
        answerOpenMeteo()

        val host = startExtension(settings = acceptedSettings().copy(windUnit = WindUnit.METERS_PER_SECOND))

        assertEquals(5.0, streamValue(host, "windSpeed"), 0.001)
    }

    @Test
    fun `an imperial profile defaults the wind speed to miles per hour`() {
        answerOpenMeteo()

        val host = startExtension(profile = FakeKarooSystem.imperialProfile())

        assertEquals(11.1846815, streamValue(host, "windSpeed"), 0.001)
    }

    @Test
    fun `an imperial profile converts rainfall`() {
        answerOpenMeteo(openMeteo(OpenMeteo(precipitation = 2.0)))

        val host = startExtension(profile = FakeKarooSystem.imperialProfile())

        assertEquals(2.0 / 25.4, streamValue(host, "precipitation"), 1e-6)
    }

    @Test
    fun `the downloaded current conditions drive the basic weather fields`() {
        answerOpenMeteo()

        val host = startExtension()

        val expected = mapOf(
            "temperature" to 12.5,
            "relativeHumidity" to 70.0,
            "cloudCover" to 40.0,
            "precipitation" to 0.0,
            "uvi" to 2.0,
            "windGusts" to 32.4,
            "surfacePressure" to 1_010.0,
            "sealevelPressure" to 1_015.0,
            "windDirection" to 270.0,
        )

        val actual = expected.mapValues { (typeId, _) -> streamValue(host, typeId) }

        assertEquals(expected, actual)
    }

    @Test
    fun `a rider heading straight into the wind reads a full headwind speed`() {
        answerOpenMeteo()

        val host = startExtension()

        // Wind from 270, rider heading 270: the whole 5 m/s is against the rider.
        assertEquals(18.0, streamValue(host, "headwindSpeed"), 0.001)
    }

    @Test
    fun `a rider heading downwind reads a negative headwind speed`() {
        karoo.system.setLocation(BERLIN_LAT, BERLIN_LON, orientation = DOWNWIND_BEARING)
        answerOpenMeteo()

        val host = startExtension()

        assertEquals(-18.0, streamValue(host, "headwindSpeed"), 0.001)
    }

    @Test
    fun `the headwind direction field streams the wind angle relative to the rider`() {
        answerOpenMeteo()

        val host = startExtension()

        // The field reports an error code until the weather arrives, so wait for the real angle.
        assertEquals(180.0, awaitReportedValue(host, "headwind") { it >= 0.0 }, 0.001)
    }

    @Test
    fun `the headwind direction field reports an error code before setup`() {
        val host = startExtension(settings = HeadwindSettings(welcomeDialogAccepted = false))

        assertEquals(-3.0, streamValue(host, "headwind"), 0.001)
    }

    @Test
    fun `the headwind direction field reports no gps when the fix carries no bearing`() {
        karoo.system.setLocation(BERLIN_LAT, BERLIN_LON, orientation = null)
        val host = startExtension()

        assertEquals(-1.0, streamValue(host, "headwind"), 0.001)
    }

    @Test
    fun `headwind speed stays unavailable instead of reporting a number before setup`() {
        val host = startExtension(settings = HeadwindSettings(welcomeDialogAccepted = false))
        val stream = host.startStream("headwindSpeed")

        assertEquals(StreamState.NotAvailable, stream.await(TIMEOUT_MS) { it is StreamState.NotAvailable })

        // A zero or stale number here would show a rider a wind speed that was never downloaded.
        assertThrows(IllegalStateException::class.java) {
            stream.await(NEGATIVE_WAIT_MS) { it is StreamState.Streaming }
        }
    }

    @Test
    fun `the request rounds the position to the configured grid`() {
        answerOpenMeteo()

        val host = startExtension(
            settings = acceptedSettings().copy(roundLocationTo = RoundLocationSetting.KM_1),
        )
        streamValue(host, "windSpeed")

        val request = karoo.system.httpRequests.single()
        assertTrue(
            "1 km rounding keeps more of the position than the 3 km default: ${request.url}",
            request.url.contains("latitude=52.522523"),
        )
    }

    @Test
    fun `an active route makes one request cover several positions along it`() {
        karoo.system.setRoute(routePoints())
        answerOpenMeteo(openMeteoMultiLocation(MULTI_LOCATION_ENTRIES))

        val host = startExtension(settings = acceptedSettings().copy(forecastedKmPerHour = 4))

        assertEquals("the field still follows the first position", 18.0, streamValue(host, "windSpeed"), 0.001)

        val latitudes = karoo.system.httpRequests.last().let(::requestedLatitudes)
        assertTrue("the route is split into several forecast points, got $latitudes", latitudes.size >= 3)
        assertTrue(
            "the positions are ordered the way the rider will reach them: $latitudes",
            latitudes.zipWithNext().all { (first, second) -> second > first },
        )
        // The first point is the rider's own rounded position, which can round just short of the
        // route start; every later one lies on the route.
        assertTrue(
            "the positions run from the rider to the end of the route: $latitudes",
            latitudes.first().toDouble() in (BERLIN_LAT - ROUNDING_SLACK)..BERLIN_LAT &&
                kotlin.math.abs(latitudes.last().toDouble() - routeEndLatitude()) < 0.001,
        )

        val cached = awaitCachedForecastCount { it == latitudes.size }
        assertEquals("the cache holds one forecast for every position asked for", latitudes.size, cached)
    }

    @Test
    fun `a cached forecast is served while the weather request fails`() {
        runBlocking { saveForecastRecord(karoo.app, openMeteoResponse(), HeadwindStats()) }
        karoo.system.responder = HttpResponses.failure("offline")

        val host = startExtension()

        assertEquals(18.0, streamValue(host, "windSpeed"), 0.001)
        val stats = awaitStats { it.failedWeatherRequest != null }
        assertNotNull("the failed download is still reported", stats.lastWeatherError)
    }

    @Test
    fun `failed weather request is recorded in stats`() {
        karoo.system.responder = HttpResponses.status(503)

        startExtension()

        val stats = awaitStats { it.failedWeatherRequest != null }
        assertNull(stats.lastSuccessfulWeatherRequest)
        assertTrue(stats.lastWeatherError!!.contains("503"))
    }

    @Test
    fun `a successful request records the rounded position it was fetched for`() {
        answerOpenMeteo()

        val host = startExtension()
        streamValue(host, "windSpeed")

        val stats = awaitStats { it.lastSuccessfulWeatherRequest != null }
        assertEquals(WeatherDataProvider.OPEN_METEO, stats.lastSuccessfulWeatherProvider)
        assertEquals("the recorded position is the one the request used", 52.513_514, stats.lastSuccessfulWeatherPosition!!.lat, 1e-6)
        assertEquals(13.405_405, stats.lastSuccessfulWeatherPosition!!.lon, 1e-6)
        assertNull("a success clears the previous error", stats.lastWeatherError)
    }

    @Test
    fun `the selected provider is the one that is called`() {
        answerOpenMeteo(openWeatherMap())

        val host = startExtension(
            settings = acceptedSettings().copy(
                weatherProvider = WeatherDataProvider.OPEN_WEATHER_MAP,
                openWeatherMapApiKey = "test-key",
            ),
        )

        assertEquals(18.0, streamValue(host, "windSpeed"), 0.001)
        val request = karoo.system.httpRequests.single()
        assertTrue(request.url.startsWith("https://api.openweathermap.org/data/3.0/onecall?lat="))
        assertTrue(request.url.contains("appid=test-key"))
        assertTrue(request.url.contains("units=metric"))
    }

    @Test
    fun `an invalid openweathermap key is reported in stats`() {
        karoo.system.responder = HttpResponses.status(401)

        startExtension(
            settings = acceptedSettings().copy(
                weatherProvider = WeatherDataProvider.OPEN_WEATHER_MAP,
                openWeatherMapApiKey = "expired",
            ),
        )

        val stats = awaitStats { it.failedWeatherRequest != null }
        assertEquals("OpenWeatherMap API key is invalid or expired", stats.lastWeatherError)
    }

    @Test
    fun `a field between two forecast positions reports their distance weighted mix`() {
        // The rider is a quarter of the way from the first position to the second, so the nearer
        // position has to carry three quarters of the value: 4 m/s and 8 m/s mix to 5 m/s.
        val nearWindSpeed = 4.0
        val farWindSpeed = 8.0
        val riderLat = 52.513_514
        val riderLon = 13.405_405
        runBlocking {
            saveForecastRecord(
                karoo.app,
                WeatherTestPayloads.openMeteoResponseFor(
                    OpenMeteo(latitude = riderLat, longitude = riderLon - 0.01, windSpeed = nearWindSpeed),
                    OpenMeteo(latitude = riderLat, longitude = riderLon + 0.03, windSpeed = farWindSpeed),
                ),
                HeadwindStats(),
            )
        }
        // The download fails, so the extension cannot replace the two-position cache with its own
        // single-position one.
        karoo.system.responder = HttpResponses.failure("offline")

        val host = startExtension()

        // The rider is a quarter of the way to the far position, so the nearer one carries three
        // quarters of the weight. Until the rider's own position is known the field reports the
        // first cached position alone, so wait past that value.
        val blended = awaitReportedValue(host, "windSpeed") {
            kotlin.math.abs(it - nearWindSpeed * KMH_PER_MS) > 0.1
        }

        assertEquals((nearWindSpeed + (farWindSpeed - nearWindSpeed) * 0.25) * KMH_PER_MS, blended, 0.05)
    }

    @Test
    fun `the relative grade field turns a headwind into an equivalent climb`() {
        karoo.system.setDataPoint(DataType.Type.SPEED, 8.0)
        karoo.system.setDataPoint(DataType.Type.ELEVATION_GRADE, 2.0)
        answerOpenMeteo()

        // An 81 kg rider on a 9 kg bike is the 90 kg the estimate is documented with.
        val host = startExtension(profile = FakeKarooSystem.metricProfile().copy(weight = 81f))

        assertEquals(4.915, streamValue(host, "relativeGrade"), 0.02)
    }

    @Test
    fun `impossible rider inputs leave the relative grade undefined`() {
        assertTrue(
            "zero mass is not a rider",
            RelativeGradeDataType.estimateRelativeGrade(0.0, 8.0, 5.0, 0.0, 0.0).isNaN(),
        )
        assertTrue(
            "negative wind speed is not physical",
            RelativeGradeDataType.estimateRelativeGrade(0.0, 8.0, -1.0, 0.0, 80.0).isNaN(),
        )
    }

    @Test
    fun `relative elevation gain only accumulates into a headwind climb`() {
        val type = RelativeElevationGainDataType(KarooSystemService(karoo.app), karoo.app)

        assertEquals("no wind grade difference adds nothing", 5.0, type.updateAccumulatedWindElevation(5.0, 0.02, 0.02, 8.0, 10.0), 1e-9)
        assertEquals("a headwind climb adds distance times the grade difference", 2.4, type.updateAccumulatedWindElevation(0.0, 0.05, 0.02, 8.0, 10.0), 1e-9)
        assertEquals("a tailwind never subtracts", 5.0, type.updateAccumulatedWindElevation(5.0, 0.0, 0.02, 8.0, 10.0), 1e-9)
        assertEquals("a downhill is left alone", 5.0, type.updateAccumulatedWindElevation(5.0, -0.01, -0.02, 8.0, 10.0), 1e-9)
        assertEquals("a zero interval adds nothing", 5.0, type.updateAccumulatedWindElevation(5.0, 0.05, 0.02, 8.0, 0.0), 1e-9)
    }

    /** Answers every weather download with [payload], which each test builds as it needs. */
    private fun answerOpenMeteo(payload: String = openMeteo()) {
        karoo.system.responder = responderAnsweringWith { payload }
    }

    private fun acceptedSettings() = HeadwindSettings(
        welcomeDialogAccepted = true,
        roundLocationTo = RoundLocationSetting.KM_3,
    )

    private fun startExtension(
        settings: HeadwindSettings = acceptedSettings(),
        profile: UserProfile? = null,
    ): FakeKarooHost {
        runBlocking { saveSettings(karoo.app, settings) }
        profile?.let(karoo.system::setUserProfile)
        return karoo.host<KarooHeadwindExtension>()
    }

    private fun streamValue(host: FakeKarooHost, typeId: String): Double =
        awaitReportedValue(host, typeId) { true }

    /**
     * The value the field settles on, for fields that report an error code or
     * [StreamState.NotAvailable] before their inputs are ready.
     */
    private fun awaitReportedValue(
        host: FakeKarooHost,
        typeId: String,
        predicate: (Double) -> Boolean,
    ): Double {
        val stream = host.startStream(typeId)
        val state =
            stream.await(TIMEOUT_MS) { state ->
                state is StreamState.Streaming &&
                    state.dataPoint.values[DataType.Field.SINGLE]?.let(predicate) == true
            } as StreamState.Streaming
        return state.dataPoint.values.getValue(DataType.Field.SINGLE)
    }

    private fun awaitStats(predicate: (HeadwindStats) -> Boolean): HeadwindStats =
        karoo.awaitValue(TIMEOUT_MS) {
            runBlocking { karoo.app.streamStats().first() }.takeIf(predicate)
        }

    /** How many positions the cached forecast covers, once it matches [predicate]. */
    private fun awaitCachedForecastCount(predicate: (Int) -> Boolean): Int =
        karoo.awaitValue(TIMEOUT_MS) {
            runBlocking { karoo.app.streamForecastRecord().first() }
                .response?.data?.size
                ?.takeIf(predicate)
        }

    private fun requestedLatitudes(request: OnHttpResponse.MakeHttpRequest): List<String> =
        request.url.substringAfter("latitude=").substringBefore("&").split(",")

    private fun routePoints(): List<Pair<Double, Double>> =
        (0..30).map { index -> BERLIN_LAT + index * 0.005 to BERLIN_LON + index * 0.008 }

    private fun routeEndLatitude(): Double = routePoints().last().first

    companion object {
        private const val TIMEOUT_MS = 20_000L

        /** Long enough for the extension to have emitted anything it was going to. */
        private const val NEGATIVE_WAIT_MS = 1_000L

        /** A rounded position can land this far outside the first point of the route it came from. */
        private const val ROUNDING_SLACK = 0.01

        /** Metres per second to kilometres per hour, the extension's metric wind unit. */
        private const val KMH_PER_MS = 3.6
        private const val BERLIN_LAT = 52.52
        private const val BERLIN_LON = 13.41

        /** A rider heading that points straight into the 270 degree wind. */
        private const val INTO_THE_WIND_BEARING = 270.0
        private const val DOWNWIND_BEARING = 90.0

        /** More entries than the extension can ask for, so the response never runs short. */
        private const val MULTI_LOCATION_ENTRIES = 12
    }
}
