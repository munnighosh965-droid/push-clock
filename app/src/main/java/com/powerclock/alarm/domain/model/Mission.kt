package com.powerclock.alarm.domain.model

/**
 * Every way Power Clock can ask you to prove you are awake.
 */
enum class MissionType(
    val needsCamera: Boolean,
    val isWorkout: Boolean,
) {
    PUSH_UPS(needsCamera = true, isWorkout = true),
    KNEE_PUSH_UPS(needsCamera = true, isWorkout = true),
    SQUATS(needsCamera = true, isWorkout = true),
    JUMPING_JACKS(needsCamera = true, isWorkout = true),
    MATH(needsCamera = false, isWorkout = false),
    MEMORY(needsCamera = false, isWorkout = false),
    TYPING(needsCamera = false, isWorkout = false),
    QR_SCAN(needsCamera = true, isWorkout = false),
    SHAKE(needsCamera = false, isWorkout = false),
    ;

    companion object {
        /** Missions that never need a camera or physical effort. */
        val SAFE_FALLBACKS = listOf(MATH, TYPING, MEMORY)
        val WORKOUTS = entries.filter { it.isWorkout }
        val BRAIN = listOf(MATH, MEMORY, TYPING)
    }
}

/** Detection strictness for camera workouts. */
enum class Sensitivity { BEGINNER, NORMAL, STRICT }

/**
 * One configured mission inside an alarm's mission stack.
 *
 * @param target repetitions for workouts, questions for math, rounds for
 *   memory, phrases for typing, shakes for shake missions. Ignored for QR.
 * @param difficulty 1 (easy) .. 3 (hard) for brain missions.
 */
data class MissionConfig(
    val type: MissionType,
    val target: Int = defaultTarget(type),
    val difficulty: Int = 1,
    val sensitivity: Sensitivity = Sensitivity.NORMAL,
) {
    init {
        require(target in 1..MAX_TARGET) { "target out of safe range" }
        require(difficulty in 1..3) { "difficulty out of range" }
    }

    fun encode(): String = "${type.name}:$target:$difficulty:${sensitivity.name}"

    companion object {
        /** Hard safety ceiling on any repetition/target value. */
        const val MAX_TARGET = 30
        const val MIN_WORKOUT_TARGET = 3

        fun defaultTarget(type: MissionType): Int = when (type) {
            MissionType.PUSH_UPS, MissionType.KNEE_PUSH_UPS -> 5
            MissionType.SQUATS -> 5
            MissionType.JUMPING_JACKS -> 10
            MissionType.MATH -> 3
            MissionType.MEMORY -> 3
            MissionType.TYPING -> 1
            MissionType.QR_SCAN -> 1
            MissionType.SHAKE -> 20
        }

        fun decode(raw: String): MissionConfig? {
            val parts = raw.split(":")
            if (parts.size < 2) return null
            val type = MissionType.entries.firstOrNull { it.name == parts[0] } ?: return null
            val target = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, MAX_TARGET)
                ?: defaultTarget(type)
            val difficulty = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 3) ?: 1
            val sensitivity = parts.getOrNull(3)
                ?.let { s -> Sensitivity.entries.firstOrNull { it.name == s } }
                ?: Sensitivity.NORMAL
            return MissionConfig(type, target, difficulty, sensitivity)
        }

        fun encodeStack(stack: List<MissionConfig>): String =
            stack.joinToString("|") { it.encode() }

        fun decodeStack(raw: String): List<MissionConfig> =
            raw.split("|").mapNotNull { if (it.isBlank()) null else decode(it) }
    }
}
