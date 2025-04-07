package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService

class WindGustsDataType(karooSystemService: KarooSystemService, context: Context) : BaseDataType(karooSystemService, context, "windGusts"){
    override fun getValue(data: WeatherData): Double {
        return data.windGusts
    }
}