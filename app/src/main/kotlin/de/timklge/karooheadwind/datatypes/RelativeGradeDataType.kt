package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.util.Log
import de.timklge.karooheadwind.HeadingResponse
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.getRelativeHeadingFlow
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamDataFlow
import de.timklge.karooheadwind.streamUserProfile
import de.timklge.karooheadwind.throttle
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos

class RelativeGradeDataType(private val karooSystemService: KarooSystemService, private val context: Context): DataTypeImpl("karoo-headwind", "relativeGrade") {
    data class RelativeGradeResponse(val relativeGrade: Double, val actualGrade: Double, val riderSpeed: Double)

    data class ResistanceForces(
        val airResistanceWithoutWind: Double,
        val airResistanceWithWind: Double,
        val rollingResistance: Double,
        val gravitationalForce: Double
    )

    companion object {
        // Default physical constants - adjust as needed
        const val DEFAULT_GRAVITY = 9.80665 // Acceleration due to gravity (m/s^2)
        const val DEFAULT_AIR_DENSITY = 1.225 // Air density at sea level, 15°C (kg/m^3)
        const val DEFAULT_CDA = 0.4 // Default coefficient of drag * frontal area (m^2). Varies significantly with rider position and equipment.
        const val DEFAULT_BIKE_WEIGHT = 9.0 // Default bike weight (kg).
        const val DEFAULT_CRR = 0.005 // Default coefficient of rolling resistance

        /**
         * Estimates the various resistance forces acting on a cyclist.
         *
         * @param actualGrade The current gradient of the road (unitless, e.g., 0.05 for 5%).
         * @param riderSpeed The speed of the rider relative to the ground (m/s). Must be non-negative.
         * @param windSpeed The speed of the wind relative to the ground (m/s). Must be non-negative.
         * @param windDirectionDegrees The direction of the wind relative to the rider's direction
         *                             of travel (degrees).
         *                             0 = direct headwind, 90 = crosswind right,
         *                             180 = direct tailwind, 270 = crosswind left.
         * @param totalMass The combined mass of the rider and the bike (kg). Must be positive.
         * @param cda The rider's coefficient of drag multiplied by their frontal area (m^2).
         *            Defaults to DEFAULT_CDA. Represents aerodynamic efficiency.
         * @param crr The coefficient of rolling resistance. Defaults to DEFAULT_CRR.
         * @param airDensity The density of the air (kg/m^3). Defaults to DEFAULT_AIR_DENSITY.
         * @param g The acceleration due to gravity (m/s^2). Defaults to DEFAULT_GRAVITY.
         * @return A [ResistanceForces] object containing the calculated forces, or null
         *         if input parameters are invalid.
         */
        fun estimateResistanceForces(
            actualGrade: Double,
            riderSpeed: Double,
            windSpeed: Double,
            windDirectionDegrees: Double,
            totalMass: Double,
            cda: Double = DEFAULT_CDA,
            crr: Double = DEFAULT_CRR,
            airDensity: Double = DEFAULT_AIR_DENSITY,
            g: Double = DEFAULT_GRAVITY
        ): ResistanceForces? {
            // --- Input Validation ---
            if (totalMass <= 0.0 || riderSpeed < 0.0 || windSpeed < 0.0 || g <= 0.0 || airDensity < 0.0 || cda < 0.0 || crr < 0.0) {
                Log.w(KarooHeadwindExtension.TAG, "Warning: Invalid input parameters for force calculation.")
                return null
            }

            // 1. Calculate wind component parallel to rider's direction
            val windComponentParallel = windSpeed * cos(Math.toRadians(windDirectionDegrees))

            // 2. Calculate effective air speed
            val effectiveAirSpeed = riderSpeed + windComponentParallel

            // 3. Calculate aerodynamic resistance factor
            val aeroFactor = 0.5 * airDensity * cda

            // 4. Calculate air resistance forces
            // Drag Force = aeroFactor * speed^2 * sign(speed)
            val airResistanceWithWind = aeroFactor * effectiveAirSpeed * abs(effectiveAirSpeed)
            val airResistanceWithoutWind = aeroFactor * riderSpeed * abs(riderSpeed)

            // 5. Calculate gravitational force (force due to slope)
            // Decomposing the gravitational force along the slope
            val gravitationalForce = totalMass * g * actualGrade

            // 6. Calculate rolling resistance force
            // This is simplified; in reality, it's perpendicular to the road surface.
            // F_rolling = Crr * N = Crr * m * g * cos(arctan(grade))
            // For small angles, cos(arctan(grade)) is close to 1, so we approximate.
            val rollingResistance = totalMass * g * crr

            return ResistanceForces(
                airResistanceWithoutWind = airResistanceWithoutWind,
                airResistanceWithWind = airResistanceWithWind,
                rollingResistance = rollingResistance,
                gravitationalForce = gravitationalForce
            )
        }

        /**
         * Estimates the "relative grade" experienced by a cyclist.
         *
         * Relative grade is the hypothetical grade (%) at which the rider would experience
         * the same total resistance force as they currently experience, but under the
         * assumption of zero wind. It quantifies the perceived effort due to wind in
         * terms of an equivalent slope.
         *
         * @param actualGrade The current gradient of the road (unitless, e.g., 0.05 for 5%).
         * @param riderSpeed The speed of the rider relative to the ground (m/s). Must be non-negative.
         * @param windSpeed The speed of the wind relative to the ground (m/s). Must be non-negative.
         * @param windDirectionDegrees The direction of the wind relative to the rider's direction
         *                             of travel (degrees).
         *                             0 = direct headwind, 90 = crosswind right,
         *                             180 = direct tailwind, 270 = crosswind left.
         * @param totalMass The combined mass of the rider and the bike (kg). Must be positive.
         * @param cda The rider's coefficient of drag multiplied by their frontal area (m^2).
         *            Defaults to DEFAULT_CDA. Represents aerodynamic efficiency.
         * @param airDensity The density of the air (kg/m^3). Defaults to DEFAULT_AIR_DENSITY.
         * @param g The acceleration due to gravity (m/s^2). Defaults to DEFAULT_GRAVITY.
         * @return The calculated relative grade (unitless, e.g., 0.08 for 8%), or Double.NaN
         *         if input parameters are invalid.
         */
        fun estimateRelativeGrade(
            actualGrade: Double,
            riderSpeed: Double,
            windSpeed: Double,
            windDirectionDegrees: Double,
            totalMass: Double,
            cda: Double = DEFAULT_CDA,
            airDensity: Double = DEFAULT_AIR_DENSITY,
            g: Double = DEFAULT_GRAVITY,
        ): Double {
            val forces = estimateResistanceForces(
                actualGrade,
                riderSpeed,
                windSpeed,
                windDirectionDegrees,
                totalMass,
                cda,
                DEFAULT_CRR,
                airDensity,
                g
            )

            if (forces == null) {
                Log.w(KarooHeadwindExtension.TAG, "Could not calculate forces for relative grade.")
                return Double.NaN
            }

            if (riderSpeed == 0.0 && windSpeed == 0.0) {
                // If no movement and no wind, relative grade is just the actual grade
                return actualGrade
            }

            // The difference in force is purely from the wind.
            // This difference in force, when equated to a change in gravitational force, gives the change in grade.
            // delta_F_air = F_air_with_wind - F_air_without_wind
            // delta_F_air = m * g * delta_grade
            // delta_grade = delta_F_air / (m * g)
            // relative_grade = actual_grade + delta_grade
            val dragForceDifference = forces.airResistanceWithWind - forces.airResistanceWithoutWind
            val gravitationalFactor = totalMass * g

            if (gravitationalFactor == 0.0) {
                return actualGrade // Avoid division by zero
            }

            return actualGrade + (dragForceDifference / gravitationalFactor)
        }

        suspend fun streamRelativeGrade(karooSystemService: KarooSystemService, context: Context): Flow<RelativeGradeResponse> {
            val relativeWindDirectionFlow = karooSystemService.getRelativeHeadingFlow(context).filterIsInstance<HeadingResponse.Value>().map { it.diff + 180 }
            val speedFlow = karooSystemService.streamDataFlow(DataType.Type.SPEED).filterIsInstance<StreamState.Streaming>().map { it.dataPoint.singleValue ?: 0.0 }
            val actualGradeFlow = karooSystemService.streamDataFlow(DataType.Type.ELEVATION_GRADE).filterIsInstance<StreamState.Streaming>().map { it.dataPoint.singleValue }.filterNotNull().map { it / 100.0 } // Convert to decimal grade
            val totalMassFlow = karooSystemService.streamUserProfile().map {
                if (it.weight in 30.0f..300.0f){
                    it.weight
                } else {
                    Log.w(KarooHeadwindExtension.TAG, "Invalid rider weight ${it.weight} kg, defaulting to 70 kg")
                    70.0f // Default to 70 kg if weight is invalid
                } + DEFAULT_BIKE_WEIGHT
            }

            val refreshRate = karooSystemService.getRefreshRateInMilliseconds(context)

            val windSpeedFlow = context.streamCurrentWeatherData(karooSystemService).filterNotNull().map { weatherData ->
                weatherData.windSpeed
            }

            data class StreamValues(
                val relativeWindDirection: Double,
                val speed: Double,
                val windSpeed: Double,
                val actualGrade: Double,
                val totalMass: Double
            )

            return combine(relativeWindDirectionFlow, speedFlow, windSpeedFlow, actualGradeFlow, totalMassFlow) { windDirection, speed, windSpeed, actualGrade, totalMass ->
                StreamValues(windDirection, speed, windSpeed, actualGrade, totalMass)
            }.distinctUntilChanged().throttle(refreshRate).map { (windDirection, speed, windSpeed, actualGrade, totalMass) ->
                val relativeGrade = estimateRelativeGrade(actualGrade, speed, windSpeed, windDirection, totalMass)

                Log.d(KarooHeadwindExtension.TAG, "Relative grade: $relativeGrade - Wind Direction: $windDirection - Speed: $speed - Wind Speed: $windSpeed - Actual Grade: $actualGrade - Total Mass: $totalMass")

                RelativeGradeResponse(relativeGrade, actualGrade, speed)
            }
        }
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val relativeGradeFlow = streamRelativeGrade(karooSystemService, context)

            relativeGradeFlow.collect { response ->
                emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to response.relativeGrade * 100))))
            }
        }
        emitter.setCancellable {
            Log.d(KarooHeadwindExtension.TAG, "stop $dataTypeId stream")
            job.cancel()
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(formatDataTypeId = DataType.Type.ELEVATION_GRADE))
    }
}