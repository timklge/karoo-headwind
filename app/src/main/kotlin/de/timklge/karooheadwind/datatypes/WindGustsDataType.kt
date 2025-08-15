package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.UserProfile

class WindGustsDataType(karooSystemService: KarooSystemService, context: Context) : BaseDataType(karooSystemService, context, "windGusts"){
    override fun getValue(data: WeatherData, userProfile: UserProfile): Double {
        return data.windGusts
    }

    override fun getFormatDataType(): String {
        return DataType.Type.SPEED
    }
}