package com.kittenml.tts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kittenml.tts.ui.screen.TTSScreen
import com.kittenml.tts.ui.screen.practice.PracticeListScreen
import com.kittenml.tts.ui.screen.practice.PracticeScreen
import com.kittenml.tts.ui.screen.practice.PracticeViewModel
import com.kittenml.tts.ui.screen.speaking.SpeakingListScreen
import com.kittenml.tts.ui.screen.speaking.SpeakingScreen
import com.kittenml.tts.ui.screen.speaking.SpeakingViewModel
import com.kittenml.tts.ui.theme.AppBackground
import com.kittenml.tts.ui.theme.CardBg
import com.kittenml.tts.ui.theme.Neutral
import com.kittenml.tts.ui.theme.PrimaryAccent
import com.kittenml.tts.ui.theme.KittenTTSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KittenTTSTheme {
                AppRoot()
            }
        }
    }
}

private object Routes {
    const val PRACTICE_LIST = "practice_list"
    const val PRACTICE_DETAIL = "practice_detail"
    const val SPEAKING_LIST = "speaking_list"
    const val SPEAKING_DETAIL = "speaking_detail"
    const val TTS = "tts"
}

@Composable
private fun AppRoot() {
    val navController = rememberNavController()
    // Each feature's two screens share one ViewModel.
    val practiceVm: PracticeViewModel = viewModel()
    val speakingVm: SpeakingViewModel = viewModel()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.hierarchy?.firstOrNull()?.route

    val practiceSelected = currentRoute == Routes.PRACTICE_LIST || currentRoute == Routes.PRACTICE_DETAIL
    val speakingSelected = currentRoute == Routes.SPEAKING_LIST || currentRoute == Routes.SPEAKING_DETAIL

    Scaffold(
        containerColor = AppBackground,
        bottomBar = {
            NavigationBar(containerColor = CardBg) {
                NavigationBarItem(
                    selected = practiceSelected,
                    onClick = {
                        navController.navigate(Routes.PRACTICE_LIST) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Text("📖") },
                    label = { Text("Reading") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = speakingSelected,
                    onClick = {
                        navController.navigate(Routes.SPEAKING_LIST) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Text("🎤") },
                    label = { Text("Speaking") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.TTS,
                    onClick = {
                        navController.navigate(Routes.TTS) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Text("🔊") },
                    label = { Text("Voice Studio") },
                    colors = navColors()
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.PRACTICE_LIST,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.PRACTICE_LIST) {
                val paragraphs by practiceVm.paragraphs.collectAsStateWithLifecycle()
                PracticeListScreen(
                    paragraphs = paragraphs,
                    onSelect = { id ->
                        practiceVm.selectParagraph(id)
                        navController.navigate(Routes.PRACTICE_DETAIL)
                    }
                )
            }
            composable(Routes.PRACTICE_DETAIL) {
                PracticeScreen(
                    viewModel = practiceVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SPEAKING_LIST) {
                val prompts by speakingVm.prompts.collectAsStateWithLifecycle()
                SpeakingListScreen(
                    prompts = prompts,
                    onSelect = { id ->
                        speakingVm.selectPrompt(id)
                        navController.navigate(Routes.SPEAKING_DETAIL)
                    }
                )
            }
            composable(Routes.SPEAKING_DETAIL) {
                SpeakingScreen(
                    viewModel = speakingVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.TTS) {
                TTSScreen()
            }
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = PrimaryAccent,
    selectedTextColor = PrimaryAccent,
    indicatorColor = AppBackground,
    unselectedIconColor = Neutral,
    unselectedTextColor = Neutral
)
