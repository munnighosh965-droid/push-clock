package com.powerclock.alarm.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powerclock.alarm.alarmengine.AlarmScheduler
import com.powerclock.alarm.data.prefs.SettingsRepository
import com.powerclock.alarm.data.prefs.UserSettings
import com.powerclock.alarm.data.repo.AlarmRepository
import com.powerclock.alarm.data.repo.HistoryRepository
import com.powerclock.alarm.domain.model.Alarm
import com.powerclock.alarm.domain.scheduling.NextOccurrenceCalculator
import com.powerclock.alarm.domain.stats.WakeStats
import com.powerclock.alarm.ui.components.PowerCard
import com.powerclock.alarm.ui.components.ProgressRing
import com.powerclock.alarm.ui.components.TimeFormat
import com.powerclock.alarm.ui.components.WeekDots
import com.powerclock.alarm.ui.components.Wordmark
import com.powerclock.alarm.ui.components.rememberReducedMotion
import com.powerclock.alarm.ui.theme.Glacier
import com.powerclock.alarm.ui.theme.Horizon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

data class HomeUiState(
    val settings: UserSettings = UserSettings(),
    val alarms: List<Alarm> = emptyList(),
    val nextAlarm: Alarm? = null,
    val nextTrigger: ZonedDateTime? = null,
    val stats: WakeStats = WakeStats.EMPTY,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    alarmRepository: AlarmRepository,
    historyRepository: HistoryRepository,
    private val scheduler: AlarmScheduler,
    private val alarmRepo: AlarmRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        settingsRepository.settings,
        alarmRepository.observeAll(),
        historyRepository.observeAll(),
    ) { settings, alarms, events ->
        val now = ZonedDateTime.now()
        val next = alarms
            .filter { it.enabled }
            .mapNotNull { alarm -> NextOccurrenceCalculator.nextTrigger(alarm, now)?.let { alarm to it } }
            .minByOrNull { it.second }
        HomeUiState(
            settings = settings,
            alarms = alarms,
            nextAlarm = next?.first,
            nextTrigger = next?.second,
            stats = WakeStats.compute(events, ZoneId.systemDefault(), LocalDate.now()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun toggleAlarm(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            val updated = alarmRepo.setEnabled(alarm.id, enabled)
            if (updated != null) {
                if (enabled) scheduler.schedule(updated) else scheduler.cancel(updated.id)
            }
        }
    }
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onCreateAlarm: () -> Unit,
    onOpenAlarms: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1000)
        }
    }

    val reduceMotion = rememberReducedMotion(state.settings.reduceMotion)
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        val greetingName = state.settings.name.ifBlank { "there" }
        val greeting = when (now.hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            in 18..22 -> "Good evening"
            else -> "Rest well"
        }

        Reveal(visible = entered, delayMillis = 0, reduceMotion = reduceMotion) {
            HeroPanel(
                greeting = "$greeting, $greetingName",
                now = now,
                nextAlarm = state.nextAlarm,
                nextTrigger = state.nextTrigger,
                reduceMotion = reduceMotion,
                onToggleNext = { alarm, enabled -> viewModel.toggleAlarm(alarm, enabled) },
                onCreateAlarm = onCreateAlarm,
            )
        }
        Spacer(Modifier.height(16.dp))

        Reveal(visible = entered, delayMillis = 90, reduceMotion = reduceMotion) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedStatCard(
                    value = state.stats.currentStreak,
                    label = if (state.stats.currentStreak == 1) "day streak" else "days streak",
                    accent = MaterialTheme.colorScheme.primary,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.weight(1f),
                )
                AnimatedStatCard(
                    value = state.stats.powerScore,
                    label = "Power Score",
                    accent = MaterialTheme.colorScheme.secondary,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Reveal(visible = entered, delayMillis = 160, reduceMotion = reduceMotion) {
            PowerCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    WeekDots(state.stats.last7Days)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (state.settings.bedtimeReminderEnabled) {
            PowerCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Bedtime reminder", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tonight at ${TimeFormat.minutesAsClock(state.settings.bedtimeMinutes)} — winding down early makes the mission easier.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (state.alarms.isNotEmpty()) {
            androidx.compose.material3.TextButton(onClick = onOpenAlarms) {
                Text("See all ${state.alarms.size} alarms")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Staggered entrance wrapper; collapses to a no-op when motion is reduced. */
@Composable
private fun Reveal(
    visible: Boolean,
    delayMillis: Int,
    reduceMotion: Boolean,
    content: @Composable () -> Unit,
) {
    if (reduceMotion) {
        content()
        return
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(420, delayMillis = delayMillis)) +
            slideInVertically(
                animationSpec = tween(480, delayMillis = delayMillis, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 5 },
            ),
    ) { content() }
}

/**
 * The home hero: live clock, breathing aurora glow, an arc that fills as the
 * next alarm approaches, and the primary call to action.
 */
@Composable
private fun HeroPanel(
    greeting: String,
    now: ZonedDateTime,
    nextAlarm: Alarm?,
    nextTrigger: ZonedDateTime?,
    reduceMotion: Boolean,
    onToggleNext: (Alarm, Boolean) -> Unit,
    onCreateAlarm: () -> Unit,
) {
    val glow = if (reduceMotion) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "heroGlow")
        val v by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(6000, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "heroGlowValue",
        )
        v
    }

    // How close the next alarm is, as an arc: full ring = 12 h away or more.
    val windowMinutes = 12f * 60f
    val minutesAway = nextTrigger?.let {
        Duration.between(now, it).toMinutes().coerceAtLeast(0L).toFloat()
    }
    val targetProgress = minutesAway?.let { 1f - (it / windowMinutes).coerceIn(0f, 1f) } ?: 0f
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(if (reduceMotion) 0 else 900, easing = FastOutSlowInEasing),
        label = "alarmProgress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.55f),
            ) {
                val radius = size.maxDimension * (0.55f + 0.12f * glow)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Glacier.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * (0.25f + 0.2f * glow), size.height * 0.15f),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(size.width * (0.25f + 0.2f * glow), size.height * 0.15f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Horizon.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width * (0.85f - 0.2f * glow), size.height * 0.8f),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(size.width * (0.85f - 0.2f * glow), size.height * 0.8f),
                )
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                Wordmark()
                Spacer(Modifier.height(18.dp))
                Text(
                    greeting,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                AnimatedContent(
                    targetState = TimeFormat.clock(now),
                    transitionSpec = {
                        if (reduceMotion) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            (
                                slideInVertically(tween(420)) { it / 3 } +
                                    fadeIn(tween(420))
                                ) togetherWith (
                                slideOutVertically(tween(420)) { -it / 3 } +
                                    fadeOut(tween(300))
                                )
                        }
                    },
                    label = "clock",
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { contentDescription = "Current time $text" },
                    )
                }
                Text(
                    TimeFormat.fullDate(now),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(
                        progress = progress,
                        ringSize = 96.dp,
                        stroke = 8.dp,
                    ) {
                        Text(
                            if (nextTrigger == null) "—" else TimeFormat.clock(nextTrigger),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        if (nextAlarm != null && nextTrigger != null) {
                            Text(
                                "Next alarm ${TimeFormat.countdown(now, nextTrigger)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                TimeFormat.nextAlarm(nextTrigger),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val missions = nextAlarm.missions
                            Text(
                                if (missions.isEmpty()) {
                                    "Workout added automatically"
                                } else {
                                    missions.joinToString(" → ") { missionShortName(it.type.name) }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (nextAlarm.label.isNotBlank()) {
                                Text(
                                    nextAlarm.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(
                                "No alarms scheduled",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Set your first Power Clock alarm and win tomorrow morning.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (nextAlarm != null) {
                        Switch(
                            checked = nextAlarm.enabled,
                            onCheckedChange = { onToggleNext(nextAlarm, it) },
                            modifier = Modifier.semantics {
                                contentDescription = "Quick toggle for next alarm"
                            },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))

                val ctaScale = if (reduceMotion) {
                    1f
                } else {
                    val transition = rememberInfiniteTransition(label = "cta")
                    val v by transition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.02f,
                        animationSpec = infiniteRepeatable(
                            tween(1600, easing = FastOutSlowInEasing),
                            RepeatMode.Reverse,
                        ),
                        label = "ctaScale",
                    )
                    v
                }
                Button(
                    onClick = onCreateAlarm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(ctaScale)
                        .sizeIn(minHeight = 56.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Set alarm", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Stat tile whose number counts up when the value changes. */
@Composable
private fun AnimatedStatCard(
    value: Int,
    label: String,
    accent: Color,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val shown by animateIntAsState(
        targetValue = value,
        animationSpec = tween(if (reduceMotion) 0 else 800, easing = FastOutSlowInEasing),
        label = "stat-$label",
    )
    PowerCard(modifier = modifier) {
        Column {
            Text(
                "$shown",
                style = MaterialTheme.typography.headlineLarge,
                color = accent,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun missionShortName(name: String): String = when (name) {
    "PUSH_UPS" -> "Push-ups"
    "KNEE_PUSH_UPS" -> "Knee push-ups"
    "SQUATS" -> "Squats"
    "JUMPING_JACKS" -> "Jumping jacks"
    "MATH" -> "Math"
    "MEMORY" -> "Memory"
    "TYPING" -> "Typing"
    "QR_SCAN" -> "QR scan"
    "SHAKE" -> "Shake"
    else -> name
}
