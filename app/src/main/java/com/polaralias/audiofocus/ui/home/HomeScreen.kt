package com.polaralias.audiofocus.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.polaralias.audiofocus.core.model.AppSettings
import com.polaralias.audiofocus.core.model.TargetApp
import com.polaralias.audiofocus.core.model.ThemeConfig
import com.polaralias.audiofocus.core.model.ThemeType
import com.polaralias.audiofocus.domain.settings.SettingsRepository
import com.polaralias.audiofocus.service.AudioFocusService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settingsRepository.appSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    private val _selectedApp = MutableStateFlow(TargetApp.YOUTUBE)
    val selectedApp: StateFlow<TargetApp> = _selectedApp.asStateFlow()

    fun selectApp(app: TargetApp) {
        _selectedApp.value = app
    }

    fun toggleMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMonitoringEnabled(enabled)

            if (enabled) {
                val intent = Intent(context, AudioFocusService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                val intent = Intent(context, AudioFocusService::class.java).apply {
                    action = AudioFocusService.ACTION_STOP_MONITORING
                }
                context.startService(intent)
            }
        }
    }

    fun updateTheme(config: ThemeConfig) {
        viewModelScope.launch {
            when (_selectedApp.value) {
                TargetApp.YOUTUBE -> settingsRepository.setYoutubeTheme(config)
                TargetApp.YOUTUBE_MUSIC -> settingsRepository.setYoutubeMusicTheme(config)
            }
        }
    }

    fun persistImagePermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val settings by viewModel.appSettings.collectAsState()
    val selectedApp by viewModel.selectedApp.collectAsState()
    val context = LocalContext.current

    // Determine current theme config based on selected app
    val currentTheme = when (selectedApp) {
        TargetApp.YOUTUBE -> settings.youtubeTheme
        TargetApp.YOUTUBE_MUSIC -> settings.youtubeMusicTheme
    }

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

            // App Selector
            TabRow(
                selectedTabIndex = if (selectedApp == TargetApp.YOUTUBE) 0 else 1,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedApp == TargetApp.YOUTUBE,
                    onClick = { viewModel.selectApp(TargetApp.YOUTUBE) },
                    text = { Text("YouTube") }
                )
                Tab(
                    selected = selectedApp == TargetApp.YOUTUBE_MUSIC,
                    onClick = { viewModel.selectApp(TargetApp.YOUTUBE_MUSIC) },
                    text = { Text("YouTube Music") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Theme Type Selector
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Theme Type",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = currentTheme.type == ThemeType.SOLID,
                            onClick = { viewModel.updateTheme(currentTheme.copy(type = ThemeType.SOLID)) }
                        )
                        Text("Solid Color")
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(
                            selected = currentTheme.type == ThemeType.IMAGE,
                            onClick = { viewModel.updateTheme(currentTheme.copy(type = ThemeType.IMAGE)) }
                        )
                        Text("Image")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Configuration based on type
            if (currentTheme.type == ThemeType.SOLID) {
                SolidColorConfig(
                    currentColor = currentTheme.color,
                    onColorSelected = { viewModel.updateTheme(currentTheme.copy(color = it)) }
                )
            } else {
                ImageConfig(
                    imageUri = currentTheme.imageUri,
                    onImageSelected = { uri ->
                        viewModel.persistImagePermission(context, uri)
                        viewModel.updateTheme(currentTheme.copy(imageUri = uri.toString()))
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Blur Slider (Always available, but mainly for Image)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Blur Level",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Slider(
                        value = currentTheme.blurLevel.toFloat(),
                        onValueChange = { viewModel.updateTheme(currentTheme.copy(blurLevel = it.toInt())) },
                        valueRange = 0f..3f,
                        steps = 2
                    )
                    Text(
                        text = "Level: ${currentTheme.blurLevel}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Preview
            Text(
                text = "Preview",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            PreviewCard(theme = currentTheme)
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SolidColorConfig(
    currentColor: Int,
    onColorSelected: (Int) -> Unit
) {
    var hexInput by remember(currentColor) {
        mutableStateOf(String.format("#%06X", (0xFFFFFF and currentColor)))
    }
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Color Selection", style = MaterialTheme.typography.titleMedium)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row {
                val colors = listOf(
                    0xFF000000.toInt(), // Black
                    0xFF1E1E1E.toInt(), // Dark Gray
                    0xFF6200EE.toInt(), // Purple
                    0xFF03DAC5.toInt(), // Teal
                    0xFFB00020.toInt()  // Red
                )

                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .clickable { onColorSelected(color) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = hexInput,
                onValueChange = { input ->
                    hexInput = input
                    if (input.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$"))) {
                        try {
                            onColorSelected(android.graphics.Color.parseColor(input))
                        } catch (e: Exception) {
                            // Invalid color
                        }
                    }
                },
                label = { Text("Hex Color (#RRGGBB)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
        }
    }
}

@Composable
fun ImageConfig(
    imageUri: String?,
    onImageSelected: (Uri) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImageSelected(uri)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) {
                Text("Select Image from Gallery")
            }
            
            if (imageUri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Image Selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun PreviewCard(theme: ThemeConfig) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (theme.type == ThemeType.IMAGE && theme.imageUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(Uri.parse(theme.imageUri))
                        .crossfade(true)
                        .build(),
                    contentDescription = "Overlay Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Apply blur overlay if needed (simple scrim for preview)
                if (theme.blurLevel > 0) {
                     Box(
                         modifier = Modifier
                             .fillMaxSize()
                             .background(Color.Black.copy(alpha = theme.blurLevel * 0.2f))
                     )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(theme.color))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)) // Simulate controls dimming
            ) {
                Text(
                    text = "Overlay Controls Preview",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
