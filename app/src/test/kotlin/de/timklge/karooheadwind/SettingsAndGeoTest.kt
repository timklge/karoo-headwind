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

import de.timklge.karooheadwind.datatypes.GpsCoordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings defaults, their persisted JSON form, and the coordinate helpers. Pure JVM. */
class SettingsAndGeoTest {

    @Test
    fun `settings written by an older version still read, with new fields defaulted`() {
        // The shape an earlier release persisted: some fields, no others, and a key a later
        // version dropped. decodeStats/streamSettings in DataStore.kt must survive all of it.
        val olderStoredJson = """{"welcomeDialogAccepted":true,"forecastedKmPerHour":30,"removedSetting":7}"""

        val settings = jsonWithUnknownKeys.decodeFromString<HeadwindSettings>(olderStoredJson)

        assertEquals("a stored field keeps its value", true, settings.welcomeDialogAccepted)
        assertEquals(30, settings.forecastedKmPerHour)
        assertEquals("a field the old version never wrote takes its default", RefreshRate.STANDARD, settings.refreshRate)
        assertNull("an omitted unit means the profile decides", settings.windUnit)
    }

    @Test
    fun `the fallbacks used for missing or unreadable storage decode`() {
        // DataStore.kt substitutes these literals when the stored JSON is absent or corrupt, so a
        // bad literal would leave the extension unable to read its own settings at all.
        assertNull(
            jsonWithUnknownKeys.decodeFromString<HeadwindStats>(HeadwindStats.defaultStats)
                .lastSuccessfulWeatherRequest
        )
        assertEquals(
            false,
            jsonWithUnknownKeys.decodeFromString<HeadwindSettings>(HeadwindSettings.defaultSettings)
                .welcomeDialogAccepted,
        )
    }

    @Test
    fun `the forecast distance per hour follows the profile unit`() {
        val settings = HeadwindSettings(forecastedKmPerHour = 20, forecastedMilesPerHour = 12)

        assertEquals(20_000, settings.getForecastMetersPerHour(isImperial = false))
        assertEquals(12 * 1_609, settings.getForecastMetersPerHour(isImperial = true))
    }

    @Test
    fun `the chosen wind unit wins over the profile default`() {
        val explicit = HeadwindSettings(windUnit = WindUnit.METERS_PER_SECOND)

        assertEquals(WindUnit.METERS_PER_SECOND, explicit.getWindUnit(isImperial = true))
        assertEquals(WindUnit.METERS_PER_SECOND, explicit.getWindUnit(isImperial = false))
    }

    @Test
    fun `without a chosen wind unit the profile decides`() {
        val settings = HeadwindSettings(windUnit = null)

        assertEquals(WindUnit.MILES_PER_HOUR, settings.getWindUnit(isImperial = true))
        assertEquals(WindUnit.KILOMETERS_PER_HOUR, settings.getWindUnit(isImperial = false))
        assertEquals(WindUnit.MILES_PER_HOUR, defaultWindUnit(isImperial = true))
        assertEquals(WindUnit.KILOMETERS_PER_HOUR, defaultWindUnit(isImperial = false))
    }

    @Test
    fun `refresh rate descriptions name the interval that rate actually uses`() {
        // The label is what the rider picks by, so it has to track the millisecond value beside it.
        RefreshRate.entries.forEach { rate ->
            assertTrue(
                "${rate.name} must state its Karoo 2 interval of ${rate.k2Ms} ms",
                rate.getDescription(isOnK2 = true).contains("${rate.k2Ms / 1_000}s"),
            )
        }
        RefreshRate.entries.filterNot { it == RefreshRate.FAST }.forEach { rate ->
            assertTrue(
                "${rate.name} must state its Karoo 3 interval of ${rate.k3Ms} ms",
                rate.getDescription(isOnK2 = false).contains("${rate.k3Ms / 1_000}s"),
            )
        }
        assertEquals("Fastest is the Karoo 3 top rate, with no slower setting below it", "Fastest", RefreshRate.FAST.getDescription(isOnK2 = false))
    }

    @Test
    fun `wind unit ids stay stable because saved settings refer to them by id`() {
        assertEquals(listOf("kmh", "ms", "mph", "kn"), WindUnit.entries.map { it.id })
    }

    @Test
    fun `rounding a coordinate snaps it to the chosen grid and keeps the rest`() {
        val original = GpsCoordinates(52.516_4, 13.377_7, bearing = 187.5, distanceAlongRoute = 900.0)

        val threeKm = original.round(3.0)
        val fiveKm = original.round(5.0)

        assertEquals("3 km of latitude is about 0.027 degrees", 52.513_514, threeKm.lat, 1e-6)
        assertEquals(13.378_378, threeKm.lon, 1e-6)
        assertEquals("5 km of latitude is about 0.045 degrees", 52.522_523, fiveKm.lat, 1e-6)
        assertEquals("the rider bearing is not touched by rounding", 187.5, threeKm.bearing!!, 0.0)
        assertEquals("the distance along the route is not touched either", 900.0, threeKm.distanceAlongRoute!!, 0.0)
    }

    @Test
    fun `distance to another coordinate is the haversine distance`() {
        val berlin = GpsCoordinates(52.52, 13.41)

        assertEquals(0.0, berlin.distanceTo(berlin), 1e-9)
        assertTrue(
            "0.01 degrees of longitude at Berlin is roughly 0.68 km",
            berlin.distanceTo(GpsCoordinates(52.52, 13.42)) in 0.66..0.69,
        )
        assertEquals(
            "distance is symmetric",
            berlin.distanceTo(GpsCoordinates(52.60, 13.60)),
            GpsCoordinates(52.60, 13.60).distanceTo(berlin),
            1e-9,
        )
        assertTrue(
            "a degree of latitude is about 111 km",
            berlin.distanceTo(GpsCoordinates(53.52, 13.41)) in 110.0..112.0,
        )
    }
}
