package com.powerclock.alarm.ui.ringing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.powerclock.alarm.domain.missions.MathProblemGenerator
import com.powerclock.alarm.domain.missions.MemorySequenceGenerator
import com.powerclock.alarm.domain.missions.PhraseBank
import com.powerclock.alarm.domain.model.MissionConfig
import com.powerclock.alarm.ui.theme.Champagne
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun MathMissionScreen(config: MissionConfig, onComplete: () -> Unit) {
    var solved by remember { mutableIntStateOf(0) }
    var seed by remember { mutableIntStateOf(0) }
    val problem = remember(solved, seed) { MathProblemGenerator.generate(config.difficulty) }
    var input by remember(problem) { mutableStateOf("") }
    var wrongFlash by remember { mutableStateOf(false) }

    LaunchedEffect(wrongFlash) {
        if (wrongFlash) {
            delay(700)
            wrongFlash = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Math mission ${solved + 1} of ${config.target}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(problem.text, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { v -> input = v.filter { it.isDigit() || it == '-' }.take(7) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        if (wrongFlash) {
            Text(
                "Not quite — here's a fresh one.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (input.toIntOrNull() == problem.answer) {
                    solved++
                    if (solved >= config.target) onComplete()
                } else {
                    // A new randomized problem prevents brute-force tapping.
                    seed++
                    wrongFlash = true
                    input = ""
                }
            },
            enabled = input.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 56.dp),
        ) { Text("Check answer") }
    }
}

@Composable
fun MemoryMissionScreen(config: MissionConfig, onComplete: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    val sequence = remember(round, attempt) {
        MemorySequenceGenerator.generate(config.difficulty, round, Random(System.nanoTime()))
    }
    var showingIndex by remember(sequence) { mutableIntStateOf(-1) }
    var showingDone by remember(sequence) { mutableStateOf(false) }
    var progress by remember(sequence) { mutableIntStateOf(0) }
    var failed by remember(sequence) { mutableStateOf(false) }

    LaunchedEffect(sequence) {
        delay(600)
        for (i in sequence.indices) {
            showingIndex = i
            delay(650)
            showingIndex = -1
            delay(200)
        }
        showingDone = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Memory round ${round + 1} of ${config.target}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                !showingDone -> "Watch the sequence…"
                failed -> "Missed it — watch the new sequence."
                else -> "Your turn: repeat it ($progress/${sequence.size})"
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(24.dp))
        for (row in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0..2) {
                    val idx = row * 3 + col
                    val lit = showingIndex >= 0 && sequence[showingIndex] == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (lit) Champagne else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable(enabled = showingDone && !failed) {
                                if (sequence[progress] == idx) {
                                    progress++
                                    if (progress >= sequence.size) {
                                        val nextRound = round + 1
                                        if (nextRound >= config.target) {
                                            onComplete()
                                        } else {
                                            round = nextRound
                                        }
                                    }
                                } else {
                                    failed = true
                                    attempt++
                                }
                            },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun TypingMissionScreen(config: MissionConfig, onComplete: () -> Unit) {
    var done by remember { mutableIntStateOf(0) }
    val phrase = remember(done) { PhraseBank.randomPhrase(Random(System.nanoTime())) }
    var input by remember(phrase) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Type this phrase (${done + 1} of ${config.target})",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "\u201C$phrase\u201D",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(120) },
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (PhraseBank.matches(phrase, input)) {
                    done++
                    if (done >= config.target) onComplete()
                }
                input = ""
            },
            enabled = input.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 56.dp),
        ) { Text("Submit") }
    }
}
