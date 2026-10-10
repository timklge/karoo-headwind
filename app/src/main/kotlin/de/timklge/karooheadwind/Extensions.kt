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

package de.timklge.karooheadwind

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ActiveRidePage
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform

fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> {
    return callbackFlow {
        val listenerId = addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
            trySendBlocking(event.state)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

fun KarooSystemService.streamNavigationState(): Flow<OnNavigationState> {
    return callbackFlow {
        val listenerId = addConsumer { event: OnNavigationState ->
            trySendBlocking(event)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

fun KarooSystemService.streamRideState(): Flow<RideState> {
    return callbackFlow {
        val listenerId = addConsumer { event: RideState ->
            trySendBlocking(event)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

fun<T> Flow<T>.throttle(timeout: Long): Flow<T> = this
    .conflate()
    .transform {
        emit(it)
        delay(timeout)
    }

fun KarooSystemService.streamActiveRidePage(): Flow<ActiveRidePage> {
    return callbackFlow {
        val listenerId = addConsumer { activeRidePage: ActiveRidePage ->
            trySendBlocking(activeRidePage)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

fun KarooSystemService.streamDatatypeIsVisible(
    datatype: String,
): Flow<Boolean> {
    return streamActiveRidePage().map { page ->
        page.page.elements.any { it.dataTypeId == datatype }
    }
}