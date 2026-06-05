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