package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class TemperatureDataType(karooSystemService: KarooSystemService, context: Context) : BaseDataType(karooSystemService, context, "temperature"){
    override fun getValue(data: WeatherData): Double {
        return data.temperature
    }

    override fun getFormatDataType(): String? {
        return DataType.Type.TEMPERATURE
    }
}