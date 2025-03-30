package de.timklge.karooheadwind.weatherprovider

enum class WeatherInterpretation {
    CLEAR, CLOUDY, RAINY, SNOWY, DRIZZLE, THUNDERSTORM, UNKNOWN;

    companion object {
        // WMO weather interpretation codes (WW)
        fun fromWeatherCode(code: Int?): WeatherInterpretation {
            return when(code){
                0 -> CLEAR
                1, 2, 3 -> CLOUDY
                45, 48, 61, 63, 65, 66, 67, 80, 81, 82 -> RAINY
                71, 73, 75, 77, 85, 86 -> SNOWY
                51, 53, 55, 56, 57 -> DRIZZLE
                95, 96, 99 -> THUNDERSTORM
                else -> UNKNOWN
            }
        }

        fun getKnownWeatherCodes(): Set<Int> = setOf(0, 1, 2, 3, 45, 48, 61, 63, 65, 66, 67, 80, 81, 82, 71, 73, 75, 77, 85, 86, 51, 53, 55, 56, 57, 95, 96, 99)
    }
}