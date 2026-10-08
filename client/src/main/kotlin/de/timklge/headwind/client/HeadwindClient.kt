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

package de.timklge.headwind.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import de.timklge.karooheadwind.IHeadwindCallback
import de.timklge.karooheadwind.IHeadwindService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.util.concurrent.atomic.AtomicReference

/**
 * Streams the current weather from karoo-headwind.
 *
 * ```
 * val client = HeadwindClient(context)
 * client.snapshots().collect { snapshot -> ... }
 * ```
 *
 * [snapshots] binds to karoo-headwind when collection starts and unbinds when collection is cancelled.
 * The latest snapshot is delivered right away and again on every change. If the collector is slow, intermediate snapshots are skipped.
 */
class HeadwindClient(context: Context) {
    private val appContext = context.applicationContext

    /** Returns true if karoo-headwind is installed on this device. */
    fun isInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(SERVICE_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(SERVICE_PACKAGE, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Cold flow of [HeadwindSnapshot]s. Fails with [HeadwindServiceUnavailableException] if karoo-headwind cannot be bound.
     */
    fun snapshots(): Flow<HeadwindSnapshot> = callbackFlow<HeadwindSnapshot> {
        val serviceRef = AtomicReference<IHeadwindService?>(null)

        val callback = object : IHeadwindCallback.Stub() {
            override fun onSnapshot(snapshotJson: String) {
                try {
                    trySend(HeadwindSnapshot.fromJson(snapshotJson))
                } catch (e: Exception) {
                    Log.w(TAG, "Ignoring malformed snapshot", e)
                }
            }
        }

        fun bind(connection: ServiceConnection): Boolean {
            return appContext.bindService(serviceIntent(), connection, Context.BIND_AUTO_CREATE)
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val service = IHeadwindService.Stub.asInterface(binder)
                serviceRef.set(service)
                try {
                    service.registerCallback(callback)
                } catch (e: RemoteException) {
                    close(HeadwindServiceUnavailableException("Failed to register with karoo-headwind", e))
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                // The service process died. The binding stays active, so Android reconnects and onServiceConnected registers again.
                serviceRef.set(null)
            }

            override fun onBindingDied(name: ComponentName) {
                // The binding is no longer valid and must be recreated.
                serviceRef.set(null)
                appContext.unbindService(this)
                if (!bind(this)) {
                    close(HeadwindServiceUnavailableException("Failed to rebind to karoo-headwind"))
                }
            }
        }

        if (!bind(connection)) {
            close(HeadwindServiceUnavailableException("karoo-headwind is not installed or its service is unavailable"))
        }

        awaitClose {
            serviceRef.getAndSet(null)?.let { service ->
                try {
                    service.unregisterCallback(callback)
                } catch (e: RemoteException) {
                    Log.w(TAG, "Failed to unregister callback", e)
                }
            }
            try {
                appContext.unbindService(connection)
            } catch (e: IllegalArgumentException) {
                // Binding was never established or has already been released.
            }
        }
    }.conflate()

    private fun serviceIntent(): Intent {
        return Intent(SERVICE_ACTION).setComponent(ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
    }

    companion object {
        /** Package name of karoo-headwind. */
        const val SERVICE_PACKAGE = "de.timklge.karooheadwind"

        /** Intent action of the karoo-headwind service. */
        const val SERVICE_ACTION = "de.timklge.karooheadwind.HEADWIND_SERVICE"

        private const val SERVICE_CLASS = "de.timklge.karooheadwind.aidl.HeadwindService"
        private const val TAG = "HeadwindClient"
    }
}

/** Thrown by [HeadwindClient.snapshots] when karoo-headwind cannot be bound. */
class HeadwindServiceUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
