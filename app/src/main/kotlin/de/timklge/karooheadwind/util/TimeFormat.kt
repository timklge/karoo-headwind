package de.timklge.karooheadwind.util

import android.content.Context
import android.text.format.DateFormat
import java.time.format.DateTimeFormatter

fun getTimeFormatter(context: Context): DateTimeFormatter {
    val is24HourFormat = DateFormat.is24HourFormat(context)

    return if (is24HourFormat) {
        DateTimeFormatter.ofPattern("HH:mm")
    } else {
        DateTimeFormatter.ofPattern("h a")
    }
}