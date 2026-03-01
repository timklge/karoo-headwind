package de.timklge.karooheadwind.screens

import android.Manifest
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.layer.labels.LabelLayer
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes


val OFFLINE_MAPS_DIR get() = java.io.File(
    java.io.File(Environment.getExternalStorageDirectory(), "offline"), "maps"
)

fun getDistinctCoordinateFlow(karooSystem: KarooSystemService, ctx: android.content.Context) =
    karooSystem.getGpsCoordinateFlow(ctx).distinctUntilChanged { a, b ->
        (a == null && b == null) || (a != null && b != null && a.distanceTo(b) < 1_000)
    }

fun hasExternalStoragePermission(context: Context): Boolean {
    val readGranted = context.checkCallingOrSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    val writeGranted = context.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

    return readGranted == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            writeGranted == android.content.pm.PackageManager.PERMISSION_GRANTED
}

@Composable
fun MapScreen() {
    var karooConnected by remember { mutableStateOf<Boolean?>(null) }
    val ctx = LocalContext.current
    val karooSystem = remember { KarooSystemService(ctx) }
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

    val location by getDistinctCoordinateFlow(karooSystem, ctx)
        .collectAsStateWithLifecycle(null)

    if (karooConnected == false && showWarnings) {
        Text(
            "Could not read device status. Is your Karoo updated?",
            modifier = Modifier.padding(10.dp)
        )
        return
    }

    if (!hasExternalStoragePermission(ctx)) {
        Text(
            "Storage permission not granted. Please grant storage permission to display the map.",
            modifier = Modifier.padding(10.dp)
        )

        val storagePermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> }

        Button(onClick = {
            storagePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }) {
            Text("Request Permission")
        }
        return
    }

    val mapFiles = remember {
        OFFLINE_MAPS_DIR
            .takeIf { it.isDirectory }
            ?.listFiles { f -> f.extension.lowercase() == "map" }
            ?.toList()
            ?: emptyList()
    }

    if (mapFiles.isEmpty()) {
        Text(
            "No offline map files found. Please place *.map files in ${OFFLINE_MAPS_DIR.absolutePath}.",
            modifier = Modifier.padding(10.dp)
        )
        return
    }

    val androidGraphicFactory = AndroidGraphicFactory.INSTANCE

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            MapView(context).apply {
                isClickable = true
                isFocusable = true
                mapScaleBar.isVisible = false
                setBuiltInZoomControls(false)
                clipToOutline = true
                touchGestureHandler.isRotationEnabled = true;

                AndroidGraphicFactory.createInstance(context)

                val tileCache = AndroidUtil.createTileCache(
                    context,
                    "tilecache",
                    model.displayModel.tileSize,
                    1f,
                    model.frameBufferModel.overdrawFactor
                )

                val multiMapDataStore = MultiMapDataStore(
                    MultiMapDataStore.DataPolicy.RETURN_ALL
                )

                for (file in mapFiles) {
                    try {
                        multiMapDataStore.addMapDataStore(MapFile(file), false, false)
                        Log.i(KarooHeadwindExtension.TAG, "Loaded offline map: ${file.name}")
                    } catch (e: Exception) {
                        Log.e(KarooHeadwindExtension.TAG, "Failed to load map file ${file.name}: ${e.message}")
                    }
                }

                val rendererLayer = TileRendererLayer(
                    tileCache,
                    multiMapDataStore,
                    model.mapViewPosition,
                    false,
                    false,
                    true,
                    androidGraphicFactory
                )

                rendererLayer.setXmlRenderTheme(MapsforgeThemes.DEFAULT)
                layerManager.layers.add(rendererLayer)

                val labelLayer = LabelLayer(AndroidGraphicFactory.INSTANCE, rendererLayer.labelStore)
                layerManager.layers.add(labelLayer)

                //val o = Overlay
                //layerManager.layers.add(o)

                val zoom: Byte = 9
                model.mapViewPosition.mapPosition = org.mapsforge.core.model.MapPosition(
                    location?.let { LatLong(it.lat, it.lon) } ?: LatLong(0.0, 0.0),
                    zoom
                )

                Log.i(KarooHeadwindExtension.TAG, "Mapsforge offline MapView created with ${mapFiles.size} map file(s)")
            }
        },
        update = { mapView ->
            location?.let {
                val currentZoom = mapView.model.mapViewPosition.zoomLevel
                mapView.model.mapViewPosition.center = LatLong(it.lat, it.lon)
                if (currentZoom < 6) {
                    mapView.model.mapViewPosition.zoomLevel = 9
                }
            }
        },
        onRelease = { mapView ->
            mapView.destroyAll()
            AndroidGraphicFactory.clearResourceMemoryCache()
        }
    )
}