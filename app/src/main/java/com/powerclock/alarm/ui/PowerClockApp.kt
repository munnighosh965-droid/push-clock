package com.powerclock.alarm.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.powerclock.alarm.data.prefs.UserSettings
import com.powerclock.alarm.ui.about.AboutScreen
import com.powerclock.alarm.ui.alarms.AlarmListScreen
import com.powerclock.alarm.ui.earlyrise.EarlyRiseScreen
import com.powerclock.alarm.ui.editor.AlarmEditorScreen
import com.powerclock.alarm.ui.home.HomeScreen
import com.powerclock.alarm.ui.onboarding.OnboardingScreen
import com.powerclock.alarm.ui.privacy.PrivacyScreen
import com.powerclock.alarm.ui.progress.ProgressScreen
import com.powerclock.alarm.ui.qrcard.QrCardScreen
import com.powerclock.alarm.ui.reliability.ReliabilityScreen
import com.powerclock.alarm.ui.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val EDITOR = "editor?alarmId={alarmId}"
    const val RELIABILITY = "reliability"
    const val EARLY_RISE = "early_rise"
    const val PRIVACY = "privacy"
    const val ABOUT = "about"
    const val QR_CARD = "qr_card"

    fun editor(alarmId: Long = 0L) = "editor?alarmId=$alarmId"
}

@Composable
fun PowerClockApp(settings: UserSettings) {
    val navController = rememberNavController()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavHost(
            navController = navController,
            startDestination = if (settings.onboardingComplete) Routes.MAIN else Routes.ONBOARDING,
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onDone = {
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.MAIN) { MainTabs(navController, settings) }
            composable(
                Routes.EDITOR,
                arguments = listOf(navArgument("alarmId") { type = NavType.LongType; defaultValue = 0L }),
            ) {
                AlarmEditorScreen(onClose = { navController.popBackStack() })
            }
            composable(Routes.RELIABILITY) {
                ReliabilityScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.EARLY_RISE) {
                EarlyRiseScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PRIVACY) {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.QR_CARD) {
                QrCardScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun MainTabs(navController: NavHostController, settings: UserSettings) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        Tab("Today", Icons.Filled.WbSunny),
        Tab("Alarms", Icons.Filled.Alarm),
        Tab("Progress", Icons.Filled.Insights),
        Tab("Settings", Icons.Filled.Settings),
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (selected) {
            0 -> HomeScreen(
                modifier = contentModifier,
                onCreateAlarm = { navController.navigate(Routes.editor()) },
                onOpenAlarms = { selected = 1 },
            )
            1 -> AlarmListScreen(
                modifier = contentModifier,
                onCreateAlarm = { navController.navigate(Routes.editor()) },
                onEditAlarm = { id -> navController.navigate(Routes.editor(id)) },
            )
            2 -> ProgressScreen(
                modifier = contentModifier,
                onOpenEarlyRise = { navController.navigate(Routes.EARLY_RISE) },
            )
            else -> SettingsScreen(
                modifier = contentModifier,
                onOpenReliability = { navController.navigate(Routes.RELIABILITY) },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onOpenEarlyRise = { navController.navigate(Routes.EARLY_RISE) },
                onOpenQrCard = { navController.navigate(Routes.QR_CARD) },
            )
        }
    }
}
