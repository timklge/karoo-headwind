package de.timklge.karooheadwind.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.HeadwindStats
import de.timklge.karooheadwind.RoundLocationSetting
import de.timklge.karooheadwind.WindDirectionIndicatorSetting
import de.timklge.karooheadwind.WindDirectionIndicatorTextSetting
import de.timklge.karooheadwind.WindUnit
import de.timklge.karooheadwind.getGpsCoordinateFlow
import de.timklge.karooheadwind.saveSettings
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.streamStats
import de.timklge.karooheadwind.streamUserProfile
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onFinish: () -> Unit) {
    var karooConnected by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val karooSystem = remember { KarooSystemService(ctx) }

    var selectedWindUnit by remember { mutableStateOf(WindUnit.KILOMETERS_PER_HOUR) }
    var selectedWindDirectionIndicatorTextSetting by remember {
        mutableStateOf(
            WindDirectionIndicatorTextSetting.HEADWIND_SPEED
        )
    }
    var selectedWindDirectionIndicatorSetting by remember {
        mutableStateOf(
            WindDirectionIndicatorSetting.HEADWIND_DIRECTION
        )
    }
    var selectedRoundLocationSetting by remember { mutableStateOf(RoundLocationSetting.KM_2) }
    var forecastKmPerHour by remember { mutableStateOf("20") }
    var forecastMilesPerHour by remember { mutableStateOf("12") }

    val profile by karooSystem.streamUserProfile().collectAsStateWithLifecycle(null)
    val stats by ctx.streamStats().collectAsStateWithLifecycle(HeadwindStats())
    val location by karooSystem.getGpsCoordinateFlow(ctx).collectAsStateWithLifecycle(null)

    var savedDialogVisible by remember { mutableStateOf(false) }
    var exitDialogVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ctx.streamSettings(karooSystem).collect { settings ->
            selectedWindUnit = settings.windUnit
            selectedWindDirectionIndicatorTextSetting = settings.windDirectionIndicatorTextSetting
            selectedWindDirectionIndicatorSetting = settings.windDirectionIndicatorSetting
            selectedRoundLocationSetting = settings.roundLocationTo
            forecastKmPerHour = settings.forecastedKmPerHour.toString()
            forecastMilesPerHour = settings.forecastedMilesPerHour.toString()
        }
    }

    LaunchedEffect(Unit) {
        karooSystem.connect { connected ->
            karooConnected = connected
        }
    }

    Column(
        modifier = Modifier
            .padding(5.dp)
            .verticalScroll(rememberScrollState())
            .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        val windDirectionIndicatorSettingDropdownOptions =
            WindDirectionIndicatorSetting.entries.toList()
                .map { unit -> DropdownOption(unit.id, unit.label) }
        val windDirectionIndicatorSettingSelection by remember(selectedWindDirectionIndicatorSetting) {
            mutableStateOf(windDirectionIndicatorSettingDropdownOptions.find { option -> option.id == selectedWindDirectionIndicatorSetting.id }!!)
        }
        Dropdown(
            label = "Wind direction indicator",
            options = windDirectionIndicatorSettingDropdownOptions,
            selected = windDirectionIndicatorSettingSelection
        ) { selectedOption ->
            selectedWindDirectionIndicatorSetting =
                WindDirectionIndicatorSetting.entries.find { unit -> unit.id == selectedOption.id }!!
        }

        val windDirectionIndicatorTextSettingDropdownOptions =
            WindDirectionIndicatorTextSetting.entries.toList()
                .map { unit -> DropdownOption(unit.id, unit.label) }
        val windDirectionIndicatorTextSettingSelection by remember(
            selectedWindDirectionIndicatorTextSetting
        ) {
            mutableStateOf(windDirectionIndicatorTextSettingDropdownOptions.find { option -> option.id == selectedWindDirectionIndicatorTextSetting.id }!!)
        }
        Dropdown(
            label = "Text on headwind indicator",
            options = windDirectionIndicatorTextSettingDropdownOptions,
            selected = windDirectionIndicatorTextSettingSelection
        ) { selectedOption ->
            selectedWindDirectionIndicatorTextSetting =
                WindDirectionIndicatorTextSetting.entries.find { unit -> unit.id == selectedOption.id }!!
        }

        val windSpeedUnitDropdownOptions =
            WindUnit.entries.toList().map { unit -> DropdownOption(unit.id, unit.label) }
        val windSpeedUnitInitialSelection by remember(selectedWindUnit) {
            mutableStateOf(windSpeedUnitDropdownOptions.find { option -> option.id == selectedWindUnit.id }!!)
        }
        Dropdown(
            label = "Wind Speed Unit",
            options = windSpeedUnitDropdownOptions,
            selected = windSpeedUnitInitialSelection
        ) { selectedOption ->
            selectedWindUnit = WindUnit.entries.find { unit -> unit.id == selectedOption.id }!!
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


        FilledTonalButton(modifier = Modifier
            .fillMaxWidth()
            .height(50.dp), onClick = {
            val newSettings = HeadwindSettings(
                windUnit = selectedWindUnit,
                welcomeDialogAccepted = true,
                windDirectionIndicatorSetting = selectedWindDirectionIndicatorSetting,
                windDirectionIndicatorTextSetting = selectedWindDirectionIndicatorTextSetting,
                roundLocationTo = selectedRoundLocationSetting,
                forecastedMilesPerHour = forecastMilesPerHour.toIntOrNull()?.coerceIn(3, 30) ?: 12,
                forecastedKmPerHour = forecastKmPerHour.toIntOrNull()?.coerceIn(5, 50) ?: 20
            )

            coroutineScope.launch {
                saveSettings(ctx, newSettings)
                savedDialogVisible = true
            }
        }) {
            Icon(Icons.Default.Done, contentDescription = "Save")
            Spacer(modifier = Modifier.width(5.dp))
            Text("Save")
        }

        if (!karooConnected) {
            Text(
                modifier = Modifier.padding(5.dp),
                text = "Could not read device status. Is your Karoo updated?"
            )
        }

        FilledTonalButton(modifier = Modifier
            .fillMaxWidth()
            .height(50.dp), onClick = {
            exitDialogVisible = true
        }) {
            Icon(Icons.AutoMirrored.Default.ExitToApp, contentDescription = "Exit")
            Spacer(modifier = Modifier.width(5.dp))
            Text("Exit")
        }
    }

    if (exitDialogVisible) {
        AlertDialog(onDismissRequest = { exitDialogVisible = false },
            confirmButton = {
                Button(onClick = {
                    onFinish()
                }) { Text("Yes") }
            },
            dismissButton = {
                Button(onClick = {
                    exitDialogVisible = false
                }) { Text("No") }
            },
            text = { Text("Do you really want to exit?") }
        )
    }

    if (savedDialogVisible){
        AlertDialog(onDismissRequest = { savedDialogVisible = false },
            confirmButton = {
                Button(onClick = {
                    savedDialogVisible = false
                }) { Text("OK") }
            },
            text = { Text("Settings saved successfully.") }
        )
    }
}