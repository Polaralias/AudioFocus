package com.audiofocus.app.ui.home

import com.audiofocus.app.core.model.AppSettings
import com.audiofocus.app.core.model.TargetApp
import com.audiofocus.app.core.model.ThemeConfig
import com.audiofocus.app.domain.settings.SettingsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: HomeViewModel
    private val appSettingsFlow = MutableStateFlow(AppSettings())
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk(relaxed = true)
        every { settingsRepository.appSettings } returns appSettingsFlow
        viewModel = HomeViewModel(settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggleMonitoring updates repository`() = runTest {
        viewModel.toggleMonitoring(false)
        coVerify { settingsRepository.setMonitoringEnabled(false) }
    }

    @Test
    fun `updateTheme for YouTube updates correct theme`() = runTest {
        viewModel.selectApp(TargetApp.YOUTUBE)
        val config = ThemeConfig(color = 123)
        viewModel.updateTheme(config)
        coVerify { settingsRepository.setYoutubeTheme(config) }
    }

    @Test
    fun `updateTheme for YouTube Music updates correct theme`() = runTest {
        viewModel.selectApp(TargetApp.YOUTUBE_MUSIC)
        val config = ThemeConfig(color = 456)
        viewModel.updateTheme(config)
        coVerify { settingsRepository.setYoutubeMusicTheme(config) }
    }
}
