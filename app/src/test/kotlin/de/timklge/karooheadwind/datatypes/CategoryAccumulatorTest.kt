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

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryAccumulatorTest {
    @Test
    fun testEmptyAccumulatorReturnsNullSpeeds() {
        val stats = CategoryAccumulator().toStats(totalSamples = 0)

        assertEquals(0.0, stats.timePercent)
        assertNull(stats.avgRideSpeed)
        assertNull(stats.maxRideSpeed)
        assertNull(stats.avgWindSpeed)
        assertNull(stats.maxWindSpeed)
    }

    @Test
    fun testStatsAreRelativeToTotalSamplesAndIgnoreMissingRideSpeed() {
        val accumulator = CategoryAccumulator()
        accumulator.add(windSpeed = 4.0, rideSpeed = 8.0)
        accumulator.add(windSpeed = 2.0, rideSpeed = null)

        val stats = accumulator.toStats(totalSamples = 4)

        assertEquals(50.0, stats.timePercent)
        assertEquals(8.0, stats.avgRideSpeed)
        assertEquals(8.0, stats.maxRideSpeed)
        assertEquals(3.0, stats.avgWindSpeed)
        assertEquals(4.0, stats.maxWindSpeed)
    }

    @Test
    fun testClearResetsAccumulator() {
        val accumulator = CategoryAccumulator()
        accumulator.add(windSpeed = 4.0, rideSpeed = 8.0)
        accumulator.clear()

        val stats = accumulator.toStats(totalSamples = 1)

        assertEquals(0.0, stats.timePercent)
        assertNull(stats.avgWindSpeed)
        assertNull(stats.avgRideSpeed)
    }

    @Test
    fun testThresholdCheckUsesBucketQuantizedSpeed() {
        val threshold = WindAggregator.HEADWIND_THRESHOLD

        assertFalse(WindAggregator.toBucketSpeed(0.99) >= threshold)
        assertFalse(WindAggregator.toBucketSpeed(-0.99) <= -threshold)
        assertTrue(WindAggregator.toBucketSpeed(1.5) >= threshold)
        assertTrue(WindAggregator.toBucketSpeed(-1.5) <= -threshold)
    }
}
