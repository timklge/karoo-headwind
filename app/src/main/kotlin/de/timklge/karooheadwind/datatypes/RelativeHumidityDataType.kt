package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService

class RelativeHumidityDataType(karooSystemService: KarooSystemService, context: Context) : BaseDataType(karooSystemService, context, "relativeHumidity"){
    override fun getValue(data: WeatherData): Double? {
        return data.relativeHumidity
    }
}