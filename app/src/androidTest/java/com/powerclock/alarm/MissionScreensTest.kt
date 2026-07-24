package com.powerclock.alarm

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.powerclock.alarm.domain.model.MissionConfig
import com.powerclock.alarm.domain.model.MissionType
import com.powerclock.alarm.ui.ringing.MathMissionScreen
import com.powerclock.alarm.ui.ringing.TypingMissionScreen
import com.powerclock.alarm.ui.theme.PowerClockTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class MissionScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun mathMission_completesAfterCorrectAnswers() {
        val completed = AtomicBoolean(false)
        val config = MissionConfig(MissionType.MATH, target = 2, difficulty = 1)
        compose.setContent {
            PowerClockTheme(darkTheme = true) {
                MathMissionScreen(config) { completed.set(true) }
            }
        }

        repeat(2) {
            compose.waitForIdle()
            val problemNode = compose.onAllNodes(hasText("= ?", substring = true))
                .fetchSemanticsNodes()
                .first()
            val text = problemNode.config
                .getOrNull(SemanticsProperties.Text)!!
                .joinToString("") { annotated -> annotated.text }
            val answer = solve(text)
            compose.onNodeWithText("Check answer").assertIsDisplayed()
            // Type into the single text field.
            compose.onAllNodes(hasSetTextAction())[0]
                .performTextInput(answer.toString())
            compose.onNodeWithText("Check answer").performClick()
        }
        compose.waitForIdle()
        assertTrue(completed.get())
    }

    @Test
    fun mathMission_wrongAnswerShowsFreshProblem() {
        val config = MissionConfig(MissionType.MATH, target = 1, difficulty = 1)
        compose.setContent {
            PowerClockTheme(darkTheme = true) {
                MathMissionScreen(config) { }
            }
        }
        compose.onAllNodes(hasSetTextAction())[0]
            .performTextInput("999999")
        compose.onNodeWithText("Check answer").performClick()
        compose.onNodeWithText("Not quite — here's a fresh one.").assertIsDisplayed()
    }

    @Test
    fun typingMission_completesOnExactPhrase() {
        val completed = AtomicBoolean(false)
        val config = MissionConfig(MissionType.TYPING, target = 1)
        compose.setContent {
            PowerClockTheme(darkTheme = true) {
                TypingMissionScreen(config) { completed.set(true) }
            }
        }
        compose.waitForIdle()
        val phraseNode = compose.onAllNodes(hasText("\u201C", substring = true))
            .fetchSemanticsNodes()
            .first()
        val phrase = phraseNode.config
            .getOrNull(SemanticsProperties.Text)!!
            .joinToString("") { annotated -> annotated.text }
            .removePrefix("\u201C")
            .removeSuffix("\u201D")
        compose.onAllNodes(hasSetTextAction())[0]
            .performTextInput(phrase)
        compose.onNodeWithText("Submit").performClick()
        compose.waitForIdle()
        assertTrue(completed.get())
    }

    private fun solve(text: String): Int {
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
