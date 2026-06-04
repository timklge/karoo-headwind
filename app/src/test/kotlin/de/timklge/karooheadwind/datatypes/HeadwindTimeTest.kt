package de.timklge.karooheadwind.datatypes

import org.junit.Test
import kotlin.test.assertEquals

class HeadwindTimeTest {
    @Test
    fun testAccumulatesWhenHeadwindAboveThreshold() {
        val updated = HeadwindTimeDataType.updateAccumulatedHeadwindTime(
            previousAccumulatedTime = 10.0,
            headwindSpeed = 5.0,
            deltaTime = 2.0
        )

        assertEquals(12.0, updated, 0.001)
    }

    @Test
    fun testDoesNotAccumulateWhenHeadwindBelowThreshold() {
        val updated = HeadwindTimeDataType.updateAccumulatedHeadwindTime(
            previousAccumulatedTime = 10.0,
            headwindSpeed = 0.5,
            deltaTime = 2.0
        )

        assertEquals(10.0, updated, 0.001)
    }

    @Test
    fun testDoesNotAccumulateOnTailwind() {
        val updated = HeadwindTimeDataType.updateAccumulatedHeadwindTime(
            previousAccumulatedTime = 10.0,
            headwindSpeed = -2.0,
            deltaTime = 2.0
        )

        assertEquals(10.0, updated, 0.001)
    }

    @Test
    fun testAccumulatesExactlyAtThreshold() {
        val updated = HeadwindTimeDataType.updateAccumulatedHeadwindTime(
            previousAccumulatedTime = 0.0,
            headwindSpeed = HeadwindTimeDataType.HEADWIND_THRESHOLD_MS,
            deltaTime = 1.5
        )

        assertEquals(1.5, updated, 0.001)
    }
}
