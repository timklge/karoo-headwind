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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ServiceStatusSingleton private constructor() {
    companion object {
        private var instance: ServiceStatusSingleton? = null

        @Synchronized
        fun getInstance(): ServiceStatusSingleton {
            if (instance == null) {
                instance = ServiceStatusSingleton()
            }
            return instance as ServiceStatusSingleton
        }
    }

    private val serviceStatus: MutableStateFlow<Boolean> = MutableStateFlow(false)

    fun getServiceStatus(): StateFlow<Boolean> {
        return serviceStatus
    }

    fun setServiceStatus(status: Boolean) {
        serviceStatus.value = status
    }
}