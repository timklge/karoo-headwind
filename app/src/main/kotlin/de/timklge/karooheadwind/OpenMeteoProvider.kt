package de.timklge.karooheadwind

import de.timklge.karooheadwind.datatypes.GpsCoordinates
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.UserProfile

class OpenMeteoProvider : WeatherProvider {
    override suspend fun getWeatherData(
        karooSystem: KarooSystemService,
        coordinates: List<GpsCoordinates>,
        settings: HeadwindSettings,
        profile: UserProfile?
    ): HttpResponseState.Complete {
        val precipitationUnit = if (profile?.preferredUnit?.distance != UserProfile.PreferredUnit.UnitType.IMPERIAL)
            PrecipitationUnit.MILLIMETERS else PrecipitationUnit.INCH
        val temperatureUnit = if (profile?.preferredUnit?.temperature != UserProfile.PreferredUnit.UnitType.IMPERIAL)
            TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT

        return karooSystem.originalOpenMeteoRequest(coordinates, settings, profile, precipitationUnit, temperatureUnit)
    }
}