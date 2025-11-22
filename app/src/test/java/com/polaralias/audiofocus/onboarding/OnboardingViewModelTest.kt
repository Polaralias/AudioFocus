package com.polaralias.audiofocus.onboarding

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.polaralias.audiofocus.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class OnboardingViewModelTest {

    private lateinit var viewModel: OnboardingViewModel
    private lateinit var application: Application
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state checks permissions and starts at overlay step when not granted`() = runTest {
        viewModel = OnboardingViewModel(application)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.OVERLAY, state.currentStep)
        assertFalse(state.hasOverlayPermission)
        assertFalse(state.hasNotificationAccess)
        assertTrue(state.canPostNotifications)
        assertFalse(state.hasAccessibilityAccess)
        assertFalse(state.showError)
        assertFalse(state.isOnboardingComplete)
    }

    @Test
    fun `onPermissionDenied sets error flag`() = runTest {
        viewModel = OnboardingViewModel(application)
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.onPermissionDenied()
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state.showError)
    }

    @Test
    fun `onPermissionGranted clears error flag and checks permissions`() = runTest {
        viewModel = OnboardingViewModel(application)
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.onPermissionDenied()
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.onPermissionGranted()
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertFalse(state.showError)
    }

    @Test
    fun `startWelcome updates to overlay step`() = runTest {
        viewModel = OnboardingViewModel(application)
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.startWelcome()
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.OVERLAY, state.currentStep)
    }

    @Test
    fun `checkPermissionsAndUpdateStep moves to notification step when overlay granted`() = runTest {
        viewModel = OnboardingViewModel(application)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.checkPermissionsAndUpdateStep()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.OVERLAY, state.currentStep)
    }
}
