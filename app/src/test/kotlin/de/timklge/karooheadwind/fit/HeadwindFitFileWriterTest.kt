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

package de.timklge.karooheadwind.fit

import io.hammerhead.karooext.models.DeveloperField
import io.hammerhead.karooext.models.FieldValue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeadwindFitFileWriterTest {
    private fun List<FieldValue>.valueOf(field: DeveloperField): Double? =
        find { it.fieldNum == field.fieldDefinitionNumber.toInt() }?.value

    @Test
    fun testNoValuesWhenNoData() {
        val values = HeadwindFitFileWriter.buildRecordValues(
            relativeAngle = null,
            windSpeed = null,
            windDirection = null,
            windGust = null,
        )

        assertTrue(values.isEmpty())
    }

    @Test
    fun testWindValuesWithoutHeading() {
        val values = HeadwindFitFileWriter.buildRecordValues(
            relativeAngle = null,
            windSpeed = 5.0,
            windDirection = 270.0,
            windGust = 7.5,
        )

        assertEquals(500.0, values.valueOf(HeadwindFitFileWriter.WIND_SPEED))
        assertEquals(270.0, values.valueOf(HeadwindFitFileWriter.WIND_DIRECTION))
        assertEquals(750.0, values.valueOf(HeadwindFitFileWriter.WIND_GUST))
        assertEquals(null, values.valueOf(HeadwindFitFileWriter.HEADWIND_SPEED))
        assertEquals(null, values.valueOf(HeadwindFitFileWriter.HEADWIND_DIRECTION))
    }

    @Test
    fun testDirectHeadwind() {
        // Relative angle 0 means the wind blows towards the rider's heading, i.e. a direct tailwind
        val values = HeadwindFitFileWriter.buildRecordValues(
            relativeAngle = 0.0,
            windSpeed = 5.0,
            windDirection = 180.0,
            windGust = 5.0,
        )

        assertEquals(-500.0, values.valueOf(HeadwindFitFileWriter.HEADWIND_SPEED))
        assertEquals(0.0, values.valueOf(HeadwindFitFileWriter.HEADWIND_DIRECTION))
    }

    @Test
    fun testNegativeRelativeAngleIsNormalizedToPositiveDirection() {
        val values = HeadwindFitFileWriter.buildRecordValues(
            relativeAngle = -90.0,
            windSpeed = 5.0,
            windDirection = 0.0,
            windGust = 5.0,
        )

        assertEquals(270.0, values.valueOf(HeadwindFitFileWriter.HEADWIND_DIRECTION))
        assertEquals(0.0, values.valueOf(HeadwindFitFileWriter.HEADWIND_SPEED))
    }

    @Test
    fun testSpeedIsClampedToSignedShortRange() {
        val values = HeadwindFitFileWriter.buildRecordValues(
            relativeAngle = null,
            windSpeed = 400.0,
            windDirection = null,
            windGust = -400.0,
        )

        assertEquals(Short.MAX_VALUE.toDouble(), values.valueOf(HeadwindFitFileWriter.WIND_SPEED))
        assertEquals(Short.MIN_VALUE.toDouble(), values.valueOf(HeadwindFitFileWriter.WIND_GUST))
    }
}
