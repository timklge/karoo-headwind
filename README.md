# Karoo Headwind Extension

[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/timklge/karoo-headwind/android.yml)](https://github.com/timklge/karoo-headwind/actions/workflows/android.yml)
[![GitHub Downloads (specific asset, all releases)](https://img.shields.io/github/downloads/timklge/karoo-headwind/app-release.apk)](https://github.com/timklge/karoo-headwind/releases)
[![GitHub License](https://img.shields.io/github/license/timklge/karoo-headwind)](https://github.com/timklge/karoo-headwind/blob/master/LICENSE)

This extension for Karoo devices adds a graphical data field that shows the current headwind direction and speed relative to the riding direction.

Compatible with Karoo 2 and Karoo 3 devices.

<a href="https://www.buymeacoffee.com/timklge" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/default-orange.png" alt="Buy Me A Coffee" height="41" width="174"></a>

![Page](preview0.png)
![Field](preview1.png)
![Overview](preview2.png)
![Setup](preview3.png)

## Installation

If you are using a Karoo 3, you can use [Hammerhead's sideloading procedure](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading) to install the app:

1. Using the browser on your phone, long-press [this download link](https://github.com/timklge/karoo-headwind/releases/latest/download/app-release.apk) and share it with the Hammerhead Companion app.
2. Your karoo should show an info screen about the app now. Press "Install".
3. Open the app from the main menu and acknowledge the API usage note.
4. Set up your data fields as desired.

If you are using a Karoo 2, you can use manual sideloading:

1. Download the apk from the [releases page](https://github.com/timklge/karoo-headwind/releases) (or build it from source)
2. Set up your Karoo for sideloading. DC Rainmaker has a great [step-by-step guide](https://www.dcrainmaker.com/2021/02/how-to-sideload-android-apps-on-your-hammerhead-karoo-1-karoo-2.html).
3. Install the app by running `adb install app-release.apk`.
4. Open the app from the main menu and acknowledge the API usage note.
5. Set up your data fields as desired.

## Usage

After installing this app on your Karoo and opening it once from the main menu, you can add the following new data fields to your data pages:

- Headwind (graphical, 1x1 field): Shows the headwind direction and speed as a circle with a triangular direction indicator. The speed is shown at the center in your set unit of measurement (default is kilometers per hour if you have set up metric units in your Karoo, otherwise miles per hour). Both direction and speed are relative to the current riding direction by default, i. e., riding directly into a wind of 20 km/h will show a headwind speed of 20 km/h, while riding in the same direction will show -20 km/h. You can change this behavior in the app settings to show the absolute wind direction and speed instead.
- Tailwind with riding speed (graphical, 1x1 field): Shows an arrow indicating the current headwind direction next to a label reading your current speed and the speed of the tailwind. If you ride against a headwind of 5 mph, it will show "-5". If you ride in the same direction of a 5 mph wind, it will read "+5". Text and arrow are colored based on the tailwind speed, with red indicating a strong headwind and green indicating a strong tailwind.
- Weather forecast (graphical, 2x1 field): Shows three columns indicating the current weather conditions (sunny, cloudy, ...), wind direction, precipitation and temperature forecasted for the next three hours. Tap on this widget to cycle through the 12 hour forecast. If you have a route loaded, the forecast widget will show the forecasted weather along points of the route, with an estimated traveled distance per hour of 20 km / 12 miles by default.
- Current weather (graphical, 1x1 field): Shows current weather conditions (same as forecast widget, but only for the current time). Tap on this widget to open the headwind app with a forecast overview.
- Additionally, data fields that only show the current data value for headwind speed, humidity, cloud cover, absolute wind speed, absolute wind gust speed, absolute wind direction, rainfall and surface pressure can be added if desired.

The app can use OpenMeteo or OpenWeatherMap as providers for live weather data.

- OpenMeteo is the default provider and does not require any configuration. 
- OpenWeatherMap can provide more accurate data for some locations. Forecasts along the loaded route are not available using OpenWeatherMap. OpenWeatherMap is free for personal use, but you need to register at https://openweathermap.org/home/sign_in and obtain a one call API key. If you use OpenWeatherMap without a correct key, the app will fall back to OpenMeteo.

The app will automatically attempt to download weather data from the selected data provider once your device has acquired a GPS fix. Your location is rounded to approximately three kilometers to maintain privacy.
New weather data is downloaded when you ride more than three kilometers from the location where the weather data was downloaded for or after one hour at the latest.
If the app cannot connect to the weather service, it will retry the download every minute. Downloading weather data should work on Karoo 2 if you have a SIM card inserted or on Karoo 3 via your phone's internet connection if you have the Karoo companion app installed.

## Credits

- Icons are from [boxicons.com](https://boxicons.com) ([MIT-licensed](icon_credits.txt))
- Made possible by the generous usage terms of [open-meteo.com](https://open-meteo.com)
- Interfaces with [openweathermap.org](https://openweathermap.org)
- Uses [karoo-ext](https://github.com/hammerheadnav/karoo-ext) (Apache2-licensed)

## Extension Developers: Headwind Data Type

If the user has installed the headwind extension on his karoo, you can stream the headwind data type from other extensions via `karoo-ext`.
Use extension id `karoo-headwind` with datatype ids `headwind` and `userwindSpeed`.

- The `headwind` datatype contains a single field that either represents an error code or the wind direction. A `-1.0` indicates missing gps receiption, `-2.0` no weather data, `-3.0` that the headwind extension
has not been set up. Otherwise, the value is the wind direction in degrees; if the user has set the headwind indicator to depict the absolute wind direction, the field will contain the absolute wind direction; otherwise
it will contain the headwind direction.
- The `userwindSpeed` datatype contains a single field with the wind speed in the user's defined unit. If the user has set the headwind indicator to show the absolute wind speed,
this field will contain the absolute wind speed; otherwise it will contain the headwind speed.