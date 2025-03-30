package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService

class SurfacePressureDataType(karooSystemService: KarooSystemService, context: Context) : BaseDataType(karooSystemService, context, "surfacePressure"){
    override fun getValue(data: WeatherData): Double? {
        return data.surfacePressure
    }
}