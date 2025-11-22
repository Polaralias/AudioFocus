package com.polaralias.audiofocus.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.polaralias.audiofocus.state.FocusStateRepository
import com.polaralias.audiofocus.state.SupportedApp
import com.polaralias.audiofocus.state.WindowSnapshot
import com.polaralias.audiofocus.state.WindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [AudioFocusAccessibilityServiceTest.TestShadowAccessibilityWindowInfo::class])
class AudioFocusAccessibilityServiceTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun pipWindowInWindowsEmitsSnapshot() {
        val controller = Robolectric.buildService(TestAudioFocusAccessibilityService::class.java)
        val service = controller.create().get()
        val repository = mock<FocusStateRepository>()

        service.overrideWindows = listOf(createPipWindow())
        setRepository(service, repository)

        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED).apply {
            try {
                val field = AccessibilityEvent::class.java.getDeclaredField("mWindowChanges")
                field.isAccessible = true
                field.setInt(this, AccessibilityEvent.WINDOWS_CHANGE_PIP)
            } catch (e: Exception) {
            }
        }

        service.onAccessibilityEvent(event)
        dispatcher.scheduler.advanceUntilIdle()

        verify(repository).updateWindowState(
            WindowSnapshot(SupportedApp.YOUTUBE, WindowState.PICTURE_IN_PICTURE)
        )

        event.recycle()
        controller.destroy()
    }

    private fun setRepository(
        service: AudioFocusAccessibilityService,
        repository: FocusStateRepository,
    ) {
        val field = AudioFocusAccessibilityService::class.java.getDeclaredField("repository")
        field.isAccessible = true
        field.set(service, repository)
    }

    private fun createPipWindow(): AccessibilityWindowInfo {
        val window = AccessibilityWindowInfo.obtain()
        val root = AccessibilityNodeInfo.obtain()
        root.packageName = "com.google.android.youtube"
        root.setBoundsInScreen(Rect(0, 0, 300, 300))
        TestShadowAccessibilityWindowInfo.setRoot(window, root)
        TestShadowAccessibilityWindowInfo.setBounds(window, Rect(0, 0, 200, 200))
        TestShadowAccessibilityWindowInfo.setType(window, 4)
        return window
    }

    class TestAudioFocusAccessibilityService : AudioFocusAccessibilityService() {
        var overrideWindows: List<AccessibilityWindowInfo>? = null

        override fun serviceWindows(): List<AccessibilityWindowInfo> {
            return overrideWindows ?: super.serviceWindows()
        }
    }

    @Implements(value = AccessibilityWindowInfo::class, isInAndroidSdk = true)
    class TestShadowAccessibilityWindowInfo {
        private var bounds: Rect = Rect()
        private var type: Int = AccessibilityWindowInfo.TYPE_APPLICATION
        private var root: AccessibilityNodeInfo? = null

        companion object {
            fun setBounds(window: AccessibilityWindowInfo, rect: Rect) {
                Shadow.extract<TestShadowAccessibilityWindowInfo>(window).bounds = Rect(rect)
            }

            fun setType(window: AccessibilityWindowInfo, type: Int) {
                Shadow.extract<TestShadowAccessibilityWindowInfo>(window).type = type
            }

            fun setRoot(window: AccessibilityWindowInfo, node: AccessibilityNodeInfo?) {
                Shadow.extract<TestShadowAccessibilityWindowInfo>(window).root = node
            }
        }

        @Implementation
        protected fun getBoundsInScreen(outBounds: Rect) {
            outBounds.set(bounds)
        }

        @Implementation
        protected fun getType(): Int = type

        @Implementation
        protected fun getRoot(): AccessibilityNodeInfo? = root
    }
}
