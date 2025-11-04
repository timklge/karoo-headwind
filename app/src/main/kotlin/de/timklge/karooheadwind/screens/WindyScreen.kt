package de.timklge.karooheadwind.screens

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
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
import de.timklge.karooheadwind.streamUserProfile
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HardwareType
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale

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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected && networkInfo.type == android.net.ConnectivityManager.TYPE_WIFI
        }
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

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    } else {
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    awaitClose {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WindyScreen(onFinish: () -> Unit) {
    var karooConnected by remember { mutableStateOf<Boolean?>(null) }
    val ctx = LocalContext.current
    val karooSystem = remember { KarooSystemService(ctx) }
    val isWifiConnected by isWifiConnected(ctx).collectAsStateWithLifecycle(initialValue = false)
    var showWarnings by remember { mutableStateOf(false) }
    val profileFlow = remember { karooSystem.streamUserProfile() }
    val profile by profileFlow.collectAsStateWithLifecycle(null)
    val isImperialTemperature = profile?.preferredUnit?.temperature == UserProfile.PreferredUnit.UnitType.IMPERIAL
    val isImperialDistance = profile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL

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

    if (location == null) {
        if (showWarnings) Text("waiting for GPS fix...", modifier = Modifier.padding(10.dp))
        return
    }

    val unitTemp = if (isImperialTemperature) "°F" else "°C"
    val unitWind = if (isImperialDistance) "mph" else "km/h"
    val unitRain = if (isImperialDistance) "in" else "mm"

    // Build Windy embed URL with current location
    val windyUrl = "https://embed.windy.com/embed.html?" +
            "type=map&location=coordinates" +
            "&metricRain=${unitRain}&metricTemp=${unitTemp}&metricWind=${unitWind}" +
            "&zoom=7&overlay=wind&product=ecmwf&level=surface" +
            "&lat=${String.format(Locale.US, "%.5f", location?.lat)}&lon=${String.format(Locale.US, "%.5f", location?.lon)}" +
            "&detail=false&pressure=true"

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.setSupportZoom(false)

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.i(KarooHeadwindExtension.TAG, "Windy page loaded: $url")
                    }
                }

                WebView.setWebContentsDebuggingEnabled(true)

                loadUrl(windyUrl)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        },
    )
}