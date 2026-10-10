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

package de.timklge.karooheadwind.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import de.timklge.karooheadwind.BuildConfig
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.jsonWithUnknownKeys
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.MANIFEST_URL_META
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.KarooAppManifest
import io.hammerhead.karooext.models.OnHttpResponse
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.timeout
import kotlin.time.Duration.Companion.seconds

/**
 * Checks the manifest url configured in the AndroidManifest meta data
 * (io.hammerhead.karooext.MANIFEST_URL) for available application updates.
 */
object Updater {
    data class UpdateInfo(
        val currentVersion: String,
        val latestVersion: String,
        val latestVersionCode: Int,
        val releaseNotes: String?,
    )

    fun getManifestUrl(context: Context): String? {
        return try {
            val applicationInfo = context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            applicationInfo.metaData?.getString(MANIFEST_URL_META)
        } catch (e: Exception) {
            Log.w(KarooHeadwindExtension.TAG, "Failed to read manifest url meta data", e)
            null
        }
    }

    /**
     * Fetches the manifest from the configured manifest url and compares the
     * advertised version with the currently installed version.
     *
     * Returns null if no update is available or the check failed.
     */
    @OptIn(FlowPreview::class)
    suspend fun checkForUpdate(context: Context, karooSystem: KarooSystemService): UpdateInfo? {
        val manifestUrl = getManifestUrl(context)
        if (manifestUrl == null) {
            Log.d(KarooHeadwindExtension.TAG, "No manifest url configured, skipping update check")
            return null
        }

        val response = callbackFlow {
            Log.d(KarooHeadwindExtension.TAG, "Http request to $manifestUrl...")

            val listenerId = karooSystem.addConsumer(
                OnHttpResponse.MakeHttpRequest(
                    "GET",
                    manifestUrl,
                    waitForConnection = true,
                    headers = mapOf("User-Agent" to KarooHeadwindExtension.TAG),
                ),
                onEvent = { event: OnHttpResponse ->
                    if (event.state is HttpResponseState.Complete) {
                        trySend(event.state as HttpResponseState.Complete)
                        close()
                    }
                },
                onError = { err ->
                    close(RuntimeException("Http error while checking for updates: $err"))
                },
            )
            awaitClose { karooSystem.removeConsumer(listenerId) }
        }.timeout(60.seconds).single()

        if (response.statusCode !in 200..299) {
            Log.w(KarooHeadwindExtension.TAG, "Update check failed with status code ${response.statusCode}")
            return null
        }

        val responseBody = response.body?.let { String(it) }
        if (responseBody == null) {
            Log.w(KarooHeadwindExtension.TAG, "Update check failed, empty response")
            return null
        }

        val manifest = try {
            jsonWithUnknownKeys.decodeFromString<KarooAppManifest>(responseBody)
        } catch (e: Exception) {
            Log.w(KarooHeadwindExtension.TAG, "Failed to parse app manifest", e)
            return null
        }

        if (manifest.packageName != context.packageName) {
            Log.w(KarooHeadwindExtension.TAG, "App manifest package name mismatch: ${manifest.packageName}")
            return null
        }

        val currentVersion = BuildConfig.VERSION_NAME
        if (!isUpdateAvailable(BuildConfig.VERSION_CODE, manifest.latestVersionCode)) {
            Log.d(KarooHeadwindExtension.TAG, "No update available, installed version is $currentVersion (${BuildConfig.VERSION_CODE})")
            return null
        }

        Log.i(KarooHeadwindExtension.TAG, "Update available: $currentVersion (${BuildConfig.VERSION_CODE}) -> ${manifest.latestVersion} (${manifest.latestVersionCode})")

        return UpdateInfo(
            currentVersion = currentVersion,
            latestVersion = manifest.latestVersion,
            latestVersionCode = manifest.latestVersionCode,
            releaseNotes = manifest.releaseNotes,
        )
    }

    fun isUpdateAvailable(currentVersionCode: Int, latestVersionCode: Int): Boolean {
        return latestVersionCode > currentVersionCode
    }
}