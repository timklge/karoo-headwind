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

import de.timklge.karooheadwind.datatypes.WindCategoryStats
import de.timklge.karooheadwind.datatypes.WindSessionStats
import io.hammerhead.karooext.models.DeveloperField
import io.hammerhead.karooext.models.FieldValue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeadwindFitSessionValuesTest {
    private fun List<FieldValue>.valueOf(field: DeveloperField): Double? =
        find { it.fieldNum == field.fieldDefinitionNumber.toInt() }?.value

    private val emptyCategory = WindCategoryStats(
        timePercent = 0.0,
        avgRideSpeed = null,
        maxRideSpeed = null,
        avgWindSpeed = null,
        maxWindSpeed = null,
    )

    @Test
    fun testEmptyStatsOnlyWritesTimePercent() {
        val values = HeadwindFitFileWriter.buildSessionValues(
            WindSessionStats(
                headwind = emptyCategory,
                tailwind = emptyCategory,
                crosswind = emptyCategory,
                avgWindSpeed = null,
                maxWindSpeed = null,
            )
        )

        assertEquals(0.0, values.valueOf(HeadwindFitFileWriter.HEADWIND_FIELDS.timePercent))
        assertEquals(0.0, values.valueOf(HeadwindFitFileWriter.TAILWIND_FIELDS.timePercent))
        assertEquals(0.0, values.valueOf(HeadwindFitFileWriter.CROSSWIND_FIELDS.timePercent))
        assertNull(values.valueOf(HeadwindFitFileWriter.HEADWIND_FIELDS.avgRideSpeed))
        assertNull(values.valueOf(HeadwindFitFileWriter.WIND_AVG_SPEED))
        assertNull(values.valueOf(HeadwindFitFileWriter.WIND_MAX_SPEED))
    }

    @Test
    fun testSpeedsAreScaledToCentimetersPerSecond() {
        val values = HeadwindFitFileWriter.buildSessionValues(
            WindSessionStats(
                headwind = WindCategoryStats(
                    timePercent = 25.0,
                    avgRideSpeed = 8.0,
                    maxRideSpeed = 10.0,
                    avgWindSpeed = 3.5,
                    maxWindSpeed = 4.0,
                ),
                tailwind = emptyCategory,
                crosswind = emptyCategory,
                avgWindSpeed = 2.0,
                maxWindSpeed = 6.0,
            )
        )

        assertEquals(25.0, values.valueOf(HeadwindFitFileWriter.HEADWIND_FIELDS.timePercent))
        assertEquals(800.0, values.valueOf(HeadwindFitFileWriter.HEADWIND_FIELDS.avgRideSpeed))
        assertEquals(1000.0, values.valueOf(HeadwindFitFileWriter.HEADWIND_FIELDS.maxRideSpeed))
        assertEquals(350.0, values.valueOf(HeadwindFitFileWriter.HEADWIND_FIELDS.avgWindSpeed))
        assertEquals(400.0, values.valueOf(HeadwindFitFileWriter.HEADWIND_FIELDS.maxWindSpeed))
        assertEquals(200.0, values.valueOf(HeadwindFitFileWriter.WIND_AVG_SPEED))
        assertEquals(600.0, values.valueOf(HeadwindFitFileWriter.WIND_MAX_SPEED))
    }

    @Test
    fun testCategoryFieldsUseDistinctFieldNumbers() {
        val numbers = listOf(
            HeadwindFitFileWriter.HEADWIND_FIELDS,
            HeadwindFitFileWriter.TAILWIND_FIELDS,
            HeadwindFitFileWriter.CROSSWIND_FIELDS,
        ).flatMap {
            listOf(it.timePercent, it.avgRideSpeed, it.maxRideSpeed, it.avgWindSpeed, it.maxWindSpeed)
        }.map { it.fieldDefinitionNumber } + listOf(
            HeadwindFitFileWriter.WIND_AVG_SPEED.fieldDefinitionNumber,
            HeadwindFitFileWriter.WIND_MAX_SPEED.fieldDefinitionNumber,
        )

        assertEquals(numbers.size, numbers.toSet().size)
    }
}
