package de.timklge.karooheadwind.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.RefreshRate
import de.timklge.karooheadwind.RoundLocationSetting
import de.timklge.karooheadwind.WeatherDataProvider
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.saveSettings
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.streamUserProfile
import de.timklge.karooheadwind.weatherprovider.WeatherProviderException
import de.timklge.karooheadwind.weatherprovider.openweathermap.OpenWeatherMapWeatherProvider
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


@Composable
fun SettingsScreen(onFinish: () -> Unit) {
    var karooConnected by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val karooSystem = remember { KarooSystemService(ctx) }

    var refreshRateSetting by remember { mutableStateOf(RefreshRate.STANDARD) }

    var selectedRoundLocationSetting by remember { mutableStateOf(RoundLocationSetting.KM_3) }
    var forecastKmPerHour by remember { mutableStateOf("20") }
    var forecastMilesPerHour by remember { mutableStateOf("12") }
    var showDistanceInForecast by remember { mutableStateOf(true) }

    val profile by karooSystem.streamUserProfile().collectAsStateWithLifecycle(null)

    var selectedWeatherProvider by remember { mutableStateOf(WeatherDataProvider.OPEN_METEO) }
    var openWeatherMapApiKey by remember { mutableStateOf("") }
    var isK2 by remember { mutableStateOf(false) }
    var useMagnetometerForHeading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ctx.streamSettings(karooSystem).collect { settings ->
            selectedRoundLocationSetting = settings.roundLocationTo
            forecastKmPerHour = settings.forecastedKmPerHour.toString()
            forecastMilesPerHour = settings.forecastedMilesPerHour.toString()
            showDistanceInForecast = settings.showDistanceInForecast
            selectedWeatherProvider = settings.weatherProvider
            openWeatherMapApiKey = settings.openWeatherMapApiKey
            refreshRateSetting = settings.refreshRate
            useMagnetometerForHeading = settings.useMagnetometerForHeading
        }
    }

    LaunchedEffect(Unit) {
        karooSystem.connect { connected ->
            karooConnected = connected
            isK2 = karooSystem.hardwareType == io.hammerhead.karooext.models.HardwareType.K2
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            karooSystem.disconnect()
        }
    }

    suspend fun updateSettings(){
        Log.d(KarooHeadwindExtension.TAG, "Saving settings")

        val newSettings = HeadwindSettings(
            welcomeDialogAccepted = true,
            roundLocationTo = selectedRoundLocationSetting,
            forecastedMilesPerHour = forecastMilesPerHour.toIntOrNull()?.coerceIn(3, 30) ?: 12,
            forecastedKmPerHour = forecastKmPerHour.toIntOrNull()?.coerceIn(5, 50) ?: 20,
            showDistanceInForecast = showDistanceInForecast,
            weatherProvider = selectedWeatherProvider,
            openWeatherMapApiKey = openWeatherMapApiKey,
            refreshRate = refreshRateSetting,
            useMagnetometerForHeading = useMagnetometerForHeading
        )

        saveSettings(ctx, newSettings)
    }

    DisposableEffect(Unit) {
        onDispose {
            runBlocking {
                updateSettings()
            }
        }
    }

    BackHandler {
        coroutineScope.launch {
            updateSettings()
            onFinish()
        }
    }

    Column(
        modifier = Modifier
            .padding(5.dp)
                .verticalScroll(rememberScrollState())
            .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val refreshRateDropdownOptions = remember(isK2) { RefreshRate.entries.toList().map { unit -> DropdownOption(unit.id, unit.getDescription(isK2)) } }
        val refreshRateSelection by remember(refreshRateSetting, isK2) {
            mutableStateOf(refreshRateDropdownOptions.find { option -> option.id == refreshRateSetting.id }!!)
        }
        Dropdown(
            label = "Refresh Rate",
            options = refreshRateDropdownOptions,
            selected = refreshRateSelection
        ) { selectedOption ->
            refreshRateSetting = RefreshRate.entries.find { unit -> unit.id == selectedOption.id }!!
        }

        val roundLocationDropdownOptions = RoundLocationSetting.entries.toList()
            .map { unit -> DropdownOption(unit.id, unit.label) }
        val roundLocationInitialSelection by remember(selectedRoundLocationSetting) {
            mutableStateOf(roundLocationDropdownOptions.find { option -> option.id == selectedRoundLocationSetting.id }!!)
        }
        Dropdown(
            label = "Round Location",
            options = roundLocationDropdownOptions,
            selected = roundLocationInitialSelection
        ) { selectedOption ->
            selectedRoundLocationSetting =
                RoundLocationSetting.entries.find { unit -> unit.id == selectedOption.id }!!
        }

        if (profile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL) {
            OutlinedTextField(
                value = forecastMilesPerHour, modifier = Modifier.fillMaxWidth(),
                onValueChange = { forecastMilesPerHour = it },
                label = { Text("Forecast Distance per Hour") },
                suffix = { Text("mi") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        } else {
            OutlinedTextField(
                value = forecastKmPerHour, modifier = Modifier.fillMaxWidth(),
                onValueChange = { forecastKmPerHour = it },
                label = { Text("Forecast Distance per Hour") },
                suffix = { Text("km") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = showDistanceInForecast, onCheckedChange = { showDistanceInForecast = it})
            Spacer(modifier = Modifier.width(10.dp))
            Text("Show Distance in Forecast")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = useMagnetometerForHeading, onCheckedChange = { useMagnetometerForHeading = it})
            Spacer(modifier = Modifier.width(10.dp))
            Text("Use Magnetometer")
        }

        if (!karooConnected) {
            Text(
                modifier = Modifier.padding(5.dp),
                text = "Could not read device status. Is your Karoo updated?"
            )
        }

        WeatherProviderSection(
            selectedProvider = selectedWeatherProvider,
            karooSystemService = karooSystem,
            onProviderChanged = { selectedWeatherProvider = it },
            onApiKeyChanged = { openWeatherMapApiKey = it },
            apiKey = openWeatherMapApiKey
        )

        Spacer(modifier = Modifier.padding(30.dp))

    }
}

@Composable
fun WeatherProviderSection(
    selectedProvider: WeatherDataProvider,
    karooSystemService: KarooSystemService,
    onProviderChanged: (WeatherDataProvider) -> Unit,
    apiKey: String,
    onApiKeyChanged: (String) -> Unit,
) {
    val profile by karooSystemService.streamUserProfile().collectAsStateWithLifecycle(null)
    val settings by LocalContext.current.streamSettings(karooSystemService).collectAsStateWithLifecycle(HeadwindSettings())

    var apiTestErrorMessage by remember { mutableStateOf("") }
    var apiTestDialogVisible by remember { mutableStateOf(false) }
    var apiTestDialogPending by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val weatherProviderOptions = WeatherDataProvider.entries.toList()
        .map { provider -> DropdownOption(provider.id, provider.label) }
    val weatherProviderSelection by remember(selectedProvider) {
        mutableStateOf(weatherProviderOptions.find { option -> option.id == selectedProvider.id }!!)
    }

    val currentApiKey by rememberUpdatedState(apiKey)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Dropdown(
            label = "Weather Provider",
            options = weatherProviderOptions,
            selected = weatherProviderSelection
        ) { selectedOption ->
            onProviderChanged(WeatherDataProvider.entries.find { provider -> provider.id == selectedOption.id }!!)
        }

        if (selectedProvider == WeatherDataProvider.OPEN_WEATHER_MAP) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    onApiKeyChanged(it)
                },
                label = { Text("OpenWeatherMap API Key") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                    singleLine = true
            )

            Text(
                text = "If you want to use OpenWeatherMap, you need to provide an API key.",
                style = MaterialTheme.typography.bodySmall
            )

            if (apiTestDialogVisible) {
                Dialog(onDismissRequest = { apiTestDialogVisible = false }) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = apiTestErrorMessage)
                            if (apiTestDialogPending) {
                                LinearProgressIndicator()
                            }
                            Button(
                                onClick = { apiTestDialogVisible = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("OK")
                            }
                        }
                    }
                }
            }

            FilledTonalButton(modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
                onClick = {
                    apiTestDialogVisible = true
                    apiTestDialogPending = true
                    apiTestErrorMessage = "Testing API key..."

                    coroutineScope.launch {
                        try {
                            // Use currentApiKey instead of apiKey to capture the latest value
                            val provider = OpenWeatherMapWeatherProvider(currentApiKey)
                            val response = provider.getWeatherData(karooSystemService, listOf(GpsCoordinates(52.5186, 13.399)), settings, profile)
                            apiTestDialogPending = false
                            if (response.error.isNullOrEmpty()) {
                                apiTestErrorMessage = "API key is valid"
                                Log.d(KarooHeadwindExtension.TAG, "API key is valid")
                            } else {
                                apiTestErrorMessage = "Error testing API key: ${response.error}"
                                Log.e(KarooHeadwindExtension.TAG, "API key is invalid")
                            }
                        } catch (e: WeatherProviderException) {
                            if (e.statusCode == 0) {
                                Log.e(KarooHeadwindExtension.TAG, "Error testing API key: No connection")
                                apiTestDialogPending = false
                                apiTestErrorMessage = "Error testing API key: No internet connection"
                            } else {
                                Log.e(KarooHeadwindExtension.TAG, "Error testing API key: ${e.message}")
                                apiTestDialogPending = false
                                apiTestErrorMessage = "Error testing API key: ${e.message}"
                            }
                        } catch (e: Exception) {
                            Log.e(KarooHeadwindExtension.TAG, "Error testing API key: ${e.message}")
                            apiTestDialogPending = false
                            apiTestErrorMessage = "Error testing API key: ${e.message}"
                        }
                    }
                }) {
                Text("Test API Key")
            }
        }
    }
}
