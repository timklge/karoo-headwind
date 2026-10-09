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

package de.timklge.karooheadwind.aidl

import android.content.Intent
import androidx.datastore.preferences.core.edit
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.HeadwindStats
import de.timklge.karooheadwind.IHeadwindCallback
import de.timklge.karooheadwind.IHeadwindService
import de.timklge.karooheadwind.WeatherDataProvider
import de.timklge.karooheadwind.WeatherTestPayloads
import de.timklge.karooheadwind.WindUnit
import de.timklge.karooheadwind.dataStore
import de.timklge.karooheadwind.aidl.model.HeadwindSnapshot
import de.timklge.karooheadwind.aidl.model.headwindSnapshotJson
import de.timklge.karooheadwind.saveForecastRecord
import de.timklge.karooheadwind.saveSettings
import de.timklge.karooheadwind.saveStats
import fi.nikosavola.karooext.testing.FakeKarooSystem
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * The AIDL snapshot service, bound the way another extension binds it. The service reads only what
 * the extension cached, so a test seeds the cache and reads the JSON a client is handed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HeadwindServiceTest {
    @get:Rule
    val karoo = FakeKarooRule()

    private var controller: ServiceController<HeadwindService>? = null

    @Before
    fun setUp(): Unit = runBlocking {
        karoo.app.dataStore.edit { it.clear() }
    }

    @After
    fun tearDown() {
        controller?.destroy()
        controller = null
    }

    @Test
    fun `a bound client receives the cached forecast as a snapshot`() {
        seedCache(
            HeadwindStats(
                lastSuccessfulWeatherRequest = 1_760_000_000_000L,
                lastSuccessfulWeatherProvider = WeatherDataProvider.OPEN_METEO,
            )
        )

        val snapshot = awaitFirstSnapshot()

        assertEquals(1, snapshot.version)
        assertEquals(WeatherDataProvider.OPEN_METEO, snapshot.provider)
        assertEquals(WindUnit.KILOMETERS_PER_HOUR, snapshot.windUnit)
        assertEquals("timestamps are seconds, not milliseconds", 1_760_000_000L, snapshot.lastSuccessfulFetchEpochSeconds)
        assertNull(snapshot.error)

        val point = snapshot.forecast!!.single()
        assertEquals(52.513_514, point.lat, 1e-6)
        assertEquals(0.0, point.distanceAlongRoute!!, 1e-9)
        assertEquals(3, point.hourly.size)
    }

    @Test
    fun `snapshot values stay si whatever the display unit is`() {
        seedCache(
            HeadwindStats(lastSuccessfulWeatherProvider = WeatherDataProvider.OPEN_METEO),
            settings = HeadwindSettings(welcomeDialogAccepted = true, windUnit = WindUnit.KNOTS),
        )

        val snapshot = awaitFirstSnapshot()

        assertEquals("the chosen unit is reported as a preference", WindUnit.KNOTS, snapshot.windUnit)
        assertEquals("the wind speed is not converted into it", 5.0, snapshot.forecast!!.single().current.windSpeed, 1e-9)
    }

    @Test
    fun `a client that registers late still gets the latest snapshot`() {
        seedCache(HeadwindStats(lastSuccessfulWeatherProvider = WeatherDataProvider.OPEN_METEO))
        val service = bindService()
        val first = RecordingCallback()
        service.registerCallback(first)
        awaitSnapshot(first)

        val second = RecordingCallback()
        service.registerCallback(second)

        assertEquals(1, awaitSnapshot(second).version)
    }

    @Test
    fun `the snapshot reports the last failed download`() {
        runBlocking {
            saveStats(
                karoo.app,
                HeadwindStats(
                    failedWeatherRequest = 1_760_000_500_000L,
                    lastWeatherError = "OpenMeteo API request failed with status code 503",
                ),
            )
        }

        val snapshot = awaitFirstSnapshot()

        assertEquals("OpenMeteo API request failed with status code 503", snapshot.error)
        assertEquals(1_760_000_500L, snapshot.lastFailedFetchEpochSeconds)
        assertNull("nothing was ever fetched successfully", snapshot.lastSuccessfulFetchEpochSeconds)
        assertNull(snapshot.forecast)
    }

    @Test
    fun `an empty cache still produces a well-formed snapshot`() {
        val snapshot = awaitFirstSnapshot()

        assertEquals(1, snapshot.version)
        assertNull(snapshot.forecast)
        assertNull(snapshot.error)
        assertNull(snapshot.provider)
        assertNull(snapshot.lastSuccessfulFetchEpochSeconds)
        assertEquals("a client can still read the display preference", WindUnit.KILOMETERS_PER_HOUR, snapshot.windUnit)
    }

    private fun seedCache(stats: HeadwindStats, settings: HeadwindSettings = HeadwindSettings(welcomeDialogAccepted = true)) =
        runBlocking {
            saveSettings(karoo.app, settings)
            saveForecastRecord(karoo.app, WeatherTestPayloads.openMeteoResponse(), stats)
            // The snapshot's wind unit comes from the rider's profile, so state it rather than
            // leaning on whatever profile the fake happens to default to.
            karoo.system.setUserProfile(FakeKarooSystem.metricProfile())
        }

    private fun bindService(): IHeadwindService {
        val built = Robolectric.buildService(HeadwindService::class.java).create()
        controller = built
        return checkNotNull(IHeadwindService.Stub.asInterface(built.get().onBind(Intent()))) {
            "HeadwindService did not return its binder"
        }
    }

    /** Binds the service, registers one client and returns the first snapshot it is handed. */
    private fun awaitFirstSnapshot(): HeadwindSnapshot {
        val callback = RecordingCallback()
        bindService().registerCallback(callback)
        return awaitSnapshot(callback)
    }

    private fun awaitSnapshot(callback: RecordingCallback): HeadwindSnapshot =
        headwindSnapshotJson.decodeFromString<HeadwindSnapshot>(
            karoo.awaitValue { callback.snapshots.lastOrNull() }
        )

    private class RecordingCallback : IHeadwindCallback.Stub() {
        val snapshots = CopyOnWriteArrayList<String>()

        override fun onSnapshot(snapshotJson: String?) {
            snapshotJson?.let(snapshots::add)
        }
    }
}
