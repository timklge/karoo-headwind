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

fun celciusInUserUnit(celcius: Double, isImperial: Boolean): Double {
    return if (isImperial) {
        celcius * 9.0 / 5 + 32.0
    } else {
        celcius
    }
}

fun millimetersInUserUnit(millimeters: Double, isImperial: Boolean): Double {
    return if (isImperial) {
        millimeters / 25.4
    } else {
        millimeters
    }
}

fun msInWindUnit(ms: Double, unit: WindUnit): Double {
    return when (unit) {
        WindUnit.KILOMETERS_PER_HOUR -> ms * 3.6
        WindUnit.METERS_PER_SECOND -> ms
        WindUnit.MILES_PER_HOUR -> ms * 2.2369362920544
        WindUnit.KNOTS -> ms * 1.9438444924406
    }
}

fun msInUserSpeedUnit(ms: Double, isImperial: Boolean): Double {
    return if (isImperial) {
        ms * 2.2369362920544
    } else {
        ms * 3.6
    }
}