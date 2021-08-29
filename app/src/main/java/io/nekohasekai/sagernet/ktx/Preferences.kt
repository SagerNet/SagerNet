/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.ktx

import androidx.preference.PreferenceDataStore
import cn.hutool.core.util.NumberUtil
import kotlin.reflect.KProperty

fun PreferenceDataStore.string(
    name: String,
    defaultValue: () -> String = { "" },
) = PreferenceProxy(name, defaultValue, ::getString, ::putString)

fun PreferenceDataStore.boolean(
    name: String,
    defaultValue: () -> Boolean = { false },
) = PreferenceProxy(name, defaultValue, ::getBoolean, ::putBoolean)

fun PreferenceDataStore.int(
    name: String,
    defaultValue: () -> Int = { 0 },
) = PreferenceProxy(name, defaultValue, ::getInt, ::putInt)

fun PreferenceDataStore.stringToInt(
    name: String,
    defaultValue: () -> Int = { 0 },
) = PreferenceProxy(name, defaultValue, { key, default ->
    getString(key, "$default")?.takeIf { NumberUtil.isInteger(it) }?.toInt() ?: default
}, { key, value -> putString(key, "$value") })

fun PreferenceDataStore.stringToIntIfExists(
    name: String,
    defaultValue: () -> Int = { 0 },
) = PreferenceProxy(name, defaultValue, { key, default ->
    getString(key, "$default")?.takeIf { NumberUtil.isInteger(it) }?.toInt() ?: default
}, { key, value -> putString(key, value.takeIf { it > 0 }?.toString() ?: "") })

fun PreferenceDataStore.long(
    name: String,
    defaultValue: () -> Long = { 0L },
) = PreferenceProxy(name, defaultValue, ::getLong, ::putLong)

fun PreferenceDataStore.stringToLong(
    name: String,
    defaultValue: () -> Long = { 0L },
) = PreferenceProxy(name, defaultValue, { key, default ->
    getString(key, "$default")?.takeIf { NumberUtil.isLong(it) }?.toLong() ?: default
}, { key, value -> putString(key, "$value") })

class PreferenceProxy<T>(
    val name: String,
    val defaultValue: () -> T,
    val getter: (String, T) -> T?,
    val setter: (String, value: T) -> Unit,
) {

    operator fun setValue(thisObj: Any?, property: KProperty<*>, value: T) = setter(name, value)
    operator fun getValue(thisObj: Any?, property: KProperty<*>) = getter(name, defaultValue())!!

}