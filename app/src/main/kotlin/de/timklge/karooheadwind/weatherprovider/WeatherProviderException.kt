package de.timklge.karooheadwind.weatherprovider

class WeatherProviderException(val statusCode: Int, message: String) : Exception(message)