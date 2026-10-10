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
        val requestedHours = forecastData?.data?.firstOrNull()?.forecasts?.size?.coerceAtMost(6)

        if (forecastData == null || requestedHours == null || requestedPositions == null || (requestedPositions == 1 && hourOffset >= requestedHours) || (requestedPositions in 2..hourOffset)) {
            hourOffset = 0
        }

        val newSettings = currentSettings.copy(currentForecastHourOffset = hourOffset)
        saveWidgetSettings(context, newSettings)
    }
}