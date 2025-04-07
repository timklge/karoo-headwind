package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService

class WindSpeedDataType(karooSystemService: KarooSystemService, context: Context) : BaseDataType(karooSystemService, context, "windSpeed"){
    override fun getValue(data: WeatherData): Double {
        return data.windSpeed
    }
}