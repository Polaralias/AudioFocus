package com.polaralias.audiofocus.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.polaralias.audiofocus.AudioFocusApp
import com.polaralias.audiofocus.R
import com.polaralias.audiofocus.notifications.AudioFocusNotificationListener
import com.polaralias.audiofocus.state.FocusStateRepository
import com.polaralias.audiofocus.state.PlaybackActivity
import com.polaralias.audiofocus.state.PlaybackContentType
import com.polaralias.audiofocus.state.PlaybackSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class OverlayService : Service() {
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val windowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private lateinit var repository: FocusStateRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var overlayView: View? = null
    private var currentMode: OverlayMode? = null

    private var pendingAnimator: ViewPropertyAnimator? = null
    private var controlsBinding: PlaybackControlsBinding? = null

    override fun onCreate() {
        super.onCreate()
        repository = (application as AudioFocusApp).focusStateRepository
        createNotificationChannel()
        startInForeground(repository.manualPauseFlow.value)
        observeRepository()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE_MANUAL_PAUSE) {
            repository.toggleManualPause()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        removeOverlay(immediate = true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeRepository() {
        serviceScope.launch {
            repository.overlayCommands.collect { command ->
                applyOverlayCommand(command)
            }
        }
        serviceScope.launch {
            repository.manualPauseFlow.collect { paused ->
                val notification = buildNotification(paused)
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        }
        serviceScope.launch {
            repository.playbackSnapshots.collect { snapshot ->
                controlsBinding?.update(snapshot)
            }
        }
    }

    private fun applyOverlayCommand(command: OverlayCommand) {
        when (command) {
            OverlayCommand.Hide -> hideOverlay()
            is OverlayCommand.Show -> showOverlay(command.mode)
        }
    }

    private fun showOverlay(mode: OverlayMode) {
        val existingView = overlayView
        if (existingView != null && currentMode == mode) {
            fadeIn(existingView)
            return
        }

        removeOverlay(immediate = true)

        val layoutRes = when (mode) {
            OverlayMode.FULL -> R.layout.overlay_full
            OverlayMode.PARTIAL -> R.layout.overlay_partial
        }
        val view = LayoutInflater.from(this).inflate(layoutRes, null)
        bindPlaybackControls(view, mode)
        val params = when (mode) {
            OverlayMode.FULL -> createFullOverlayLayoutParams()
            OverlayMode.PARTIAL -> createPartialOverlayLayoutParams()
        }

        currentMode = mode
        overlayView = view
        view.alpha = 0f
        windowManager.addView(view, params)
        fadeIn(view)
    }

    private fun hideOverlay() {
        val view = overlayView ?: return
        fadeOut(view) {
            removeOverlay(immediate = true)
        }
    }

    private fun removeOverlay(immediate: Boolean) {
        val view = overlayView ?: return
        pendingAnimator?.cancel()
        pendingAnimator = null
        overlayView = null
        currentMode = null
        controlsBinding = null
        runCatching {
            if (immediate) {
                windowManager.removeViewImmediate(view)
            } else {
                windowManager.removeView(view)
            }
        }
    }

    private fun createFullOverlayLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
    }

    private fun createPartialOverlayLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
    }

    private fun fadeIn(view: View) {
        pendingAnimator?.cancel()
        pendingAnimator = view.animate()
            .alpha(1f)
            .setDuration(FADE_DURATION_MS)
        pendingAnimator?.withEndAction {
            pendingAnimator = null
        }
        pendingAnimator?.start()
    }

    private fun fadeOut(view: View, onEnd: () -> Unit) {
        pendingAnimator?.cancel()
        pendingAnimator = view.animate()
            .alpha(0f)
            .setDuration(FADE_DURATION_MS)
        pendingAnimator?.withEndAction {
            pendingAnimator = null
            onEnd()
        }
        pendingAnimator?.start()
    }

    private fun bindPlaybackControls(view: View, mode: OverlayMode) {
        val playPause = view.findViewById<View>(R.id.overlay_play_pause)
        val rewind = view.findViewById<View>(R.id.overlay_seek_backward)
        val forward = view.findViewById<View>(R.id.overlay_seek_forward)

        if (mode == OverlayMode.PARTIAL) {
            view.isClickable = false
            view.isFocusable = false
            view.setOnTouchListener { _, _ -> false }
        }

        if (playPause == null) {
            controlsBinding = null
            return
        }

        val binding = PlaybackControlsBinding(playPause, rewind, forward)
        controlsBinding = binding
        binding.update(repository.playbackSnapshots.value)
    }

    private inner class PlaybackControlsBinding(
        private val playPause: View,
        private val rewind: View?,
        private val forward: View?,
    ) {
        init {
            playPause.setOnClickListener { AudioFocusNotificationListener.togglePlayPause() }
            rewind?.setOnClickListener { AudioFocusNotificationListener.seekBy(-SEEK_INTERVAL_MS) }
            forward?.setOnClickListener { AudioFocusNotificationListener.seekBy(SEEK_INTERVAL_MS) }
        }

        fun update(snapshot: PlaybackSnapshot?) {
            val isVideo = snapshot?.contentType == PlaybackContentType.VIDEO
            val hasController = AudioFocusNotificationListener.hasActiveController()
            val enabled = isVideo && hasController

            listOf(playPause, rewind, forward).forEach { view ->
                view?.isEnabled = enabled
            }

            if (playPause is android.widget.TextView) {
                val textRes = if (snapshot?.activity == PlaybackActivity.PLAYING) {
                    R.string.overlay_control_pause
                } else {
                    R.string.overlay_control_play
                }
                playPause.setText(textRes)
            }
            playPause.contentDescription = this@OverlayService.getString(
                if (snapshot?.activity == PlaybackActivity.PLAYING) {
                    R.string.overlay_control_pause
                } else {
                    R.string.overlay_control_play
                },
            )
        }
    }

    private fun startInForeground(paused: Boolean) {
        val notification = buildNotification(paused)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(paused: Boolean): Notification {
        val channelId = NOTIFICATION_CHANNEL_ID
        val actionTitleRes = if (paused) {
            R.string.overlay_notification_action_resume
        } else {
            R.string.overlay_notification_action_pause
        }
        val contentTextRes = if (paused) {
            R.string.overlay_notification_content_paused
        } else {
            R.string.overlay_notification_content_active
        }

        val toggleIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_TOGGLE_MANUAL_PAUSE
        }
        val pendingToggle = PendingIntent.getService(
            this,
            0,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_overlay_notification)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(contentTextRes))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(actionTitleRes), pendingToggle)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.overlay_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.overlay_notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private companion object {
        const val NOTIFICATION_ID = 42
        const val NOTIFICATION_CHANNEL_ID = "audiofocus_overlay"
        const val ACTION_TOGGLE_MANUAL_PAUSE = "com.polaralias.audiofocus.action.TOGGLE_MANUAL_PAUSE"
        const val FADE_DURATION_MS = 200L
        const val SEEK_INTERVAL_MS = 10_000L
    }
}
