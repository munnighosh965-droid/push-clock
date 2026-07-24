package com.powerclock.alarm.alarmengine

import com.powerclock.alarm.R

/**
 * Static mapping from catalog sound names to raw resource ids. Explicit
 * R.raw references are immune to resource shrinking and name obfuscation,
 * unlike getIdentifier() lookups.
 */
object SoundResources {
    private val byName = mapOf(
        "alarm_reactor" to R.raw.alarm_reactor,
        "alarm_power_pulse" to R.raw.alarm_power_pulse,
        "alarm_digital_siren" to R.raw.alarm_digital_siren,
        "alarm_heavy_bell" to R.raw.alarm_heavy_bell,
        "alarm_morning_horn" to R.raw.alarm_morning_horn,
        "alarm_electric_rise" to R.raw.alarm_electric_rise,
        "alarm_rapid_beep" to R.raw.alarm_rapid_beep,
        "alarm_emergency_buzz" to R.raw.alarm_emergency_buzz,
    )

    const val FALLBACK_RES_ID_NAME = "alarm_reactor"

    fun resIdFor(rawResName: String): Int =
        byName[rawResName] ?: R.raw.alarm_reactor
}
