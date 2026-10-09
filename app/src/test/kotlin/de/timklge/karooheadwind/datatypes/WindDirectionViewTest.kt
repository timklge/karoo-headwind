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

package de.timklge.karooheadwind.datatypes

import androidx.datastore.preferences.core.edit
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.dataStore
import fi.nikosavola.karooext.testing.inflate
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import fi.nikosavola.karooext.testing.texts
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.ViewConfig
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The rendered wind direction field, read back from the frames the extension sends.
 *
 * The SDK view emitter drops a frame within 900 ms of the last one and reads the real clock for it,
 * so this class virtualizes the SDK's own package and moves the clock past the throttle before the
 * first frame. Without that the first frame is dropped and the wait only times out.
 *
 * That 900 ms window is an internal constant of karoo-ext 1.1.9, not a documented API, so an SDK
 * upgrade can invalidate the `instrumentedPackages` trick here. If these tests start timing out,
 * check the throttle in `io.hammerhead.karooext.internal.ViewEmitter` first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], instrumentedPackages = ["io.hammerhead.karooext.internal"])
class WindDirectionViewTest {
    @get:Rule
    val karoo = FakeKarooRule()

    @Before
    fun setUp(): Unit = runBlocking {
        // preferencesDataStore is shared across Robolectric tests, so a setting from another test
        // would otherwise change this field's refresh rate.
        karoo.app.dataStore.edit { it.clear() }
    }

    @Test
    fun `the field draws the cardinal name of the wind angle`() {
        RobolectricPump.advanceBy(1.seconds)

        val host = karoo.host<KarooHeadwindExtension>()
        karoo.system.setDataPoint(windDirectionId(), 270.0)
        karoo.system.showPage(listOf(windDirectionId()))

        val config = ViewConfig(gridSize = 30 to 15, viewSize = 240 to 120, textSize = 30)
        val view = host.startView("windDirection", config)

        assertEquals(listOf("W"), view.awaitFrame(TIMEOUT_MS).inflate(karoo.app, config).texts())
    }

    @Test
    fun `a new wind angle redraws the field`() {
        RobolectricPump.advanceBy(1.seconds)

        val host = karoo.host<KarooHeadwindExtension>()
        karoo.system.setDataPoint(windDirectionId(), 270.0)
        karoo.system.showPage(listOf(windDirectionId()))

        val config = ViewConfig(gridSize = 30 to 15, viewSize = 240 to 120, textSize = 30)
        val view = host.startView("windDirection", config)
        assertEquals(listOf("W"), view.awaitFrame(TIMEOUT_MS).inflate(karoo.app, config).texts())

        val before = view.items.size
        RobolectricPump.advanceBy(1.seconds)
        karoo.system.setDataPoint(windDirectionId(), 45.0)

        assertEquals(
            listOf("NE"),
            view.awaitFrame(TIMEOUT_MS, after = before).inflate(karoo.app, config).texts(),
        )
    }

    private fun windDirectionId(): String = DataType.dataTypeId("karoo-headwind", "windDirection")

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
