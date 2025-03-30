package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.saveWidgetSettings
import de.timklge.karooheadwind.streamCurrentForecastWeatherData
import de.timklge.karooheadwind.streamWidgetSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

class CycleHoursAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d(KarooHeadwindExtension.TAG, "Cycling hours")

        val currentSettings = context.streamWidgetSettings().first()
        val forecastData = context.streamCurrentForecastWeatherData().firstOrNull()

        var hourOffset = currentSettings.currentForecastHourOffset + 3
        val requestedPositions = forecastData?.data?.size
        val requestedHours = forecastData?.data?.firstOrNull()?.forecasts?.size

        if (forecastData == null || requestedHours == null || requestedPositions == null || hourOffset >= requestedHours || (requestedPositions in 2..hourOffset)) {
            hourOffset = 0
        }

        val newSettings = currentSettings.copy(currentForecastHourOffset = hourOffset)
        saveWidgetSettings(context, newSettings)
    }
}