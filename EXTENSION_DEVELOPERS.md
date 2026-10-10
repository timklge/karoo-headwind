# Karoo Headwind Extension: Developer Guide

## Extension Developers: Headwind Data Type

If the user has installed the headwind extension on his karoo, you can stream the headwind data type from other extensions via `karoo-ext`.
Use data type id `TYPE_EXT::karoo-headwind::TYPE_ID` with `TYPE_ID` being one of `headwind`, `windDirection`, `headwindSpeed`, `windSpeed` etc.

- The `headwind` datatype contains a single field that either represents an error code or the *relative* wind direction. A `-1.0` indicates missing gps reception, `-2.0` no weather data, `-3.0` that the headwind extension
has not been set up. Otherwise, the value is the headwind direction in degrees.
- The `windDirection` datatype contains a single field with the *absolute* wind direction in degrees (so 0 = North, 90 = East etc.)
- The `headwindSpeed` datatype contains a single field that contains the *relative*  headwind speed in the user's chosen display unit.
- The `windSpeed` datatype contains a single field that contains the *absolute* wind speed in the user's chosen display unit.
- Other datatypes like `windGusts` etc. are also available, see [extension_info.xml](https://github.com/timklge/karoo-headwind/blob/master/app/src/main/res/xml/extension_info.xml)

## Extension Developers: Current Weather Service (AIDL)

Other apps can bind to `HeadwindService` to receive the current weather as a stream, without going through `karoo-ext`.
Bind with action `de.timklge.karooheadwind.HEADWIND_SERVICE` and package `de.timklge.karooheadwind`, then register an `IHeadwindCallback` through `IHeadwindService.registerCallback`. The AIDL files are in [app/src/main/aidl](https://github.com/timklge/karoo-headwind/tree/master/app/src/main/aidl/de/timklge/karooheadwind).

Each callback receives a JSON string, the `HeadwindSnapshot`. All fields are always present, with `null` for unset values.

- `version`: schema version (currently `1`).
- `error`: error message from the most recent weather response, or `null`.
- `lastSuccessfulFetchEpochSeconds` / `lastFailedFetchEpochSeconds`: time of the last successful and last failed weather download, in epoch seconds, or `null`. Use these to decide how stale the data is.
- `provider`: `OPEN_METEO` or `OPEN_WEATHER_MAP`, or `null`.
- `windUnit`: the wind unit the user chose in the app settings (`KILOMETERS_PER_HOUR`, `METERS_PER_SECOND`, `MILES_PER_HOUR` or `KNOTS`), or `null`.
- `forecast`: the cached weather for each location along the route, or `null` if nothing is cached. Each entry (`HeadwindForecastPoint`) has:
  - `lat`, `lon`: the location in degrees.
  - `distanceAlongRoute`: distance along the route in meters, or `null` if the location is not on a route.
  - `current`: the weather at this location.
  - `hourly`: the following hourly forecast entries, oldest first. Empty if none are available.

Each weather object (`WeatherData`) has these fields:

- `time`: epoch seconds.
- `temperature`: °C.
- `windSpeed`, `windGusts`: m/s.
- `windDirection`: degrees (0 = North).
- `relativeHumidity`, `cloudCover`: percent.
- `precipitation`: precipitation amount, `precipitationProbability`: percent or `null`.
- `sealevelPressure`, `surfacePressure`: pressure in hPa (hectopascal).
- `weatherCode`: provider weather code.
- `uvi`: UV index.
- `isForecast`, `isNight`: booleans.

Entries are not interpolated to the rider's position, so clients must pick the entry closest to their location and time themselves.

### Client library

The `client` module is an Android library that wraps the binding and parsing. Add it to your project as a module dependency (`implementation(project(":client"))`) or use the published Maven artifact `de.timklge.headwind:client` from [GitHub Packages](https://github.com/timklge/karoo-headwind/packages).

```kotlin
val client = HeadwindClient(context)

if (client.isInstalled()) {
    lifecycleScope.launch {
        client.snapshots().collect { snapshot ->
            val current = snapshot.forecast?.firstOrNull()?.current ?: return@collect
            println("Temperature: ${current.temperature} °C, wind: ${current.windSpeed} m/s")
        }
    }
}
```

`snapshots()` binds to the service, emits the latest snapshot right away, then emits on every update. It reconnects if the service restarts. The flow is cancelled when collection stops, which unbinds the service. If the service cannot be bound, the flow fails with `HeadwindServiceUnavailableException`.

The `client-example` module is a minimal Android app that uses the client library and shows the current data in a single activity.
