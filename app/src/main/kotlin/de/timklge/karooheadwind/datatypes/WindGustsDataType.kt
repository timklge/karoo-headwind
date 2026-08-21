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

import android.content.Context
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.util.msInWindUnit
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.UserProfile

class WindGustsDataType(karooSystemService: KarooSystemService, context: Context) : BaseDataType(karooSystemService, context, "windGusts"){
    override fun getValue(data: WeatherData, userProfile: UserProfile, settings: HeadwindSettings): Double {
        val isImperial = userProfile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
        return msInWindUnit(data.windGusts, settings.getWindUnit(isImperial))
    }

    override fun getFormatDataType(): String? {
        return DataType.Type.INTENSITY_FACTOR
    }
}
