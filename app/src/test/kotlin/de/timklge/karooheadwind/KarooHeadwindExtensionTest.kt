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
import fi.nikosavola.karooext.testing.HttpResponses
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KarooHeadwindExtensionTest {
    @get:Rule val karoo = FakeKarooRule()

    @Before
    fun setUp() = runBlocking {
        // preferencesDataStore caches its instance across Robolectric tests, so start clean.
        karoo.app.dataStore.edit { it.clear() }
        saveSettings(karoo.app, HeadwindSettings(welcomeDialogAccepted = true))
        // The weather loop also waits on route streams such as distance to destination.
        karoo.system.initialStreamState = StreamState.NotAvailable
        karoo.system.setLocation(52.52, 13.41)
    }

    @Test
    fun `open-meteo response reaches the wind speed field`() {
        karoo.system.responder = HttpResponses.success(OPEN_METEO_BERLIN.toByteArray())

        val stream = karoo.host<KarooHeadwindExtension>().startStream("windSpeed")

        val state = stream.await(20_000) { it is StreamState.Streaming } as StreamState.Streaming
        // 5 m/s in the metric default of km/h
        assertEquals(18.0, state.dataPoint.values.getValue(DataType.Field.SINGLE), 0.001)
        val request = karoo.system.httpRequests.single()
        assertTrue(request.url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=52.5"))
        assertEquals("karoo-headwind", request.headers["User-Agent"])
    }

    @Test
    fun `failed weather request is recorded in stats`() {
        karoo.system.responder = HttpResponses.status(503)

        karoo.host<KarooHeadwindExtension>()

        val stats = karoo.awaitValue(timeoutMs = 20_000) {
            runBlocking { karoo.app.streamStats().first() }.takeIf { it.failedWeatherRequest != null }
        }
        assertNull(stats.lastSuccessfulWeatherRequest)
    }

    companion object {
        val OPEN_METEO_BERLIN = """
            {"latitude":52.52,"longitude":13.41,"timezone":"GMT","elevation":38.0,"utc_offset_seconds":0,"hourly":null,
             "current":{"time":1760000000,"interval":900,"temperature_2m":12.5,"relative_humidity_2m":70,
               "precipitation":0.0,"cloud_cover":40,"surface_pressure":1010.0,"pressure_msl":1015.0,
               "wind_speed_10m":5.0,"wind_direction_10m":270.0,"wind_gusts_10m":9.0,"weather_code":1,
               "is_day":1,"uv_index":2.0}}
        """.trimIndent()
    }
}
