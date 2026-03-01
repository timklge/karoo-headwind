package de.timklge.karooheadwind.screens

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.getGpsCoordinateFlow
import de.timklge.karooheadwind.util.buildKarooOkHttpClient
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HardwareType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.module.http.HttpRequestUtil

fun getDistinctCoordinateFlow(karooSystem: KarooSystemService, ctx: android.content.Context) = karooSystem.getGpsCoordinateFlow(ctx).distinctUntilChanged { a, b ->
    (a == null && b == null) || (a != null && b != null && a.distanceTo(b) < 1_000)
}

fun isWifiConnected(context: android.content.Context): kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.callbackFlow {
    val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager

    if (connectivityManager == null) {
        trySend(false)
        close()
        return@callbackFlow
    }

    fun checkWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }

    // Send initial state
    trySend(checkWifiConnected())

    val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            trySend(checkWifiConnected())
        }

        override fun onLost(network: android.net.Network) {
            trySend(checkWifiConnected())
        }

        override fun onCapabilitiesChanged(
            network: android.net.Network,
            networkCapabilities: android.net.NetworkCapabilities
        ) {
            trySend(checkWifiConnected())
        }
    }

    connectivityManager.registerDefaultNetworkCallback(networkCallback)

    awaitClose {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WindyScreen() {
    var karooConnected by remember { mutableStateOf<Boolean?>(null) }
    val ctx = LocalContext.current
    val karooSystem = remember { KarooSystemService(ctx) }
    val isWifiConnected by isWifiConnected(ctx).collectAsStateWithLifecycle(initialValue = false)
    var showWarnings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        karooSystem.connect { connected ->
            karooConnected = connected
        }
    }

    LaunchedEffect(Unit) {
        delay(500)
        showWarnings = true
    }

    DisposableEffect(Unit) {
        onDispose {
            karooSystem.disconnect()
        }
    }

    val location by getDistinctCoordinateFlow(karooSystem, ctx).collectAsStateWithLifecycle(null)

    if (karooConnected == false && showWarnings) {
        Text("Could not read device status. Is your Karoo updated?", modifier = Modifier.padding(10.dp))
        return
    }

    if (!isWifiConnected) {
        if (karooSystem.hardwareType == HardwareType.K2) {
            if (showWarnings) Text("Please connect to WiFi / cellular to show the windy map.", modifier = Modifier.padding(10.dp))
            return
        } else {
            if (showWarnings) Text("Please connect to WiFi to show the windy map.", modifier = Modifier.padding(10.dp))
            return
        }
    }

    // Create MapLibre view with current location
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            MapLibre.getInstance(ctx)
            HttpRequestUtil.setOkHttpClient(buildKarooOkHttpClient(karooSystem))

            MapView(context).apply {
                getMapAsync { map ->
                    // Set initial style
                    map.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                        Log.i(KarooHeadwindExtension.TAG, "MapLibre style loaded")
                    }

                    location?.let {
                        map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                            .target(LatLng(it.lat, it.lon))
                            .zoom(9.0)
                            .build()
                    }
                }
            }
        },
        update = { mapView ->
            // Update map when location changes
            mapView.getMapAsync { map ->
                location?.let {
                    map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                        .target(LatLng(it.lat, it.lon))
                        .zoom(7.0)
                        .build()
                }
            }
        },
        onRelease = { mapView ->
            mapView.onDestroy()
        },
    )
}