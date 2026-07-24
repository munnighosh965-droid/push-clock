package com.powerclock.alarm.domain.missions

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class BrainMissionsTest {

    @Test
    fun `math answers are correct across difficulties`() {
        val random = Random(42)
        repeat(200) {
            for (difficulty in 1..3) {
                val p = MathProblemGenerator.generate(difficulty, random)
                val evaluated = evaluate(p.text)
                assertThat(evaluated).isEqualTo(p.answer)
            }
        }
    }

    @Test
    fun `math problems are randomized`() {
        val random = Random(7)
        val problems = (1..20).map { MathProblemGenerator.generate(2, random).text }.toSet()
        assertThat(problems.size).isGreaterThan(5)
    }

    @Test
    fun `memory sequence length grows with difficulty and round`() {
        val r = Random(1)
        assertThat(MemorySequenceGenerator.generate(1, 0, r).size).isEqualTo(3)
        assertThat(MemorySequenceGenerator.generate(3, 0, r).size).isEqualTo(5)
        assertThat(MemorySequenceGenerator.generate(3, 2, r).size).isEqualTo(7)
        // Hard ceiling.
        assertThat(MemorySequenceGenerator.generate(3, 20, r).size).isEqualTo(9)
    }

    @Test
    fun `memory pads stay in range`() {
        val r = Random(2)
        repeat(50) {
            MemorySequenceGenerator.generate(3, 3, r).forEach { pad ->
                assertThat(pad).isAtLeast(0)
                assertThat(pad).isAtMost(8)
            }
        }
    }

    @Test
    fun `phrase matching normalizes whitespace and case`() {
        assertThat(PhraseBank.matches("Wake Move Win", "  wake   move win ")).isTrue()
        assertThat(PhraseBank.matches("wake move win", "wake move wins")).isFalse()
    }

    private fun evaluate(text: String): Int {
        // Formats: "a + b = ?", "a − b = ?", "a × b + c = ?"
        val expr = text.removeSuffix(" = ?")
        return when {
            "×" in expr -> {
                val (mul, add) = expr.split(" + ")
                val (a, b) = mul.split(" × ").map { it.trim().toInt() }
                a * b + add.trim().toInt()
            }
            "−" in expr -> {
                val (a, b) = expr.split(" − ").map { it.trim().toInt() }
                a - b
            }
            else -> {
                val (a, b) = expr.split(" + ").map { it.trim().toInt() }
                a + b
            }
        }
    }
}
