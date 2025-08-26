package com.example.ai_notification_assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner


class FloatingService : LifecycleService(), SavedStateRegistryOwner {

    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    companion object {
        private const val CHANNEL_ID = "assistant_foreground_channel"
        private const val CHANNEL_NAME = "Assistant Service"
        private const val ONGOING_NOTIF_ID = 1001
    }


    private lateinit var windowManager: WindowManager
    private var floatingIcon: View? = null



    override fun onCreate() {
        super.onCreate()

        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)


        // 1) Foreground notification ASAP (fixes the 5s crash)
        createChannelIfNeeded()
        startForeground(ONGOING_NOTIF_ID, buildOngoingNotification())

        // 2) WindowManager for overlay
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 3) Add the floating icon if we have overlay permission
        if (Settings.canDrawOverlays(this)) {
            addFloatingIcon()
        } else {
            Log.w("NotificationAI", "Overlay permission not granted; bubble not shown yet.")
        }

        // 4) Observe new notifications
        Log.d("NotificationAI", "FloatingService created & foreground started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // If user granted overlay permission after starting service, try adding the icon now
        if (floatingIcon == null && Settings.canDrawOverlays(this)) {
            addFloatingIcon()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove observer to avoid memory leaks
        // Remove overlay view
        floatingIcon?.let { windowManager.removeView(it) }
        floatingIcon = null
        Log.d("NotificationAI", "FloatingService destroyed")
    }

    // --- Helpers ---

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                description = "Keeps the AI Notification Assistant running"
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildOngoingNotification(): Notification {
        // Tap → open main UI (or OverlayActivity if you prefer)
        val tapIntent = Intent(this, MainActivity::class.java)
        val contentPI = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (PendingIntent.FLAG_IMMUTABLE)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Assistant running")
            .setContentText("Listening for notifications and preparing suggestions")
            .setContentIntent(contentPI)
            .setOngoing(true)
            .build()
    }

    private fun addFloatingIcon() {
        if (floatingIcon != null) return

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingIcon = inflater.inflate(R.layout.floating_icon, null).apply {
            visibility = View.VISIBLE
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        // --- Close Zone (at bottom) ---
        val closeView = inflater.inflate(R.layout.close_zone, null)
        closeView.visibility = View.GONE

        val closeParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 150
        }

        windowManager.addView(closeView, closeParams)

        // --- Touch Handling ---
        floatingIcon!!.setOnTouchListener(object : View.OnTouchListener {
            private var lastX = 0
            private var lastY = 0
            private var lastTouchX = 0f
            private var lastTouchY = 0f
            private var isMoving = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isMoving = false
                        lastX = params.x
                        lastY = params.y
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                        closeView.visibility = View.VISIBLE
                        closeView.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200).start()

                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - lastTouchX).toInt()
                        val dy = (event.rawY - lastTouchY).toInt()

                        if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) {
                            isMoving = true
                        }

                        params.x = lastX + dx
                        params.y = lastY + dy

                        // keep inside screen boundaries
                        val maxX = screenWidth - (floatingIcon?.width ?: 100)
                        val maxY = screenHeight - (floatingIcon?.height ?: 100)
                        params.x = params.x.coerceIn(0, maxX)
                        params.y = params.y.coerceIn(0, maxY)

                        windowManager.updateViewLayout(floatingIcon, params)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        closeView.animate().scaleX(1f).scaleY(1f).setDuration(200).withEndAction {
                            closeView.visibility = View.GONE
                        }.start()

                        if (isMoving) {
                            // Check if dropped inside close zone
                            val bubbleBottom = params.y + (floatingIcon?.height ?: 0)
                            if (bubbleBottom > screenHeight - 250) {
                                windowManager.removeView(floatingIcon)
                                floatingIcon = null
                                stopSelf() // close service
                            }
                        } else {
                            openOverlay() // treat as click
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingIcon, params)
        Log.d("NotificationAI", "Floating icon added")
    }


    private var overlayView: View? = null
    private var overlayVisible = false

    private fun openOverlay() {
        if (overlayVisible) {
            // Remove overlay if already shown
            overlayView?.let { windowManager.removeView(it) }
            overlayView = null
            overlayVisible = false
            return
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)

        // Create a ComposeView for overlay
        val composeView = ComposeView(this).apply {
            // Provide lifecycle + saved state manually

            setViewTreeLifecycleOwner(this@FloatingService)
            setViewTreeSavedStateRegistryOwner(this@FloatingService)

            setContent {
                val item by NotificationData.latestNotification.collectAsState()

                MaterialTheme {
                    if (item != null) {
                        SmallOverlayContent(item!!)
                    } else {
                        Text(
                            "No notification yet",
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        )
                    }
                }
            }
        }


        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER // Overlay at screen center
        }

        windowManager.addView(composeView, params)
        overlayView = composeView
        overlayVisible = true
    }

    @Composable
    fun SmallOverlayContent(item: NotificationItem) {
        Column(
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(20.dp))
                .padding(16.dp)
                .widthIn(min = 220.dp, max = 300.dp)
        ) {
            Text(
                "📢 ${item.title}",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF4F46E5)
            )
            Spacer(Modifier.height(8.dp))
            Text(item.text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "💡 ${item.suggestion}",
                color = Color(0xFF16A34A),
                modifier = Modifier
                    .background(Color(0xFFEFFDEE), RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                val clipboard =
                                    getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("AI Suggestion", item.suggestion)
                                clipboard.setPrimaryClip(clip)
                                Toast
                                    .makeText(
                                        applicationContext,
                                        "Copied to clipboard",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            }
                        )
                    }
            )

        }
    }

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry


}
