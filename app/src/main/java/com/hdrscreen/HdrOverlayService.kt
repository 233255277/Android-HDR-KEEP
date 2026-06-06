package com.hdrscreen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.media.ImageReader
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
 *
 * 版本差异：
 * - API ≥ 33：全屏 + alpha≈0.001 透明方案
 * - API <  33（Android 12）：1×1 微窗口 + colorMode=HDR，规避 SurfaceView 的 alpha 无效问题
 */
class HdrOverlayService : Service() {

    companion object {
        private const val TAG = "HdrOverlayService"

        /** API 33 为分界线：以上 SurfaceView alpha 正常，以下需走微窗口 */
        private val IS_API33_PLUS = Build.VERSION.SDK_INT >= 33

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
    private var dummyReader: ImageReader? = null  // 旋转时临时占位Surface，防止MediaPlayer报错

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
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "保持屏幕HDR激发态"
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
            val notification = Notification.Builder(this, "hdr_overlay")
                .setContentTitle("HDR屏幕激活器")
                .setContentText("悬浮窗运行中")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            createOverlay()
            isRunning = true
            Log.i(TAG, "HDR overlay started, api33+=$IS_API33_PLUS")
        }
        return START_STICKY
    }

    // ── 窗口创建 ────────────────────────────────────────────────

    private fun createOverlay() {
        surfaceView = SurfaceView(this).apply {
            alpha = 0.001f
            setBackgroundColor(0x00000000)
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.RGBA_8888)

            // 注册 Surface 生命周期回调：屏幕旋转时 Surface 会销毁→重建
            holder.addCallback(surfaceCallback)
        }

        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                colorMode = ActivityInfo.COLOR_MODE_HDR
            }

            if (IS_API33_PLUS) {
                // API33+：全屏透明方案
                flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
            } else {
                // API31-32：1×1 微窗口，规避 SurfaceView alpha 无效
                width = 1
                height = 1
            }
        }

        windowManager?.addView(surfaceView, params)
        Log.d(TAG, "Overlay added: microSize=${!IS_API33_PLUS}, colorMode=HDR")
    }

    // ── Surface 生命周期回调（解决旋转失效）────────────────────

    private val surfaceCallback = object : android.view.SurfaceHolder.Callback {

        override fun surfaceCreated(holder: android.view.SurfaceHolder) {
            Log.d(TAG, "Surface created")
            // 切回真实Surface，释放占位ImageReader
            if (mediaPlayer != null) {
                try {
                    mediaPlayer?.setSurface(holder.surface)
                    if (mediaPlayer?.isPlaying != true) {
                        mediaPlayer?.start()
                    }
                    dummyReader?.close()
                    dummyReader = null
                    Log.d(TAG, "Surface rebound to real Surface")
                } catch (e: Exception) {
                    Log.w(TAG, "Rebind failed, recreating: ${e.message}")
                    releaseMediaPlayer()
                    dummyReader?.close()
                    dummyReader = null
                    startOrResumePlayback(holder.surface)
                }
            } else {
                startOrResumePlayback(holder.surface)
            }
        }

        override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, w: Int, h: Int) {
            Log.d(TAG, "Surface changed: ${w}x${h}")
        }

        override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
            Log.d(TAG, "Surface destroyed — switching to dummy ImageReader")
            // 立即切换到1×1占位Surface，防止MediaPlayer因Surface失效而报错
            try {
                dummyReader = ImageReader.newInstance(1, 1, PixelFormat.RGBA_8888, 2)
                mediaPlayer?.setSurface(dummyReader?.surface)
                Log.d(TAG, "Switched to ImageReader placeholder")
            } catch (e: Exception) {
                Log.w(TAG, "ImageReader fallback failed: ${e.message}")
                releaseMediaPlayer()
            }
        }
    }

    // ── HDR 播放管理 ───────────────────────────────────────────

    private fun startOrResumePlayback(surface: android.view.Surface) {
        if (!surface.isValid) {
            Log.w(TAG, "Surface invalid, skip playback")
            return
        }

        // 如果已有播放器在运行，直接复用（理论上 surfaceDestroyed 已释放）
        if (mediaPlayer != null) {
            Log.d(TAG, "MediaPlayer already exists, skipping")
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                val uri = Uri.parse("android.resource://${packageName}/${R.raw.hdr_test}")
                setDataSource(this@HdrOverlayService, uri)
                setSurface(surface)
                setLooping(true)
                setVolume(0f, 0f)
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    android.os.Handler(mainLooper).postDelayed({
                        if (isRunning) {
                            releaseMediaPlayer()
                            surfaceView?.holder?.surface?.let { startOrResumePlayback(it) }
                        }
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

    private fun releaseMediaPlayer() {
        try { mediaPlayer?.stop() } catch (e: Exception) {}
        try { mediaPlayer?.release() } catch (e: Exception) {}
        mediaPlayer = null
        // 同时清理占位ImageReader
        try { dummyReader?.close() } catch (e: Exception) {}
        dummyReader = null
    }

    private fun removeOverlay() {
        try {
            surfaceView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {}
        surfaceView = null
    }

    override fun onDestroy() {
        releaseMediaPlayer()
        removeOverlay()
        isRunning = false
        setRunning(false)
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }
}