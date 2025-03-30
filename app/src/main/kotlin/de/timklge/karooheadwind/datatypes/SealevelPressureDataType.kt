package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService

class SealevelPressureDataType(karooSystemService: KarooSystemService, context: Context) : BaseDataType(karooSystemService, context, "sealevelPressure"){
    override fun getValue(data: WeatherData): Double? {
        return data.sealevelPressure
    }
}