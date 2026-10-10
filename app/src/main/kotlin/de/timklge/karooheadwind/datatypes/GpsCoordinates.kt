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

package de.timklge.karooheadwind.datatypes

import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class GpsCoordinates(val lat: Double, val lon: Double, val bearing: Double? = 0.0, val distanceAlongRoute: Double? = null){
    companion object {
        private fun roundDegrees(degrees: Double, km: Double): Double {
            val nkm = degrees * 111
            val rounded = (nkm / km).roundToInt() * km

            return rounded / 111
        }
    }

    fun round(km: Double = 2.0): GpsCoordinates {
        return copy(lat = roundDegrees(lat, km), lon = roundDegrees(lon, km))
    }

    // Haversine formula in kilometers
    fun distanceTo(other: GpsCoordinates): Double {
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)
        val lat2 = Math.toRadians(other.lat)
        val lon2 = Math.toRadians(other.lon)
        val dlat = lat2 - lat1
        val dlon = lon2 - lon1
        val a = sin(dlat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dlon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val r = 6371.0
        val distance = r * c

        return distance
    }
}