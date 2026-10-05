package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VoiceScreen
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.SanaViewModel

enum class SanaNavScreen {
    VOICE,
    CHAT,
    SETTINGS
}

@Composable
fun SanaApp(viewModel: SanaViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var currentScreen by remember { mutableStateOf(SanaNavScreen.VOICE) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    // Multi-permission launcher for RECORD_AUDIO and POST_NOTIFICATIONS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (recordAudioGranted) {
            viewModel.startContinuousVoice()
        } else {
            showPermissionRationale = true
        }
    }

    fun requestPermissionsAndStartVoice() {
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotif = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotif) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (hasAudio) {
            viewModel.startContinuousVoice()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    BackHandler(enabled = currentScreen != SanaNavScreen.VOICE) {
        currentScreen = SanaNavScreen.VOICE
    }

    Scaffold(
        containerColor = ObsidianBg,
        bottomBar = {
            NavigationBar(
                containerColor = ObsidianSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                NavigationBarItem(
                    selected = currentScreen == SanaNavScreen.VOICE,
                    onClick = { currentScreen = SanaNavScreen.VOICE },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("Voice", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00363D),
                        selectedTextColor = CyanPrimary,
                        indicatorColor = CyanPrimary,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_voice")
                )

                NavigationBarItem(
                    selected = currentScreen == SanaNavScreen.CHAT,
                    onClick = { currentScreen = SanaNavScreen.CHAT },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Chat",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("Chat", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00363D),
                        selectedTextColor = CyanPrimary,
                        indicatorColor = CyanPrimary,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_chat")
                )

                NavigationBarItem(
                    selected = currentScreen == SanaNavScreen.SETTINGS,
                    onClick = { currentScreen = SanaNavScreen.SETTINGS },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("Settings", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00363D),
                        selectedTextColor = CyanPrimary,
                        indicatorColor = CyanPrimary,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentScreen) {
                SanaNavScreen.VOICE -> {
                    VoiceScreen(
                        uiState = uiState,
                        onStartVoice = { requestPermissionsAndStartVoice() },
                        onStopVoice = { viewModel.stopContinuousVoice() },
                        onRetry = { viewModel.retryLastMessage() },
                        onModeSelected = { viewModel.setAssistantMode(it) },
                        onOpenSettings = { currentScreen = SanaNavScreen.SETTINGS }
                    )
                }
                SanaNavScreen.CHAT -> {
                    ChatScreen(
                        uiState = uiState,
                        onSendMessage = { viewModel.sendTextMessage(it) },
                        onReplayAudio = { viewModel.replayMessageAudio(it) },
                        onClearHistory = { viewModel.clearChatHistory() },
                        onMicToggled = {
                            if (uiState.isContinuousActive) {
                                viewModel.stopContinuousVoice()
                            } else {
                                requestPermissionsAndStartVoice()
                            }
                        }
                    )
                }
                SanaNavScreen.SETTINGS -> {
                    SettingsScreen(
                        preferences = uiState.preferences,
                        onModeChanged = { viewModel.setAssistantMode(it) },
                        onVoiceChanged = { viewModel.setGeminiVoice(it) },
                        onAddMemoryNote = { viewModel.addMemoryNote(it) },
                        onRemoveMemoryNote = { viewModel.removeMemoryNote(it) },
                        onClearMemory = { viewModel.clearMemory() },
                        onSaveApiKey = { manualKey ->
                            viewModel.updatePreferences(
                                userName = uiState.preferences.userName,
                                preferredLanguage = uiState.preferences.preferredLanguage,
                                customInstructions = uiState.preferences.customInstructions,
                                manualApiKey = manualKey
                            )
                        },
                        onBack = { currentScreen = SanaNavScreen.VOICE }
                    )
                }
            }
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = {
                Text(
                    text = "Microphone Access Required",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "SANA needs microphone access to listen to your voice and conduct real-time hands-free conversation with Gemini Native Audio.",
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color(0xFF00363D))
                ) {
                    Text("Grant Permission", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
