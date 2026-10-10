/*
 * Copyright 2024-2026 karoo-headwind contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.timklge.karooheadwind.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.R
import de.timklge.karooheadwind.saveSettings
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.util.Updater
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private var updateNotificationShown = false

@Composable
fun MainScreen(close: () -> Unit) {
    var karooConnected by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val karooSystem = remember { KarooSystemService(ctx) }

    var welcomeDialogVisible by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<Updater.UpdateInfo?>(null) }
    var tabIndex by remember { mutableIntStateOf(0) }

    var isRefreshing by remember { mutableStateOf(false) }

    val tabs = listOf("Live", "Setup", "Windy")

    fun refreshData() {
        coroutineScope.launch {
            isRefreshing = true
            // Set the lastUpdateRequested value to trigger a weather update in the KarooHeadwindExtension
            val settings = ctx.streamSettings(karooSystem).first()
            saveSettings(ctx, settings.copy(lastUpdateRequested = System.currentTimeMillis()))
            delay(1000) // Give some time to show the refreshing indicator
            isRefreshing = false
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(2000) // Timeout after 2 seconds if the refresh doesn't complete
            isRefreshing = false
        }
    }

    fun onFinish() {
        if (tabIndex > 0) {
            tabIndex--
        } else {
            close()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            karooSystem.disconnect()
        }
    }

    LaunchedEffect(Unit) {
        ctx.streamSettings(karooSystem).collect { settings ->
            welcomeDialogVisible = !settings.welcomeDialogAccepted
        }
    }

    LaunchedEffect(Unit) {
        karooSystem.connect { connected ->
            karooConnected = connected
        }
    }

    LaunchedEffect(karooConnected) {
        if (karooConnected && updateInfo == null && !updateNotificationShown) {
            try {
                val updateInfoResult = Updater.checkForUpdate(ctx, karooSystem)

                if (updateInfoResult != null) {
                    updateNotificationShown = true
                    updateInfo = updateInfoResult
                }
            } catch (e: Exception) {
                Log.w(KarooHeadwindExtension.TAG, "Update check failed", e)
            }
        }
    }

    PullToRefreshBox(
        modifier = Modifier.fillMaxSize(),
        isRefreshing = isRefreshing,
        onRefresh = { refreshData() }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {

            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = tabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            text = { Text(title) },
                            selected = tabIndex == index,
                            onClick = { tabIndex = index }
                        )
                    }
                }
                when (tabIndex) {
                    0 -> WeatherScreen(::onFinish)
                    1 -> SettingsScreen(::onFinish)
                    2 -> WindyScreen(::onFinish)
                }
            }
        }

        if (welcomeDialogVisible) {
            AlertDialog(
                onDismissRequest = { },
                confirmButton = {
                    Button(onClick = {
                        coroutineScope.launch {
                            saveSettings(ctx, HeadwindSettings(welcomeDialogAccepted = true))
                        }
                    }) { Text("OK") }
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Welcome to karoo-headwind!")

                        Spacer(Modifier.padding(10.dp))

                        Text("You can add headwind direction and other fields to your data pages in your profile settings.")

                        Spacer(Modifier.padding(10.dp))

                        Text("Please note that this app periodically fetches weather data from OpenMeteo for your current location.")
                    }
                }
            )
        }

        if (!welcomeDialogVisible) {
            updateInfo?.let { update ->
                AlertDialog(
                    onDismissRequest = { updateInfo = null },
                    confirmButton = {
                        Button(onClick = {
                            updateInfo = null
                            openAppInfoInSettings(ctx)
                        }) { Text("Update") }
                    },
                    dismissButton = {
                        Button(onClick = { updateInfo = null }) { Text("Later") }
                    },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text("A new version of karoo-headwind is available.")

                            Spacer(Modifier.padding(10.dp))

                            Text("Version ${update.latestVersion} (you are running ${update.currentVersion}).")

                            if (update.releaseNotes != null) {
                                Spacer(Modifier.padding(10.dp))

                                Text("Release notes:")

                                Spacer(Modifier.padding(4.dp))

                                Text(update.releaseNotes)
                            }
                        }
                    }
                )
            }
        }

        // Do not show back button on the Windy tab
        if (tabIndex != 2) {
            Image(
                painter = painterResource(id = R.drawable.back),
                contentDescription = "Back",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 10.dp)
                    .size(54.dp)
                    .clickable {
                        onFinish()
                    }
            )
        }
    }
}

private const val SETTINGS_APP_PACKAGE = "io.hammerhead.settingsapp"
private const val APP_INFO_ACTION = "io.hammerhead.action.APP_INFO"

private fun openAppInfoInSettings(context: Context) {
    val intent = Intent(APP_INFO_ACTION).apply {
        setPackage(SETTINGS_APP_PACKAGE)
        Updater.getManifestUrl(context)?.let { putExtra("manifestUrl", it) }
    }

    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(KarooHeadwindExtension.TAG, "Could not open the settings app", e)
    }
}