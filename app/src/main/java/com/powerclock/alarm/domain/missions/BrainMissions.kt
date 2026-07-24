package com.powerclock.alarm.domain.missions

import kotlin.random.Random

/** A single math question with its accepted answer. */
data class MathProblem(val text: String, val answer: Int)

object MathProblemGenerator {

    /**
     * @param difficulty 1 = single-digit sums, 2 = two-digit mixed,
     *   3 = multiplication plus addition chains.
     */
    fun generate(difficulty: Int, random: Random = Random.Default): MathProblem =
        when (difficulty.coerceIn(1, 3)) {
            1 -> {
                val a = random.nextInt(2, 10)
                val b = random.nextInt(2, 10)
                MathProblem("$a + $b = ?", a + b)
            }
            2 -> {
                val a = random.nextInt(11, 60)
                val b = random.nextInt(11, 60)
                if (random.nextBoolean()) {
                    MathProblem("$a + $b = ?", a + b)
                } else {
                    val big = maxOf(a, b)
                    val small = minOf(a, b)
                    MathProblem("$big − $small = ?", big - small)
                }
            }
            else -> {
                val a = random.nextInt(3, 13)
                val b = random.nextInt(3, 13)
                val c = random.nextInt(5, 40)
                MathProblem("$a × $b + $c = ?", a * b + c)
            }
        }
}

object MemorySequenceGenerator {
    /**
     * Sequence of pad indices (0..8) to memorize. Length grows with
     * difficulty and round number.
     */
    fun generate(difficulty: Int, round: Int, random: Random = Random.Default): List<Int> {
        val length = (2 + difficulty.coerceIn(1, 3) + round).coerceAtMost(9)
        return List(length) { random.nextInt(0, 9) }
    }
}

object PhraseBank {
    // Original Power Clock phrases; intentionally short and typo-resistant.
    private val phrases = listOf(
        "morning power is earned one rep at a time",
        "the alarm rang and I answered",
        "small steps still count today",
        "wake move win repeat",
        "my morning belongs to me",
        "strong starts make calm days",
        "I am up before my excuses",
        "today runs on my schedule",
        "one clear mind coming right up",
        "the snooze button lost this round",
        "energy first coffee second",
        "future me says thank you",
    )

    fun randomPhrase(random: Random = Random.Default): String =
        phrases[random.nextInt(phrases.size)]

    /** Comparison is whitespace-normalized and case-insensitive. */
    fun matches(expected: String, typed: String): Boolean =
        normalize(expected) == normalize(typed)

    private fun normalize(s: String) = s.trim().replace(Regex("\\s+"), " ").lowercase()
}
