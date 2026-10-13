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

package de.timklge.karooheadwind.util

import de.timklge.karooheadwind.WindUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/** The unit converters the data types apply before streaming a value. Pure JVM, no Android runtime. */
class ConversionTest {

    @Test
    fun `celsius passes through for metric and converts for imperial`() {
        assertEquals(12.5, celciusInUserUnit(12.5, isImperial = false), 0.0)
        assertEquals(32.0, celciusInUserUnit(0.0, isImperial = true), 0.0)
        assertEquals(212.0, celciusInUserUnit(100.0, isImperial = true), 0.0)
        assertEquals(54.5, celciusInUserUnit(12.5, isImperial = true), 0.0)
    }

    @Test
    fun `millimeters pass through for metric and convert for imperial`() {
        assertEquals(2.0, millimetersInUserUnit(2.0, isImperial = false), 0.0)
        assertEquals(1.0, millimetersInUserUnit(25.4, isImperial = true), 1e-9)
    }

    @Test
    fun `one metre per second converts into each offered wind unit`() {
        assertEquals(3.6, msInWindUnit(1.0, WindUnit.KILOMETERS_PER_HOUR), 1e-9)
        assertEquals(1.0, msInWindUnit(1.0, WindUnit.METERS_PER_SECOND), 0.0)
        assertEquals(2.2369362920544, msInWindUnit(1.0, WindUnit.MILES_PER_HOUR), 1e-9)
        assertEquals(1.9438444924406, msInWindUnit(1.0, WindUnit.KNOTS), 1e-9)
    }

    @Test
    fun `rider speed converts to the profile unit rather than the chosen wind unit`() {
        assertEquals(3.6, msInUserSpeedUnit(1.0, isImperial = false), 1e-9)
        assertEquals(2.2369362920544, msInUserSpeedUnit(1.0, isImperial = true), 1e-9)
    }

    @Test
    fun `the signed angle difference takes the shorter way round`() {
        assertEquals(0.0, signedAngleDifference(0.0, 0.0), 1e-9)
        assertEquals(90.0, signedAngleDifference(0.0, 90.0), 1e-9)
        assertEquals(-90.0, signedAngleDifference(90.0, 0.0), 1e-9)
        assertEquals("350 to 10 is a short positive step", 20.0, signedAngleDifference(350.0, 10.0), 1e-9)
        assertEquals("10 to 350 is a short negative step", -20.0, signedAngleDifference(10.0, 350.0), 1e-9)
        assertEquals(180.0, signedAngleDifference(0.0, 180.0), 1e-9)
    }

    @Test
    fun `the signed angle difference folds angles beyond a full turn`() {
        assertEquals(45.0, signedAngleDifference(720.0, 45.0), 1e-9)
        assertEquals(45.0, signedAngleDifference(-360.0, 45.0), 1e-9)
        assertEquals("a negative angle folds onto the same heading", 0.0, signedAngleDifference(45.0, -315.0), 1e-9)
    }

}
