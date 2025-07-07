package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile

class UviDataType(karooSystemService: KarooSystemService, context: Context) : BaseDataType(karooSystemService, context, "uvi"){
    override fun getValue(data: WeatherData, userProfile: UserProfile): Double {
        return data.uvi
    }
}