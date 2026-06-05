package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile

class RelativeHumidityDataType(karooSystemService: KarooSystemService, context: Context) : BaseDataType(karooSystemService, context, "relativeHumidity"){
    override fun getValue(data: WeatherData, userProfile: UserProfile, settings: HeadwindSettings): Double {
        return data.relativeHumidity.toDouble()
    }
}