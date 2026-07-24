package com.powerclock.alarm.domain.audio

/**
 * The eight bundled, original Power Clock alarm tones. All tones were
 * synthesized specifically for this app (see tools/generate_sounds.py) and
 * are royalty-free. "reactor" doubles as the guaranteed fallback tone.
 */
data class BuiltInSound(
    val id: String,
    val displayName: String,
    val rawResName: String,
)

object SoundCatalog {

    const val FALLBACK_ID = "reactor"
    const val CUSTOM_ID = "custom"

    val sounds: List<BuiltInSound> = listOf(
        BuiltInSound("reactor", "Reactor", "alarm_reactor"),
        BuiltInSound("power_pulse", "Power Pulse", "alarm_power_pulse"),
        BuiltInSound("digital_siren", "Digital Siren", "alarm_digital_siren"),
        BuiltInSound("heavy_bell", "Heavy Bell", "alarm_heavy_bell"),
        BuiltInSound("morning_horn", "Morning Horn", "alarm_morning_horn"),
        BuiltInSound("electric_rise", "Electric Rise", "alarm_electric_rise"),
        BuiltInSound("rapid_beep", "Rapid Beep", "alarm_rapid_beep"),
        BuiltInSound("emergency_buzz", "Emergency Buzz", "alarm_emergency_buzz"),
    )

    fun byId(id: String?): BuiltInSound =
        sounds.firstOrNull { it.id == id } ?: sounds.first { it.id == FALLBACK_ID }

    fun randomSound(exceptId: String? = null): BuiltInSound =
        sounds.filter { it.id != exceptId }.random()
}
