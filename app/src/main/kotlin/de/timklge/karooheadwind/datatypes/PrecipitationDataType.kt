package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.util.millimetersInUserUnit
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile

class PrecipitationDataType(karooSystemService: KarooSystemService, context: Context) : BaseDataType(karooSystemService, context, "precipitation"){
    override fun getValue(data: WeatherData, userProfile: UserProfile): Double {
        return millimetersInUserUnit(data.precipitation, userProfile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL)
    }
}