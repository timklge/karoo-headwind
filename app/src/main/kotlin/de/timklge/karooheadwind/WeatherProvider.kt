package de.timklge.karooheadwind

import de.timklge.karooheadwind.datatypes.GpsCoordinates
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.UserProfile

interface WeatherProvider {
    suspend fun getWeatherData(
        karooSystem: KarooSystemService,
        coordinates: List<GpsCoordinates>,
        settings: HeadwindSettings,
        profile: UserProfile?
    ): HttpResponseState.Complete
}