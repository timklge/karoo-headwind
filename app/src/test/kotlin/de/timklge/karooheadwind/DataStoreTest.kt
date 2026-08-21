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

import org.junit.Test
import org.junit.Assert.assertEquals

class DataStoreTest {

    @Test
    fun testLerpAngle_normalCase() {
        // Normal case (no wrap-around)
        val result = lerpAngle(90.0, 180.0, 0.5)
        assertEquals(135.0, result, 0.001)
    }

    @Test
    fun testLerpAngle_wrapAround0To360() {
        // Wrap around 0/360 degrees
        val result = lerpAngle(350.0, 10.0, 0.5)
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun testLerpAngle_wrapAround360To0() {
        // Wrap around 0/360 degrees (reverse)
        val result = lerpAngle(10.0, 350.0, 0.5)
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun testLerpAngle_largeAngleDifference() {
        // Large angle difference (should take shorter path)
        val result = lerpAngle(45.0, 315.0, 0.5)
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun testLerpAngle_exactly180DegreesApart() {
        // Exactly 180 degrees apart
        assertEquals(90.0, lerpAngle(0.0, 180.0, 0.5), 0.001)
        assertEquals(90.0, lerpAngle(180.0, 0.0, 0.5), 0.001)
    }

    @Test
    fun testLerpAngle_factorBoundaries() {
        // Factor = 0 and 1
        assertEquals(350.0, lerpAngle(350.0, 10.0, 0.0), 0.001)
        assertEquals(0.0, lerpAngle(350.0, 10.0, 0.5), 0.001)
        assertEquals(10.0, lerpAngle(350.0, 10.0, 1.0), 0.001)
    }

    @Test
    fun testLerpAngle_negativeAngles() {
        // Negative angles
        val result = lerpAngle(-10.0, 10.0, 0.5)
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun testLerpAngle_anglesGreaterThan360() {
        // Angles > 360
        val result = lerpAngle(370.0, 380.0, 0.5)
        assertEquals(15.0, result, 0.001)
    }
}
