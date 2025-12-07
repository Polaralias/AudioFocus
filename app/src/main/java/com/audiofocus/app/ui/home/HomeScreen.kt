package com.audiofocus.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiofocus.app.core.model.AppSettings
import com.audiofocus.app.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settingsRepository.appSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    fun toggleMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMonitoringEnabled(enabled)
        }
    }

    fun updateYoutubeThemeBlur(blurLevel: Int) {
        viewModelScope.launch {
            val current = appSettings.value.youtubeTheme
            settingsRepository.setYoutubeTheme(current.copy(blurLevel = blurLevel))
        }
    }

    fun updateYoutubeThemeColor(color: Int) {
        viewModelScope.launch {
            val current = appSettings.value.youtubeTheme
            settingsRepository.setYoutubeTheme(current.copy(color = color))
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val settings by viewModel.appSettings.collectAsState()

    Scaffold(
        topBar = {
            Text(
                text = "Audio Focus",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Master Toggle
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Service Enabled",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (settings.isMonitoringEnabled) "Monitoring active" else "Monitoring paused",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = settings.isMonitoringEnabled,
                        onCheckedChange = { viewModel.toggleMonitoring(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Customization",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Blur Slider
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Blur Level",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Slider(
                        value = settings.youtubeTheme.blurLevel.toFloat(),
                        onValueChange = { viewModel.updateYoutubeThemeBlur(it.toInt()) },
                        valueRange = 0f..3f,
                        steps = 2 // 0, 1, 2, 3
                    )
                    Text(
                        text = "Level: ${settings.youtubeTheme.blurLevel}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Background Color Preview (Simple implementation)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Background Color",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                         // Simple color palette
                         val colors = listOf(
                             0xFF000000.toInt(), // Black
                             0xFF1E1E1E.toInt(), // Dark Gray
                             0xFF6200EE.toInt(), // Purple
                             0xFF03DAC5.toInt()  // Teal
                         )

                         colors.forEach { color ->
                             Box(
                                 modifier = Modifier
                                     .size(48.dp)
                                     .padding(4.dp)
                                     .clip(CircleShape)
                                     .background(Color(color))
                                     .clickable { viewModel.updateYoutubeThemeColor(color) }
                             )
                         }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Preview Mock
            Text(
                text = "Preview",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.Gray) // Placeholder for background image
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(settings.youtubeTheme.color).copy(alpha = 0.8f)) // Simple approximation
                ) {
                     Text(
                         text = "Overlay Preview Area",
                         color = Color.White,
                         modifier = Modifier.align(Alignment.Center)
                     )
                }
            }
        }
    }
}
