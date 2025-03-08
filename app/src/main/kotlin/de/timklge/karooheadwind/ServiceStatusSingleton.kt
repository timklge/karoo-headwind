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