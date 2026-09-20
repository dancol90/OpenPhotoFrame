package io.github.micw.openphotoframe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps the app running and auto-restarts MainActivity after crashes.
 * 
 * When the service is restarted by Android after an OOM kill, it checks if MainActivity
 * is running and restarts it if necessary. This ensures the photo frame continues to work
 * even after memory-related crashes.
 * 
 * The service shows a minimal notification that explains it's keeping the
 * photo frame running.
 */
class KeepAliveService : Service() {
    private val restartScheduler by lazy {
        MainActivityRestartScheduler(
            delayedExecutor = HandlerDelayedExecutor(Handler(Looper.getMainLooper())),
            restartDelayMs = RESTART_DELAY_MS,
            restartAction = { ensureMainActivityIsRunning() },
        )
    }

    companion object {
        private const val TAG = "KeepAliveService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "keep_alive_channel"
        private const val CHANNEL_NAME = "Photo Frame Keep Alive"
        private const val RESTART_DELAY_MS = 2000L // Wait 2s before restarting MainActivity

        // Bounds how many times we'll try to relaunch a crashing MainActivity within
        // RESTART_WINDOW_MS. Without this, a MainActivity that crashes immediately on
        // launch (e.g. a display/GPU hiccup right after a long screen-off period) kills
        // this same-process service too; START_STICKY then restarts the service, which
        // immediately retries the launch - an unbounded crash/respawn loop that can peg
        // the CPU and flood system_server with binder traffic. State is persisted
        // because the loop kills the process the in-memory counter would otherwise live in.
        private const val PREFS_NAME = "KeepAliveServiceState"
        private const val KEY_ATTEMPT_COUNT = "restart_attempt_count"
        private const val KEY_WINDOW_START = "restart_window_start"
        private const val MAX_RESTART_ATTEMPTS = 5
        private const val RESTART_WINDOW_MS = 5 * 60 * 1000L // 5 minutes
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // Check if MainActivity needs to be restarted after a delay
        // This happens when the service is restarted by Android after an OOM kill
        restartScheduler.schedule()
        
        // START_STICKY ensures the service is restarted if killed by the system
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // This is not a bound service
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        restartScheduler.cancel()
        Log.d(TAG, "Service destroyed")
    }
    
    /**
     * Ensures MainActivity is running. If not, starts it - unless we've already
     * made too many restart attempts recently, in which case we back off rather
     * than risk an unbounded crash/respawn loop.
     * This is called after the service restarts following an OOM kill.
     */
    private fun ensureMainActivityIsRunning() {
        if (isMainActivityRunning()) {
            Log.d(TAG, "MainActivity is already running")
            resetRestartAttempts()
            return
        }

        if (!consumeRestartAttempt()) {
            Log.e(
                TAG,
                "MainActivity restarted $MAX_RESTART_ATTEMPTS times in the last " +
                    "${RESTART_WINDOW_MS}ms and is still not running - backing off " +
                    "instead of retrying again to avoid a crash loop"
            )
            return
        }

        Log.w(TAG, "MainActivity is not running, restarting it")
        startMainActivity()
    }

    /**
     * Returns true if another restart attempt is allowed right now, and records it.
     * Persisted in SharedPreferences (not a plain field) because the crash loop this
     * guards against kills the very process an in-memory counter would live in.
     */
    private fun consumeRestartAttempt(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val windowStart = prefs.getLong(KEY_WINDOW_START, 0L)
        var count = prefs.getInt(KEY_ATTEMPT_COUNT, 0)

        if (now - windowStart > RESTART_WINDOW_MS) {
            // Previous window (or backoff period) has elapsed - start a fresh one.
            windowStartFreshAt(prefs, now)
            count = 0
        }

        if (count >= MAX_RESTART_ATTEMPTS) {
            return false
        }

        prefs.edit().putInt(KEY_ATTEMPT_COUNT, count + 1).apply()
        return true
    }

    private fun windowStartFreshAt(prefs: SharedPreferences, now: Long) {
        prefs.edit()
            .putLong(KEY_WINDOW_START, now)
            .putInt(KEY_ATTEMPT_COUNT, 0)
            .apply()
    }

    private fun resetRestartAttempts() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_ATTEMPT_COUNT, 0)
            .apply()
    }
    
    /**
     * Checks if MainActivity is currently running.
     */
    private fun isMainActivityRunning(): Boolean {
        val isRunning = MainActivity.isRunning
        Log.d(TAG, "MainActivity running check: $isRunning")
        return isRunning
    }
    
    /**
     * Starts MainActivity.
     */
    private fun startMainActivity() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            Log.d(TAG, "MainActivity started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MainActivity", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low importance = no sound, minimal visibility
            ).apply {
                description = "Keeps the photo frame app running in the background"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        // Intent to open the app when tapping the notification
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Photo Frame Active")
            .setContentText("Keeping app running for continuous slideshow")
            .setSmallIcon(R.drawable.ic_notification) // We'll need to add this icon
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Cannot be dismissed by swiping
            .setPriority(NotificationCompat.PRIORITY_LOW) // Minimal intrusion
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
