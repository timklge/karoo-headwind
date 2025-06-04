package de.timklge.karooheadwind.util

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

// Returns the given speed value (m / s) in user unit (km/h or mph)
fun msInUserUnit(ms: Double, isImperial: Boolean): Double {
    return if (isImperial) {
        ms * 2.2369362920544
    } else {
        ms * 3.6
    }
}