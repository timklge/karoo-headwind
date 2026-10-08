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

package de.timklge.karooheadwind.aidl

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.util.Log
import de.timklge.karooheadwind.ForecastRecord
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.IHeadwindCallback
import de.timklge.karooheadwind.IHeadwindService
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.aidl.model.HeadwindSnapshot
import de.timklge.karooheadwind.aidl.model.buildHeadwindSnapshot
import de.timklge.karooheadwind.aidl.model.headwindSnapshotJson
import de.timklge.karooheadwind.streamForecastRecord
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.streamUserProfile
import de.timklge.karooheadwind.throttle
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Exposes the current weather as a JSON [de.timklge.karooheadwind.aidl.model.HeadwindSnapshot] stream over AIDL.
 *
 * Bind with the action `de.timklge.karooheadwind.HEADWIND_SERVICE` and the package `de.timklge.karooheadwind`,
 * then call [de.timklge.karooheadwind.IHeadwindService.registerCallback]. The service only reads cached data and never fetches weather itself.
 */
class HeadwindService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val callbacks = RemoteCallbackList<IHeadwindCallback>()

    @Volatile
    private var latestSnapshotJson: String? = null

    private var karooSystem: KarooSystemService? = null
    private var streamJob: Job? = null

    private val binder = object : IHeadwindService.Stub() {
        override fun registerCallback(callback: IHeadwindCallback) {
            callbacks.register(callback)
            Log.i(KarooHeadwindExtension.TAG, "Registered headwind callback, ${callbacks.registeredCallbackCount} callback(s) total")
            latestSnapshotJson?.let { json ->
                try {
                    callback.onSnapshot(json)
                    Log.d(KarooHeadwindExtension.TAG, "Delivered latest snapshot to new callback")
                } catch (e: RemoteException) {
                    Log.w(KarooHeadwindExtension.TAG, "Failed to deliver snapshot to new callback", e)
                }
            }
        }

        override fun unregisterCallback(callback: IHeadwindCallback) {
            callbacks.unregister(callback)
            Log.i(KarooHeadwindExtension.TAG, "Unregistered headwind callback, ${callbacks.registeredCallbackCount} callback(s) remaining")
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.i(KarooHeadwindExtension.TAG, "HeadwindService bound by ${intent.action ?: "unknown action"}")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(KarooHeadwindExtension.TAG, "HeadwindService created")

        val system = KarooSystemService(applicationContext)
        karooSystem = system
        system.connect()
        Log.d(KarooHeadwindExtension.TAG, "Connecting to Karoo system from HeadwindService")

        streamJob = scope.launch {
            streamSnapshots(system).collect { snapshot ->
                val json = headwindSnapshotJson.encodeToString(snapshot)
                latestSnapshotJson = json
                Log.d(KarooHeadwindExtension.TAG, "Built headwind snapshot: $json")
                broadcast(json)
            }
        }
    }

    override fun onDestroy() {
        Log.i(KarooHeadwindExtension.TAG, "HeadwindService destroying")

        streamJob?.cancel()
        streamJob = null

        karooSystem?.disconnect()
        karooSystem = null

        callbacks.kill()
        scope.cancel()

        super.onDestroy()
        Log.i(KarooHeadwindExtension.TAG, "HeadwindService destroyed")
    }

    private fun streamSnapshots(karooSystem: KarooSystemService): Flow<HeadwindSnapshot> {
        return combine(
            streamForecastRecord(),
            streamSettings(karooSystem),
            karooSystem.streamUserProfile(),
        ) { record: ForecastRecord, settings: HeadwindSettings, profile: UserProfile ->
            val isImperial = profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
            buildHeadwindSnapshot(record.stats, record.response, settings.getWindUnit(isImperial))
        }.distinctUntilChanged()
            .throttle(1_000L)
            .catch { e ->
                Log.e(KarooHeadwindExtension.TAG, "Snapshot stream failed", e)
                throw e
            }
    }

    private fun broadcast(json: String) {
        val count = callbacks.beginBroadcast()
        Log.d(KarooHeadwindExtension.TAG, "Broadcasting snapshot to $count callback(s)")
        try {
            for (i in 0 until count) {
                try {
                    callbacks.getBroadcastItem(i).onSnapshot(json)
                } catch (e: RemoteException) {
                    Log.w(KarooHeadwindExtension.TAG, "Failed to deliver snapshot", e)
                }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }
}
