package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.util.Log
import de.timklge.karooheadwind.HeadingResponse
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.WeatherDataProvider
import de.timklge.karooheadwind.getRelativeHeadingFlow
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamDataFlow
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.streamUserProfile
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.UserProfile
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

    companion object {
        // Default physical constants - adjust as needed
        const val DEFAULT_GRAVITY = 9.80665 // Acceleration due to gravity (m/s^2)
        const val DEFAULT_AIR_DENSITY = 1.225 // Air density at sea level, 15°C (kg/m^3)
        const val DEFAULT_CDA = 0.32 // Default coefficient of drag * frontal area (m^2). Varies significantly with rider position and equipment.
        const val DEFAULT_BIKE_WEIGHT = 10.0 // Default bike weight (kg).

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
            // --- Input Validation ---
            if (totalMass <= 0.0 || riderSpeed < 0.0 || windSpeed < 0.0 || g <= 0.0 || airDensity < 0.0 || cda < 0.0) {
                Log.w(KarooHeadwindExtension.TAG, "Warning: Invalid input parameters. Mass/g must be positive; speeds, airDensity, Cda must be non-negative.")
                return Double.NaN
            }
            if (riderSpeed == 0.0 && windSpeed == 0.0) {
                // If no movement and no wind, relative grade is just the actual grade
                return actualGrade
            }

            // 1. Calculate the component of wind speed parallel to the rider's direction of travel.
            //    cos(0 rad) = 1 (headwind), cos(PI rad) = -1 (tailwind)
            val windComponentParallel = windSpeed * cos(Math.toRadians(windDirectionDegrees))

            // 2. Calculate the effective air speed the rider experiences.
            //    This is rider speed + the parallel wind component.
            val effectiveAirSpeed = riderSpeed + windComponentParallel

            // 3. Calculate the aerodynamic resistance factor constant part.
            val aeroFactor = 0.5 * airDensity * cda

            // 4. Calculate the gravitational force component denominator.
            val gravitationalFactor = totalMass * g

            // 5. Calculate the difference in the aerodynamic drag force term between
            //    the current situation (with wind) and the hypothetical no-wind situation.
            //    Drag Force = aeroFactor * effectiveAirSpeed * abs(effectiveAirSpeed)
            //    We use speed * abs(speed) to ensure drag always opposes relative air motion.
            val dragForceDifference = aeroFactor * ( (effectiveAirSpeed * abs(effectiveAirSpeed)) - (riderSpeed * abs(riderSpeed)) )

            // 6. Calculate the relative grade.
            //    It's the actual grade plus the equivalent grade change caused by the wind.
            //    Equivalent Grade Change = Drag Force Difference / Gravitational Force Component
            val relativeGrade = actualGrade + (dragForceDifference / gravitationalFactor)

            return relativeGrade
        }

        fun streamRelativeGrade(karooSystemService: KarooSystemService, context: Context): Flow<RelativeGradeResponse> {
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

            val windSpeedFlow = combine(context.streamSettings(karooSystemService), karooSystemService.streamUserProfile(), context.streamCurrentWeatherData(karooSystemService).filterNotNull()) { settings, profile, weatherData ->
                val isOpenMeteo = settings.weatherProvider == WeatherDataProvider.OPEN_METEO
                val profileIsImperial = profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL

                if (isOpenMeteo) {
                    if (profileIsImperial) { // OpenMeteo returns wind speed in mph
                        val windSpeedInMilesPerHour = weatherData.windSpeed

                        windSpeedInMilesPerHour * 0.44704
                    } else { // Wind speed reported by openmeteo is in km/h
                        val windSpeedInKmh = weatherData.windSpeed

                        windSpeedInKmh * 0.277778
                    }
                } else {
                    if (profileIsImperial) { // OpenWeatherMap returns wind speed in mph
                        val windSpeedInMilesPerHour = weatherData.windSpeed

                        windSpeedInMilesPerHour * 0.44704
                    } else { // Wind speed reported by openweathermap is in m/s
                        weatherData.windSpeed
                    }
                }
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
            }.distinctUntilChanged().map { (windDirection, speed, windSpeed, actualGrade, totalMass) ->
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