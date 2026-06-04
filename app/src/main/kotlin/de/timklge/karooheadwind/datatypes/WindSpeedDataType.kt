package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.util.msInWindUnit
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.UserProfile

class WindSpeedDataType(karooSystemService: KarooSystemService, context: Context) : BaseDataType(karooSystemService, context, "windSpeed"){
    override fun getValue(data: WeatherData, userProfile: UserProfile, settings: HeadwindSettings): Double {
        val isImperial = userProfile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
        return msInWindUnit(data.windSpeed, settings.getWindUnit(isImperial))
    }

    override fun getFormatDataType(): String? {
        return DataType.Type.INTENSITY_FACTOR
    }
}
