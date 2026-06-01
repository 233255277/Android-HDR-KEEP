package com.hdrscreen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.SurfaceView
import android.view.WindowManager

/**
 * HDR悬浮窗Service — 步骤二核心
 *
 * 在全屏透明悬浮窗中循环播放HDR10视频，使屏幕保持HDR激发态。
 * 默认使用"完全透明方案"：正常尺寸但alpha≈0，系统不可见但HDR layer有效。
 */
class HdrOverlayService : Service() {

    companion object {
        private const val TAG = "HdrOverlayService"

        @Volatile
        var isServiceRunning = false
            private set

        fun setRunning(running: Boolean) {
            isServiceRunning = running
        }
    }

    private var windowManager: WindowManager? = null
    private var surfaceView: SurfaceView? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        Log.d(TAG, "Service created")
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "hdr_overlay",
                "HDR悬浮窗",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "保持屏幕HDR激发态" }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
            val notification = Notification.Builder(this, "hdr_overlay")
                .setContentTitle("HDR屏幕激活器")
                .setContentText("悬浮窗运行中")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build()
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            createOverlay()
            startHdrPlayback()
            isRunning = true
            Log.i(TAG, "HDR overlay started")
        }
        return START_STICKY
    }

    private fun createOverlay() {
        surfaceView = SurfaceView(this).apply {
            // 关键：使用最低alpha保持Surface活跃但不被用户看到
            alpha = 0.001f
            setBackgroundColor(0x00000000)
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.RGBA_8888)
        }

        val params = WindowManager.LayoutParams().apply {
            // 完全透明悬浮窗策略：正常尺寸，极低alpha
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        windowManager?.addView(surfaceView, params)
        Log.d(TAG, "Overlay created: fullscreen, alpha=0.001")
    }

    private fun startHdrPlayback() {
        val surface = surfaceView?.holder?.surface
        if (surface == null || !surface.isValid) {
            Log.w(TAG, "Surface not ready, will retry in surface callback")
            surfaceView?.holder?.addCallback(object : android.view.SurfaceHolder.Callback {
                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                    startPlaybackInternal(holder.surface)
                }
                override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w: Int, ht: Int) {}
                override fun surfaceDestroyed(h: android.view.SurfaceHolder) {}
            })
            return
        }
        startPlaybackInternal(surface)
    }

    private fun startPlaybackInternal(surface: android.view.Surface) {
        if (mediaPlayer != null) return

        try {
            mediaPlayer = MediaPlayer().apply {
                val uri = Uri.parse("android.resource://${packageName}/${R.raw.hdr_test}")
                setDataSource(this@HdrOverlayService, uri)
                setSurface(surface)
                setLooping(true)
                setVolume(0f, 0f) // 静音
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    // 出错后延迟重试
                    android.os.Handler(mainLooper).postDelayed({
                        if (isRunning) restartPlayback()
                    }, 2000)
                    true
                }
                setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        Log.i(TAG, "HDR rendering started — screen should enter HDR mode")
                    }
                    false
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start playback: ${e.message}", e)
        }
    }

    private fun restartPlayback() {
        try { mediaPlayer?.stop() } catch (e: Exception) {}
        try { mediaPlayer?.release() } catch (e: Exception) {}
        mediaPlayer = null
        startHdrPlayback()
    }

    private fun stopPlayback() {
        try { mediaPlayer?.stop() } catch (e: Exception) {}
        try { mediaPlayer?.release() } catch (e: Exception) {}
        mediaPlayer = null
    }

    private fun removeOverlay() {
        try {
            surfaceView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {}
        surfaceView = null
    }

    override fun onDestroy() {
        stopPlayback()
        removeOverlay()
        isRunning = false
        setRunning(false)
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }
}