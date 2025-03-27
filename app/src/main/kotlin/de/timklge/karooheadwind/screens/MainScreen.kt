package de.timklge.karooheadwind.screens

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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import de.timklge.karooheadwind.R
import de.timklge.karooheadwind.saveSettings
import de.timklge.karooheadwind.streamSettings
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(close: () -> Unit) {
    var karooConnected by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val karooSystem = remember { KarooSystemService(ctx) }

    var welcomeDialogVisible by remember { mutableStateOf(false) }
    var tabIndex by remember { mutableIntStateOf(0) }

    var isRefreshing by remember { mutableStateOf(false) }

    val tabs = listOf("Weather", "Settings")

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
        if (tabIndex > 0){
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

    PullToRefreshBox(modifier = Modifier.fillMaxSize(), isRefreshing = isRefreshing, onRefresh = { refreshData() }) {
        Column(modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)) {

            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = tabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(text = { Text(title) },
                            selected = tabIndex == index,
                            onClick = { tabIndex = index }
                        )
                    }
                }
                when (tabIndex) {
                    0 -> WeatherScreen(::onFinish)
                    1 -> SettingsScreen(::onFinish)
                }
            }
        }

        if (welcomeDialogVisible){
            AlertDialog(onDismissRequest = { },
                confirmButton = { Button(onClick = {
                    coroutineScope.launch {
                        saveSettings(ctx, HeadwindSettings(welcomeDialogAccepted = true))
                    }
                }) { Text("OK") } },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Welcome to karoo-headwind!")

                        Spacer(Modifier.padding(10.dp))

                        Text("You can add headwind direction and other fields to your data pages in your profile settings.")

                        Spacer(Modifier.padding(10.dp))

                        Text("Please note that this app periodically fetches data from the OpenMeteo API to know the current weather at your approximate location.")
                    }
                }
            )
        }

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