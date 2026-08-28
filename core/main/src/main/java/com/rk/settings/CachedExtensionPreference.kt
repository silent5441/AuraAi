package com.rk.settings

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Suppress("UNCHECKED_CAST")
class CachedExtensionPreference<T>(
    private val extensionId: String,
    private val key: String,
    private val defaultValue: T,
) : ReadWriteProperty<Any?, T> {

    private val prefKey: String
        get() = "$extensionId.$key"

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return when (defaultValue) {
            is Boolean -> Preference.getBoolean(prefKey, defaultValue) as T
            is String -> Preference.getString(prefKey, defaultValue) as T
            is Int -> Preference.getInt(prefKey, defaultValue) as T
            is Long -> Preference.getLong(prefKey, defaultValue) as T
            is Float -> Preference.getFloat(prefKey, defaultValue) as T
            else -> throw IllegalArgumentException("Unsupported preference type")
        }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        when (value) {
            is Boolean -> Preference.setBoolean(prefKey, value)
            is String -> Preference.setString(prefKey, value)
            is Int -> Preference.setInt(prefKey, value)
            is Long -> Preference.setLong(prefKey, value)
            is Float -> Preference.setFloat(prefKey, value)
            else -> throw IllegalArgumentException("Unsupported preference type")
        }
    }
}